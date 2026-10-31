package dev.sayitinjapanese.client;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Looks up the Japanese name of an item from Minecraft's own {@code ja_jp.json} language
 * file and derives hiragana / romaji from it with Kuromoji. Heavy work runs on one
 * background thread; callbacks are delivered on the client thread.
 */
final class JapaneseNames {
	private JapaneseNames() {}

	/** All the ways a name can be shown / matched. {@code norm} is hiragana-only for comparing. */
	record Name(String kanji, String hiragana, String romaji, String norm) {
		String display(Config.Script script) {
			return switch (script) {
				case KANJI -> kanji;
				case HIRAGANA -> hiragana;
				case ROMAJI -> romaji;
			};
		}
	}

	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "sayjp-names");
		t.setDaemon(true);
		return t;
	});

	private static final Map<String, Optional<Name>> CACHE = new ConcurrentHashMap<>();
	private static volatile JsonObject lang;
	private static volatile Tokenizer tokenizer;
	private static volatile boolean langMissingReported;

	static boolean tokenizerReady() {
		return tokenizer != null;
	}

	/** Start loading Kuromoji in the background so the first challenge isn't delayed. */
	static void warmUp() {
		EXEC.execute(JapaneseNames::tokenizer);
	}

	private static Tokenizer tokenizer() {
		Tokenizer t = tokenizer;
		if (t == null) {
			t = new Tokenizer();
			tokenizer = t;
		}
		return t;
	}

	/** Resolves the Japanese name of {@code item}; {@code callback} runs on the client thread. */
	static void resolveAsync(Item item, Consumer<Optional<Name>> callback) {
		Minecraft mc = Minecraft.getInstance();
		ResourceManager rm = mc.getResourceManager();
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		EXEC.execute(() -> {
			Optional<Name> result;
			try {
				result = CACHE.computeIfAbsent(id.toString(), k -> resolve(rm, id));
			} catch (Throwable t) {
				SayItInJapaneseClient.LOG.error("Failed to resolve Japanese name for {}", id, t);
				result = Optional.empty();
			}
			Optional<Name> r = result;
			mc.execute(() -> callback.accept(r));
		});
	}

	private static Optional<Name> resolve(ResourceManager rm, Identifier id) {
		JsonObject l = loadLang(rm);
		if (l == null) return Optional.empty();

		String text = null;
		for (String prefix : new String[]{"item.", "block."}) {
			String key = prefix + id.getNamespace() + "." + id.getPath();
			if (l.has(key) && l.get(key).isJsonPrimitive()) {
				text = l.get(key).getAsString();
				break;
			}
		}
		if (text == null || text.isBlank()) return Optional.empty();

		String kana = readingOf(text);
		String hira = Kana.toHiragana(kana);
		String norm = Kana.normalize(kana);
		if (norm.length() < 2) return Optional.empty(); // e.g. "TNT" - nothing to pronounce reliably
		return Optional.of(new Name(text, hira, Kana.toRomaji(hira), norm));
	}

	private static JsonObject loadLang(ResourceManager rm) {
		JsonObject l = lang;
		if (l != null) return l;
		synchronized (JapaneseNames.class) {
			if (lang != null) return lang;
			try {
				Optional<Resource> res = rm.getResource(Identifier.fromNamespaceAndPath("minecraft", "lang/ja_jp.json"));
				if (res.isPresent()) {
					try (Reader r = res.get().openAsReader()) {
						lang = JsonParser.parseReader(r).getAsJsonObject();
						return lang;
					}
				}
			} catch (Exception e) {
				SayItInJapaneseClient.LOG.error("Could not read ja_jp.json", e);
			}
		}
		if (!langMissingReported) {
			langMissingReported = true;
			Minecraft.getInstance().execute(() -> Challenges.say(
					"§cCouldn't find Minecraft's Japanese language file. Switch the game language to 日本語 once so it gets downloaded."));
		}
		return null;
	}

	/** Katakana reading of any Japanese text (kanji resolved by Kuromoji, unknown words kept as is). */
	static String readingOf(String text) {
		List<Token> tokens = tokenizer().tokenize(text);
		StringBuilder sb = new StringBuilder();
		for (Token t : tokens) {
			String reading = t.getReading();
			if (reading == null || reading.equals("*")) sb.append(t.getSurface());
			else sb.append(reading);
		}
		return sb.toString();
	}

	/** Hiragana-only normalised form of whatever the speech model heard (null until Kuromoji is loaded). */
	static String normalizeHeard(String heard) {
		if (tokenizer == null) return null;
		return Kana.normalize(readingOf(heard));
	}

	/** Heard text rendered as "カタカナ (romaji)" for the pop-up. */
	static String prettyHeard(String heard) {
		if (heard == null || heard.isBlank() || tokenizer == null) return "";
		String kata = Kana.toKatakana(readingOf(heard)).replace(" ", "");
		return kata + " (" + Kana.toRomaji(kata) + ")";
	}
}
