package dev.sayitinjapanese.client;

import dev.sayitinjapanese.DeleteItemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Core game logic: detects item pick-ups (by watching the inventory grow while no screen is
 * open), runs one "say it in Japanese" challenge at a time and deletes the items on failure.
 */
final class Challenges {
	private Challenges() {}

	enum Phase { LISTENING, PASSED, FAILED }

	enum NameState { PENDING, READY, NONE }

	static final class Challenge {
		final Item item;
		final String id;
		final ItemStack icon;
		final boolean practice;
		int count;
		volatile NameState state = NameState.PENDING;
		JapaneseNames.Name name;
		String heard = "";

		Challenge(Item item, ItemStack icon, int count, boolean practice) {
			this.item = item;
			this.id = BuiltInRegistries.ITEM.getKey(item).toString();
			this.icon = icon;
			this.count = count;
			this.practice = practice;
		}
	}

	private static final long RESULT_MS = 1300;

	static Challenge current;
	static Phase phase = Phase.LISTENING;
	static long phaseStart;
	static final Deque<Challenge> queue = new ArrayDeque<>();

	private static Map<Item, Integer> baseline;
	private static final Map<Item, ItemStack> samples = new HashMap<>();
	private static Object lastPlayer;
	private static int settleTicks;
	private static boolean bootstrapped;
	private static boolean warnedModel, warnedMic, warnedServer;

	// ------------------------------------------------------------------ chat helpers

