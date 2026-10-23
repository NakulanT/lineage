# Lineage

A 2D world where three creature species eat, hunt, breed and slowly evolve. Nobody controls them.
The same simulation runs on a laptop (Mac, Windows, Linux) and on an Android phone.

Plans and decisions are in `PROJECT_CONTEXT.md` and `PHASE_2.md`.

## What is in the box

| Folder | What it is |
|---|---|
| `core/` | The simulation. Plain Kotlin, no screen. Creatures, plants, shelters, evolution, history log, save/load. |
| `ui/` | The screen, drawn the same way everywhere: map, creatures, tap-to-inspect, graph, stats panel. |
| `desktop/` | Laptop app (Java2D window). |
| `android/` | Phone app. Runs the world in a foreground service so it keeps going with the screen off. |

## Learning brains

Creatures are driven by learning brains (reinforcement learning, PPO), a few brain families per species. They start
from random weights and learn while the world runs. Females learn which males to accept. `--pretrain N` lets them
copy fixed instincts for N steps first. Full details of the body, senses and brain: `ANATOMY.md`.
Learning progress goes to `training.csv`, the brains to `brains.bin`. Use `--rules` for the old fixed instincts.

**Is it learning?** Every 10,000 steps a frozen copy of the brains takes an *exam*: it plays the same test world
each time, without learning. A rising exam score means better brains. The top bar shows an arrow per species
(↑ improving, → flat, ↓ getting worse) and the `learning` tab of the graph shows the reward over time.

## Versions

Every run is a **version** with its own folder and settings, so runs never mix and can be compared.
On screen: the version button (top right, e.g. `v2 ▾`) lists all versions with their verdicts, opens an older one,
makes a new one (`+ New version`: starting numbers, plants, and each of the 15 fixes on or off) and compares them
(`Compare`: the learning of every version on the same chart). `restart` (tap twice) makes a new version with the same
settings and new random brains.

**Settings (thresholds):** `+ New version`, or `copy+edit` on any version, opens every number the world and the brains
use, in tabs: World, Reward, Shelters, Body, Senses, Breeding, Learning (per-species values side by side; on the laptop
click a number to type it). `Basics` has presets, the 15 fixes as switches, and a choice to start from random brains or
from another version's trained brains. A version's settings never change after it starts, so versions stay comparable.
The full list with ranges is in `core/src/main/kotlin/lineage/core/Params.kt`.

**Inside a brain:** tap a creature, then `brain`: its senses, its neurons lighting up, the chance of each choice, and
which senses pushed it to its choice. The stats page's `Brains` tab shows the same for a whole species.
`./gradlew :core:run --args="--explain ~/LineageRuns/v8"` writes `brain_report.csv` and `brain_neurons.csv`.

**Brains that evolve (fixes 16-19):** each brain family carries genes (brain size, learning rate, curiosity,
look-ahead, and motivations: how much it cares about food, mating, babies, hurt, hunger, exploring, death). Weak
families are replaced by changed copies of the strongest, and bigger brains eat more, so evolution decides. Parents
are rewarded when their babies grow up, litters cost by size, and immigrants replace rescues (no brain resets).
Stats page `Evolution` tab, and `brain_genes.csv`. Details: `ANATOMY.md` section 14.

**Stats page:** `stats` in the top bar (key `T`) shows, per species, whether its brains are improving (exam and live
verdicts with the numbers behind them) and charts of exam score, live reward, curiosity and population, plus what the
brains choose now, how they die, mate choice, and brain families. Versions are kept in `~/LineageRuns/v2`, `v3`, ...; the old folder `live` shows as v1.

Current test: 5 Alpha, 20 Beta, 100 Gamma on a 128 x 128 map (change with `--alpha --beta --gamma --size`).

## Run on a laptop

You need a JDK 17 or newer. Nothing else: the `gradlew` script downloads Gradle by itself.

```
./gradlew :desktop:run          # Mac and Linux
gradlew.bat :desktop:run        # Windows
```

The first run downloads dependencies (a few minutes). The app opens the version used last time from
`~/LineageRuns` (on Windows: `C:\Users\<you>\LineageRuns`). Closing the window saves the world, and the next start
carries on from it. Add `--fresh` to start a new version from the command line:

```
./gradlew :desktop:run --args="--fresh"
```

Controls: drag to move; zoom with the trackpad / mouse wheel, `+` / `-`, or the on-screen `+` `−` `fit` buttons;
click a creature to inspect it; click a plant to see its scent range; double-click or `F` to fit the map;
`Space` pause, `[` / `]` slower / faster, `V` versions, `T` stats, `L` population / learning graph, `H` help, `S` save now,
`Esc` close. The button at the top right cycles 1x / 5x / 20x / max / paused. `?` shows the help on screen.
Tap `follow` in a creature's panel to keep it in view.

## Run on the phone

You need the Android SDK (Android Studio installs it) and USB debugging on the phone.

```
./gradlew :android:installDebug
```

Or open this folder in Android Studio and press Run. Pinch to zoom, drag to move, tap a creature to inspect it,
double-tap to fit the map. The first start asks to stop battery limits for the app and to show a notification;
allow both so the world keeps running with the screen off.

## Run with no screen (balance testing)

```
./gradlew :core:run --args="--ticks 60000 --report 5000"
./gradlew :core:run --args="--ticks 100000 --out runs/test1"        # also writes the history files
```

Options: `--seed N --alpha N --beta N --gamma N --size N --cap N --plants N --resume`,
`--v1` (all fixes off), `--fixes-off 1,4,15`, `--settings v3/settings.txt` (a version's settings).

Compare runs (start and end reward, exam scores and a verdict per species):

```
./gradlew :core:run --args="--compare runs/a runs/b"
```
One step is 0.1 s at normal speed, so 60,000 steps is about 100 minutes of world time.

Tests: `./gradlew :core:test`

## The history log

Written as plain CSV files into the run folder, append-only:

- `population.csv`: one row per species every 600 steps (counts, males, females, babies, births, deaths by cause, average traits, food, shelter use).
- `events.csv`: kills, extinctions, rescues, top-ups, brain copies and brain resets (births and deaths can be switched on in `SimConfig`).
- `training.csv`: every learning update, every exam, mate-choice statistics (columns in `ANATOMY.md` 3.8).
- `settings.txt` and `note.txt`: the version's settings and what it is about.
- `brain_genes.csv`: every family check, each brain family's members and genes (size, learning settings, motivations).
- `creatures.csv`: life record of notable creatures (3 or more offspring or kills, or lived to old age).
- `run.txt`: the settings of the run. `world.save`: the full world, so a restart carries on.

## Settings

All numbers are in `core/src/main/kotlin/lineage/core/`: `SimConfig.kt` (map size, counts, cap, plants, mutation,
logging) and `Species.kt` (each species' speed, health, senses, breeding, shelter size).
