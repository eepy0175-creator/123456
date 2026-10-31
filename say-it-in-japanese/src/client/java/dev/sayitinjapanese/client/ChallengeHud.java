package dev.sayitinjapanese.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The toast-like pop-up: item icon, the word, a countdown bar and what the mic heard. */
final class ChallengeHud {
	private ChallengeHud() {}

	private static final int W = 272;
	private static final int H = 78;

	static void render(GuiGraphicsExtractor g, DeltaTracker tracker) {
		Challenges.Challenge c = Challenges.current;
		if (c == null || c.name == null) return;

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Config cfg = Config.get();
		Challenges.Phase phase = Challenges.phase;

		long total = cfg.seconds * 1000L;
		long elapsed = System.currentTimeMillis() - Challenges.phaseStart;
		boolean listening = phase == Challenges.Phase.LISTENING;

		int accent = switch (phase) {
			case LISTENING -> 0xFFFFC107;
			case PASSED -> 0xFF55FF55;
			case FAILED -> 0xFFFF5555;
		};
		int wordColor = switch (phase) {
			case LISTENING -> 0xFFFF5555;
			case PASSED -> 0xFF55FF55;
			case FAILED -> 0xFFAA3333;
		};

		int x = (g.guiWidth() - W) / 2;
		int y = 10;
		int textX = x + 58;
		int avail = W - 58 - 10;

		// panel
		g.fill(x, y, x + W, y + H, 0xE61A1A20);
		g.fill(x, y, x + 3, y + H, accent);

		// icon (2x)
		g.pose().pushMatrix();
		g.pose().translate(x + 14, y + 18);
		g.pose().scale(2f);
		g.fakeItem(c.icon, 0, 0);
		g.pose().popMatrix();

		// small label + countdown
		String label = switch (phase) {
			case LISTENING -> "SAY IT IN JAPANESE" + (c.count > 1 ? "  x" + c.count : "");
			case PASSED -> "NICE!";
			case FAILED -> c.practice || !cfg.deleteOnFail ? "TRY AGAIN" : "TOO SLOW - DELETED";
		};
		g.text(font, label, textX, y + 8, 0xFFA0A4AA);
		if (listening) {
			String secs = Math.max(0, (int) Math.ceil((total - elapsed) / 1000.0)) + "s";
			g.text(font, secs, x + W - 10 - font.width(secs), y + 8, 0xFFFFFFFF);
		}

		// the word, scaled to fit
		String word = c.name.display(cfg.script);
		float scale = Math.min(2f, (float) avail / Math.max(1, font.width(word)));
		g.pose().pushMatrix();
		g.pose().translate(textX, y + 22);
		g.pose().scale(scale);
		g.text(font, word, 0, 0, wordColor);
		g.pose().popMatrix();

		// countdown bar
		int barY = y + H - 28;
		g.fill(textX, barY, textX + avail, barY + 4, 0xFF3A3A42);
		float frac = listening ? Math.min(1f, (float) elapsed / total) : 1f;
		g.fill(textX, barY, textX + (int) (avail * frac), barY + 4, accent);

		// what the microphone heard
		String pretty = JapaneseNames.prettyHeard(c.heard);
		String heard = pretty.isEmpty() ? "listening..." : "heard: \"" + pretty + "\"";
		while (font.width(heard) > avail && heard.length() > 3) {
			heard = heard.substring(0, heard.length() - 2) + "…";
		}
		g.text(font, heard, textX, y + H - 16, wordColor);

		// queue indicator
		if (!Challenges.queue.isEmpty()) {
			String more = "+" + Challenges.queue.size();
			g.text(font, more, x + W - 8 - font.width(more), y + H - 16, 0xFFA0A4AA);
		}
	}
}