	static void say(String msg) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) p.sendSystemMessage(Component.literal("§6[SayItInJapanese]§r " + msg));
	}

	static void sayLater(String msg) {
		Minecraft.getInstance().execute(() -> say(msg));
	}

	// ------------------------------------------------------------------ lifecycle

	static void reset() {
		Speech.stop();
		current = null;
		queue.clear();
		baseline = null;
		lastPlayer = null;
	}

	/** Queue a practice challenge (/sayjp test): same flow, but nothing is ever deleted. */
	static void test() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		if (!Speech.ready()) {
			say("§eThe speech model isn't ready yet.");
			ModelManager.ensureAsync(true);
			return;
		}
		enqueue(new Challenge(Items.DIAMOND, new ItemStack(Items.DIAMOND), 1, true));
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		Config cfg = Config.get();
		if (p == null || mc.level == null) {
			if (current != null || baseline != null) reset();
			return;
		}
		if (!bootstrapped && cfg.enabled) {
			bootstrapped = true;
			ModelManager.ensureAsync(cfg.autoDownloadModel);
		}
		if (!cfg.enabled) {
			if (current != null || !queue.isEmpty()) {
				Speech.stop();
				current = null;
				queue.clear();
			}
			baseline = null;
			return;
		}
		detectPickups(mc, p, cfg);
		advance(cfg);
	}

	// ------------------------------------------------------------------ pick-up detection

	private static Map<Item, Integer> countItems(LocalPlayer p) {
		Map<Item, Integer> counts = new HashMap<>();
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			counts.merge(s.getItem(), s.getCount(), Integer::sum);
			samples.put(s.getItem(), s.copyWithCount(1));
		}
		return counts;
	}

	private static void detectPickups(Minecraft mc, LocalPlayer p, Config cfg) {
		if (p != lastPlayer) {
			lastPlayer = p;
			baseline = null;
			settleTicks = 40; // ignore the inventory sync that follows joining / respawning
		}
		Map<Item, Integer> now = countItems(p);
		if (settleTicks > 0) {
			settleTicks--;
			baseline = now;
			return;
		}
		if (baseline == null) {
			baseline = now;
			return;
		}
		boolean armed = mc.gui.screen() == null && !(cfg.skipCreative && p.isCreative());
		if (armed) {
			for (Map.Entry<Item, Integer> e : now.entrySet()) {
				int gained = e.getValue() - baseline.getOrDefault(e.getKey(), 0);
				if (gained > 0) onPickup(e.getKey(), gained);
			}
		}
		baseline = now;
	}

	private static void onPickup(Item item, int gained) {
		if (!Speech.ready()) {
			if (!warnedModel) {
				warnedModel = true;
				say(ModelManager.busy()
						? "§eSpeech model is still setting up - you're safe for now."
						: "§eSpeech model isn't installed yet - run §f/sayjp download");
			}
			ModelManager.ensureAsync(Config.get().autoDownloadModel);
			return;
		}
		// Same item already being / about to be asked about? Just add to the pile.
		if (current != null && phase == Phase.LISTENING && current.item == item) {
			current.count += gained;
			return;
		}
		for (Challenge q : queue) {
			if (q.item == item) {
				q.count += gained;
				return;
			}
		}
		ItemStack icon = samples.getOrDefault(item, new ItemStack(item));
		enqueue(new Challenge(item, icon, gained, false));
	}

	private static void enqueue(Challenge c) {
		queue.add(c);
		JapaneseNames.resolveAsync(c.item, result -> {
			if (result.isPresent()) {
				c.name = result.get();
				c.state = NameState.READY;
			} else {
				c.state = NameState.NONE;
			}
		});
	}

	// ------------------------------------------------------------------ running a challenge

	private static void advance(Config cfg) {
		long now = System.currentTimeMillis();

		if (current == null) {
			Challenge next = queue.peek();
			if (next == null) return;
			if (next.state == NameState.NONE) { // no Japanese name (modded item, "TNT", ...) - let it through
				queue.poll();
				return;
			}
			if (next.state == NameState.PENDING) return;
			queue.poll();
			begin(next, cfg, now);
			return;
		}

		Challenge c = current;
		if (phase != Phase.LISTENING) {
			if (now - phaseStart >= RESULT_MS) current = null;
			return;
		}

		long elapsed = now - phaseStart;
		c.heard = Speech.live();
		if (score(c, c.heard) >= cfg.strictness) {
			Speech.stop();
			finish(c, true, now);
		} else if (elapsed >= cfg.seconds * 1000L) {
			String finalText = Speech.stop();
			c.heard = finalText;
			finish(c, score(c, finalText) >= cfg.strictness, now);
		}
	}

	private static void begin(Challenge c, Config cfg, long now) {
		try {
			Speech.start(cfg.microphone);
		} catch (Throwable t) {
			SayItInJapaneseClient.LOG.error("Could not open microphone", t);
			if (!warnedMic) {
				warnedMic = true;
				say("§cCouldn't open a microphone (" + t.getMessage()
						+ "). Check your OS microphone permission for Minecraft/Java, or run §f/sayjp mic list");
			}
			return; // item is safe if we can't listen
		}
		current = c;
		phase = Phase.LISTENING;
		phaseStart = now;
	}

	private static double score(Challenge c, String heard) {
		if (heard == null || heard.isBlank()) return 0.0;
		if (c.name.kanji().length() >= 2 && heard.replace(" ", "").contains(c.name.kanji())) return 1.0;
		String h = JapaneseNames.normalizeHeard(heard);
		if (h == null || h.isEmpty()) return 0.0;
		return Kana.bestWindowSimilarity(c.name.norm(), h);
	}

	private static void finish(Challenge c, boolean passed, long now) {
		phase = passed ? Phase.PASSED : Phase.FAILED;
		phaseStart = now;
		if (passed) return;

		Config cfg = Config.get();
		if (c.practice || !cfg.deleteOnFail) return;

		if (ClientPlayNetworking.canSend(DeleteItemPayload.TYPE)) {
			ClientPlayNetworking.send(new DeleteItemPayload(c.id, c.count));
			say("§cDeleted " + c.count + "x " + c.name.kanji() + " §7(" + c.name.romaji() + ")");
		} else if (!warnedServer) {
			warnedServer = true;
			say("§eThis server doesn't have Say It In Japanese installed, so your items can't be deleted.");
		}
	}
}
