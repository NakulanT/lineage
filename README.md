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

Creatures are driven by learning brains (reinforcement learning, PPO), one per species. They start from random
weights and learn while the world runs. `--pretrain N` lets them copy fixed instincts for N steps first.
Full details of the body, senses and brain: `ANATOMY.md`.
Learning progress goes to `training.csv`, the brains to `brains.bin`. Use `--rules` for the old fixed instincts.

Current test: 5 Alpha, 20 Beta, 100 Gamma on a 128 x 128 map (change with `--alpha --beta --gamma --size`).

## Run on a laptop

You need a JDK 17 or newer. Nothing else: the `gradlew` script downloads Gradle by itself.

```
./gradlew :desktop:run          # Mac and Linux
gradlew.bat :desktop:run        # Windows
```

The first run downloads dependencies (a few minutes). The world is saved and the history is written to
`~/LineageRuns/live` (on Windows: `C:\Users\<you>\LineageRuns\live`). Closing the window saves the world, and the
next start carries on from it. Add `--fresh` to start a new world:

```
./gradlew :desktop:run --args="--fresh --seed 5"
```

Controls: drag to move; zoom with the trackpad / mouse wheel, `+` / `-`, or the on-screen `+` `−` `fit` buttons;
click a creature to inspect it; click a plant to see its scent range; double-click or `F` to fit the map;
`Space` pause, `[` / `]` slower / faster, `S` save now, `Esc` close the panel. The button at the top right cycles
1x / 5x / 20x / max / paused.

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

Options: `--seed N --alpha N --beta N --gamma N --size N --cap N --plants N --resume`.
One step is 0.1 s at normal speed, so 60,000 steps is about 100 minutes of world time.

Tests: `./gradlew :core:test`

## The history log

Written as plain CSV files into the run folder, append-only:

- `population.csv`: one row per species every 600 steps (counts, males, females, babies, births, deaths by cause, average traits, food, shelter use).
- `events.csv`: kills, extinctions and rescues (births and deaths can be switched on in `SimConfig`).
- `creatures.csv`: life record of notable creatures (3 or more offspring or kills, or lived to old age).
- `run.txt`: the settings of the run. `world.save`: the full world, so a restart carries on.

## Settings

All numbers are in `core/src/main/kotlin/lineage/core/`: `SimConfig.kt` (map size, counts, cap, plants, mutation,
logging) and `Species.kt` (each species' speed, health, senses, breeding, shelter size).
