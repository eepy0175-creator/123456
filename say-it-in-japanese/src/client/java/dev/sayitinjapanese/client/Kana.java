package dev.sayitinjapanese.client;

import java.util.HashMap;
import java.util.Map;

/** Small kana helpers: katakana/hiragana conversion, Hepburn romaji, fuzzy matching. */
final class Kana {
	private Kana() {}

	static String toHiragana(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (char c : s.toCharArray()) {
			if (c >= 0x30A1 && c <= 0x30F6) sb.append((char) (c - 0x60));
			else sb.append(c);
		}
		return sb.toString();
	}

	static String toKatakana(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (char c : s.toCharArray()) {
			if (c >= 0x3041 && c <= 0x3096) sb.append((char) (c + 0x60));
			else sb.append(c);
		}
		return sb.toString();
	}

	/** Keeps only hiragana letters (after converting katakana). Used for comparisons. */
	static String normalize(String s) {
		String h = toHiragana(s);
		StringBuilder sb = new StringBuilder(h.length());
		for (char c : h.toCharArray()) {
			if (c >= 0x3041 && c <= 0x3096) sb.append(c);
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------- romaji

	private static final Map<String, String> MONO = new HashMap<>();
	private static final Map<String, String> DI = new HashMap<>();

	static {
		String[] m = {
				"あ", "a", "い", "i", "う", "u", "え", "e", "お", "o",
				"か", "ka", "き", "ki", "く", "ku", "け", "ke", "こ", "ko",
				"さ", "sa", "し", "shi", "す", "su", "せ", "se", "そ", "so",
				"た", "ta", "ち", "chi", "つ", "tsu", "て", "te", "と", "to",
				"な", "na", "に", "ni", "ぬ", "nu", "ね", "ne", "の", "no",
				"は", "ha", "ひ", "hi", "ふ", "fu", "へ", "he", "ほ", "ho",
				"ま", "ma", "み", "mi", "む", "mu", "め", "me", "も", "mo",
				"や", "ya", "ゆ", "yu", "よ", "yo",
				"ら", "ra", "り", "ri", "る", "ru", "れ", "re", "ろ", "ro",
				"わ", "wa", "ゐ", "i", "ゑ", "e", "を", "o", "ん", "n",
				"が", "ga", "ぎ", "gi", "ぐ", "gu", "げ", "ge", "ご", "go",
				"ざ", "za", "じ", "ji", "ず", "zu", "ぜ", "ze", "ぞ", "zo",
				"だ", "da", "ぢ", "ji", "づ", "zu", "で", "de", "ど", "do",
				"ば", "ba", "び", "bi", "ぶ", "bu", "べ", "be", "ぼ", "bo",
				"ぱ", "pa", "ぴ", "pi", "ぷ", "pu", "ぺ", "pe", "ぽ", "po",
				"ゔ", "vu",
				"ぁ", "a", "ぃ", "i", "ぅ", "u", "ぇ", "e", "ぉ", "o",
				"ゃ", "ya", "ゅ", "yu", "ょ", "yo",
		};
		for (int i = 0; i < m.length; i += 2) MONO.put(m[i], m[i + 1]);

		String[] prefixes = {
				"き", "ky", "ぎ", "gy", "し", "sh", "じ", "j", "ぢ", "j", "ち", "ch",
				"に", "ny", "ひ", "hy", "び", "by", "ぴ", "py", "み", "my", "り", "ry",
		};
		String small = "ゃゅょ";
		String[] vowels = {"a", "u", "o"};
		for (int i = 0; i < prefixes.length; i += 2) {
			// Prefixes already carry their "y" where Hepburn needs one (ky, gy, ny...); sh/ch/j don't.
			String p = prefixes[i + 1];
			for (int k = 0; k < 3; k++) {
				DI.put(prefixes[i] + small.charAt(k), p + vowels[k]);
			}
		}
		String[] loan = {
				"ふぁ", "fa", "ふぃ", "fi", "ふぇ", "fe", "ふぉ", "fo",
				"てぃ", "ti", "でぃ", "di", "とぅ", "tu", "どぅ", "du",
				"うぃ", "wi", "うぇ", "we", "うぉ", "wo",
				"ゔぁ", "va", "ゔぃ", "vi", "ゔぇ", "ve", "ゔぉ", "vo",
				"しぇ", "she", "じぇ", "je", "ちぇ", "che",
		};
		for (int i = 0; i < loan.length; i += 2) DI.put(loan[i], loan[i + 1]);
	}

	static String toRomaji(String text) {
		String s = toHiragana(text);
		StringBuilder out = new StringBuilder();
		boolean doubleNext = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == 'っ') {
				doubleNext = true;
				continue;
			}
			if (c == 'ー') {
				if (out.length() > 0) {
					char last = out.charAt(out.length() - 1);
					if ("aiueo".indexOf(last) >= 0) out.append(last);
				}
				continue;
			}
			String r = null;
			int used = 1;
			if (i + 1 < s.length()) {
				r = DI.get(s.substring(i, i + 2));
				if (r != null) used = 2;
			}
			if (r == null) r = MONO.get(String.valueOf(c));
			if (r == null) {
				// "・", spaces, latin letters, digits...
				if (c == '・') out.append(' ');
				else out.append(Character.toLowerCase(c));
				doubleNext = false;
				continue;
			}
			if (doubleNext) {
				if (r.startsWith("ch")) out.append('t');
				else out.append(r.charAt(0));
				doubleNext = false;
			}
			out.append(r);
			i += used - 1;
		}
		return out.toString().trim();
	}

	// ---------------------------------------------------------------- fuzzy matching

	static int levenshtein(String a, String b) {
		int[] prev = new int[b.length() + 1];
		int[] cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) prev[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}

	static double similarity(String a, String b) {
		int max = Math.max(a.length(), b.length());
		if (max == 0) return 1.0;
		return 1.0 - (double) levenshtein(a, b) / max;
	}

	/**
	 * Best similarity of {@code expected} against any window of {@code heard} of roughly the
	 * same length, so extra words / background chatter around the right word don't hurt.
	 */
	static double bestWindowSimilarity(String expected, String heard) {
		if (expected.isEmpty() || heard.isEmpty()) return 0.0;
		if (heard.contains(expected)) return 1.0;
		double best = similarity(expected, heard);
		int n = expected.length();
		for (int len = Math.max(1, n - 1); len <= n + 1; len++) {
			for (int start = 0; start + len <= heard.length(); start++) {
				best = Math.max(best, similarity(expected, heard.substring(start, start + len)));
			}
		}
		return best;
	}
}
