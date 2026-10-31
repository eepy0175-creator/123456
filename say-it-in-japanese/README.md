# Say It In Japanese (Fabric, Minecraft 26.2)

Every time you pick up an item, a pop-up shows its Japanese name and you have a few
seconds to **say it out loud**. Say it right and you keep the item. Miss it and it's deleted.

The pop-up can show the word as **Kanji**, **Hiragana** or **Romaji**.

## Build

Needs JDK 25.

```
./gradlew build        # Windows: gradlew.bat build
```

The jar is `build/libs/say-it-in-japanese-1.0.0.jar`. Drop it in `mods/` together with
Fabric API (`0.161.0+26.2` or newer) on Fabric Loader 0.20.1+.

Vosk, JNA and Kuromoji are bundled inside the jar (jar-in-jar).

## First launch

On first join the mod downloads the offline Japanese speech model (`vosk-model-small-ja-0.22`,
about 50 MB) into `config/sayitinjapanese/model`. After that everything runs offline.
Turn that off with `"autoDownloadModel": false` and run `/sayjp download` yourself.

The mod also reads Minecraft's own `ja_jp` language file for the item names. The launcher
downloads it by default; if the mod complains, switch the game language to 日本語 once.

You need a working microphone and OS permission for Java / the Minecraft launcher to use it.

## Commands (client side)

| Command | What it does |
|---|---|
| `/sayjp script kanji\|hiragana\|romaji` | How the word is displayed |
| `/sayjp seconds <1-60>` | Time limit per item (default 4) |
| `/sayjp strictness <0-100>` | How closely you must match (default 60, lower = more forgiving) |
| `/sayjp enabled <true\|false>` | Master switch |
| `/sayjp delete <true\|false>` | `false` = practice mode, nothing is deleted |
| `/sayjp mic list\|default\|use <name>` | Pick a microphone |
| `/sayjp test` | Practice round with a diamond |
| `/sayjp download` | (Re)download the speech model |
| `/sayjp status` | Show the current settings |

Everything is saved in `config/sayitinjapanese.json` (also hand-editable).

## How it works

* **Pick-up detection:** each client tick the mod compares your inventory to the previous
  tick. If an item count went up while no screen is open, that's a pick-up. Picking the same
  item up again while it's being asked about just adds to the count. Creative mode is skipped
  (configurable).
* **Names:** the Japanese name comes from `ja_jp.json`. Kuromoji turns it into a hiragana
  reading, and a small Hepburn converter makes the romaji.
* **Listening:** Vosk transcribes the microphone; the transcript is converted to hiragana and
  fuzzy-compared with the expected reading, so small recognition slips are forgiven.
* **Deleting:** on failure the client sends a small packet and the **server** removes that many
  of the item from your inventory. On a dedicated server the mod must be installed there too,
  otherwise nothing can be deleted.

## Known limits

* Items you pick up while an inventory/container screen is open are not challenged.
* Items without a Japanese name in the vanilla language file (modded items) are let through,
  as are names with nothing to pronounce (e.g. "TNT").
* The small Vosk model is not perfect. If it is too harsh, lower `strictness` or use the big
  `vosk-model-ja-0.22` through `modelUrl`.
