package dev.sayitinjapanese.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Microphone capture + offline Vosk recognition. One listening session at a time. */
final class Speech {
	private Speech() {}

	private static final float RATE = 16000f;
	private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
	private static final Object LOCK = new Object();

	private static volatile boolean ready;
	private static Model model;

	private static TargetDataLine line;
	private static Recognizer recognizer;
	private static boolean running;
	private static final StringBuilder accumulated = new StringBuilder();

	/** Everything heard in the current session so far (finished sentences + the live partial). */
	private static volatile String live = "";

	static boolean ready() {
		return ready;
	}

	static String live() {
		return live;
	}

	static void loadModel(Path dir) throws IOException {
		LibVosk.setLogLevel(LogLevel.WARNINGS);
		model = new Model(dir.toString());
		ready = true;
	}

	static List<String> microphones() {
		List<String> names = new ArrayList<>();
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
		for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
			if (AudioSystem.getMixer(mi).isLineSupported(info)) names.add(mi.getName());
		}
		return names;
	}

	/** Opens the microphone and starts recognising. Throws if no usable microphone exists. */
	static void start(String micHint) throws LineUnavailableException, IOException {
		synchronized (LOCK) {
			stopLocked();
			TargetDataLine l = openLine(micHint);
			l.open(FORMAT, 16000);
			l.start();
			line = l;
			recognizer = new Recognizer(model, RATE);
			accumulated.setLength(0);
			live = "";
			running = true;
			Thread t = new Thread(Speech::loop, "sayjp-mic");
			t.setDaemon(true);
			t.start();
		}
	}

	/** Stops listening and returns the complete transcript of the session. */
	static String stop() {
		synchronized (LOCK) {
			if (recognizer != null) {
				append(extract(recognizer.getFinalResult(), "text"));
			}
			String result = accumulated.toString().trim();
			stopLocked();
			live = result;
			return result;
		}
	}

	private static void stopLocked() {
		running = false;
		if (line != null) {
			try {
				line.stop();
				line.close();
			} catch (Exception ignored) {
			}
			line = null;
		}
		if (recognizer != null) {
			recognizer.close();
			recognizer = null;
		}
	}

	private static void loop() {
		byte[] buf = new byte[3200]; // 100 ms
		while (true) {
			TargetDataLine l;
			Recognizer r;
			synchronized (LOCK) {
				if (!running) return;
				l = line;
				r = recognizer;
			}
			if (l == null || r == null) return;
			int n = l.read(buf, 0, buf.length);
			if (n <= 0) {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					return;
				}
				continue;
			}
			synchronized (LOCK) {
				if (!running || recognizer != r) return;
				if (r.acceptWaveForm(buf, n)) {
					append(extract(r.getResult(), "text"));
					live = accumulated.toString().trim();
				} else {
					live = (accumulated + " " + extract(r.getPartialResult(), "partial")).trim();
				}
			}
		}
	}

	private static void append(String s) {
		if (s == null || s.isBlank()) return;
		if (accumulated.length() > 0) accumulated.append(' ');
		accumulated.append(s.trim());
	}

	private static String extract(String json, String field) {
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			return o.has(field) ? o.get(field).getAsString() : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static TargetDataLine openLine(String hint) throws LineUnavailableException {
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
		if (hint != null && !hint.isBlank()) {
			for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
				if (!mi.getName().toLowerCase().contains(hint.toLowerCase())) continue;
				Mixer m = AudioSystem.getMixer(mi);
				if (m.isLineSupported(info)) return (TargetDataLine) m.getLine(info);
			}
		}
		return AudioSystem.getTargetDataLine(FORMAT);
	}
}
