package dev.sayitinjapanese.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stored in {@code config/sayitinjapanese.json}. Edit it by hand or use the /sayjp command. */
public final class Config {
	public enum Script { KANJI, HIRAGANA, ROMAJI }

	/** Master switch. */
	public boolean enabled = true;
	/** How the word is displayed in the pop-up: KANJI, HIRAGANA or ROMAJI. */
	public Script script = Script.KANJI;
	/** Seconds you get to say the word. */
	public int seconds = 4;
	/** 0.0 - 1.0. How close what the speech model heard has to be to the real word. Lower = more forgiving. */
	public double strictness = 0.6;
	/** Part of the microphone name to use (empty = system default). */
	public String microphone = "";
	/** Don't run challenges in creative mode (pick-block would trigger them). */
	public boolean skipCreative = true;
	/** Actually delete the item on failure. Turn off for a practice mode. */
	public boolean deleteOnFail = true;
	/** Download the speech model automatically the first time it is needed. */
	public boolean autoDownloadModel = true;
	/** Vosk Japanese model. Swap for the bigger "vosk-model-ja-0.22" for better accuracy (about 1 GB). */
	public String modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	public static Config get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("sayitinjapanese.json");
	}

	public static Path dataDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("sayitinjapanese");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p)) {
				Config c = GSON.fromJson(r, Config.class);
				if (c != null) instance = c;
			} catch (Exception e) {
				SayItInJapaneseClient.LOG.warn("Could not read config, using defaults", e);
			}
		}
		if (instance.script == null) instance.script = Script.KANJI;
		instance.seconds = Math.max(1, Math.min(60, instance.seconds));
		instance.strictness = Math.max(0.0, Math.min(1.0, instance.strictness));
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path())) {
				GSON.toJson(instance, w);
			}
		} catch (IOException e) {
			SayItInJapaneseClient.LOG.warn("Could not save config", e);
		}
	}
}
