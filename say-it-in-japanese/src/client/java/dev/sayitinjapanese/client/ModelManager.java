package dev.sayitinjapanese.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads (once) and loads the offline Vosk Japanese speech model. */
final class ModelManager {
	private ModelManager() {}

	private static final AtomicBoolean busy = new AtomicBoolean();

	static Path modelDir() {
		return Config.dataDir().resolve("model");
	}

	static boolean installed() {
		return Files.isDirectory(modelDir().resolve("am")) && Files.isDirectory(modelDir().resolve("conf"));
	}

	static boolean busy() {
		return busy.get();
	}

	/** Loads the model if present; downloads it first if allowed. Safe to call repeatedly. */
	static void ensureAsync(boolean allowDownload) {
		if (Speech.ready() || !busy.compareAndSet(false, true)) return;
		Thread t = new Thread(() -> {
			try {
				if (!installed()) {
					if (!allowDownload) {
						Challenges.sayLater("§eSpeech model missing. Run §f/sayjp download§e to fetch it (~50 MB).");
						return;
					}
					download();
				}
				Challenges.sayLater("§7Loading speech model...");
				Speech.loadModel(modelDir());
				JapaneseNames.warmUp();
				Challenges.sayLater("§aSpeech model ready. Pick something up and say its name in Japanese!");
			} catch (Throwable e) {
				SayItInJapaneseClient.LOG.error("Speech model setup failed", e);
				Challenges.sayLater("§cSpeech model setup failed: " + e);
			} finally {
				busy.set(false);
			}
		}, "sayjp-model");
		t.setDaemon(true);
		t.start();
	}

	private static void download() throws Exception {
		String url = Config.get().modelUrl;
		Challenges.sayLater("§eDownloading Japanese speech model (one time only)...");
		Path dataDir = Config.dataDir();
		Files.createDirectories(dataDir);
		Path zip = dataDir.resolve("model.zip.part");

		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
		HttpResponse<InputStream> resp = client.send(
				HttpRequest.newBuilder(URI.create(url)).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode() + " for " + url);

		long total = resp.headers().firstValueAsLong("content-length").orElse(-1);
		long done = 0;
		int lastPct = -1;
		try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(zip)) {
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				done += n;
				if (total > 0) {
					int pct = (int) (done * 100 / total) / 25 * 25;
					if (pct != lastPct) {
						lastPct = pct;
						Challenges.sayLater("§7Downloading speech model: " + pct + "%");
					}
				}
			}
		}

		Challenges.sayLater("§7Unpacking...");
		Path tmp = dataDir.resolve("model.tmp");
		deleteRecursive(tmp);
		Files.createDirectories(tmp);
		try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry e;
			while ((e = zin.getNextEntry()) != null) {
				// Strip the top level folder (e.g. "vosk-model-small-ja-0.22/")
				String name = e.getName();
				int slash = name.indexOf('/');
				if (slash < 0) continue;
				name = name.substring(slash + 1);
				if (name.isEmpty()) continue;
				Path target = tmp.resolve(name).normalize();
				if (!target.startsWith(tmp)) throw new IllegalStateException("Bad zip entry " + e.getName());
				if (e.isDirectory()) {
					Files.createDirectories(target);
				} else {
					Files.createDirectories(target.getParent());
					Files.copy(zin, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		deleteRecursive(modelDir());
		Files.move(tmp, modelDir());
		Files.deleteIfExists(zip);
	}

	private static void deleteRecursive(Path p) throws java.io.IOException {
		if (!Files.exists(p)) return;
		try (Stream<Path> s = Files.walk(p)) {
			for (Path x : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) Files.delete(x);
		}
	}
}
