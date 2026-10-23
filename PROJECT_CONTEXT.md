# Project plan: A simple world where creatures evolve

Status key: **[Decided]** = confirmed by me. **[Idea]** = my idea, details still open. **[Proposed]** = suggested during planning, not confirmed yet.

This file is a plan only. Technology choices will be designed later.

## 1. The idea [Decided]
I'm creating a simple 2D world where I create creatures and make them evolve to do things. Nobody controls them. They survive, breed and slowly change over generations.

## 2. Look of the world [Decided]
- The world looks like the original GTA 1: 2D, top-down, flat bright colors.
- GTA is the visual style only. It is not the setting.
- The world is **simple, with simple things in it.** A wild forest was only a reference for the feel, not an exact setting.
- It is not a city: no roads, cars, police or gangs.
- No night or day.
- [Decided, updated after the mockup] The world is a **plain 2D grid** with no trees, berries, paths or other decoration. See section 18 for the final look.

## 3. Where it runs [Decided]
- On my old phone, a Xiaomi POCO X5 5G (Snapdragon 695, 6 or 8 GB RAM, 6.67 inch AMOLED, 5000 mAh). I now use a new phone.
- The old phone is dedicated to this project, with 24/7 power, so the world can keep running all the time.

## 4. Species [Decided]
Three species:
- **Alpha** (top)
- **Beta** (middle)
- **Gamma** (bottom)

Differences I decided:
- Alpha has more health than Beta and Gamma.
- Alpha has a longer lifespan than Beta and Gamma.

Mating rule: **a species can only mate with its own species.**
Reason: if species could mate with each other, one species could suppress the others and wipe them out.

Genders: **each species has males and females.** A male and a female of the same species can mate and produce babies (see section 11).

## 5. What creatures can do (actions)
[Decided] I want these actions:
- Mate
- Attack
- Consume food
- Shelter

[Proposed] Also needed to make the world work: move and turn, rest, and send a signal to others.

[Proposed] Species do not get different actions. Instead they are different at them (Gamma is fast but weak, Alpha is slow but strong, and so on).

## 6. What creatures sense (sensors) [Proposed]
- Vision: food, meat, obstacles, shelter, other creatures, and which species a creature is.
- Body: own health, energy, age, whether in shelter, whether just hurt, whether able to mate.
- Hearing: signals from nearby creatures.
- Smell (optional, later): meat scent, trails.

Species could differ in how far or how widely they sense (for example Gamma sees wide but short, Alpha sees far but narrow).

## 7. Features a species can have [Decided as a list to work from]
Body
- Max health
- Lifespan
- Speed
- Strength
- Vision range
- Max energy

Food and energy
- Hunger rate
- Diet (plants, meat, or both)

Reproduction
- Maturity age
- Mating energy cost
- Cooldown between babies
- Litter size

Combat
- Attack range and cooldown

Shelter and survival
- Shelter preference
- Healing rate in shelter

Social
- Food sharing

Mind
- How smart the species is (brain capacity)
- How fast it changes between generations (mutation rate)

Removed as duplicates or not needed (decided): fat reserve, armor/defense, size, energy gained per food, disease resistance, season tolerance, night/day activity, aggression, flee threshold, memory (social memory covers it), recognizes other species (it is a sense, not a trait), can it eat other creatures (diet covers it), pack tendency, territory, and color and shape (looks come last).

## 8. Evolution
[Decided] I want species to evolve.
[Proposed] How it works in plain words:
- Babies are born slightly different from their parents (variation).
- The world kills some creatures and lets others breed (selection).
- Babies inherit from their parents (inheritance).
- Every good trait needs a cost (more health means more hunger, and so on), otherwise everything just maxes out.
- Over many generations a species can drift far from its starting values.

## 9. Memory
[Decided] I like **social memory** because it feels realistic: creatures remember specific individuals, not just species.
[Proposed] Details to work from:
- What is remembered: who the other creature is, how it relates (family, mate, or neither), what it did to me (attacked me, shared food, helped), and roughly how good or bad that was.
- Effects: avoid those who hurt it, stay near family and trusted ones, protect kids and mates, hold grudges, prefer known partners when mating.
- Limits: only a small number of individuals can be remembered (about 5 to 10), memories fade unless refreshed, strong events last longer, newborns start blank except maybe their parents, and remembering costs a little energy.
- Species could differ in how many individuals they remember and for how long.
- Things that may emerge: family groups and packs, grudges, reputations, trust networks.

Open:
- How many individuals can one creature remember, and how fast do memories fade?
- Can creatures tell each other what they know (like a warning)?
- Do memories of enemies pass to babies, or do babies start fresh?

## 10. Food sharing [Decided]
- Creatures of the same species can share food, but only with those they are directly connected to: **partner, babies, or parents.**
- Not with strangers, other species, or enemies.
- The giver loses exactly what the receiver gains.
[Proposed] Limits: a small cost or cooldown, and no one can give so much that it starves itself.

Open:
- Does sharing differ by species (for example Alpha shares rarely, Gamma shares more)?

## 11. Genders and babies [Decided]
- Two genders: male and female.
- A male and a female of the **same species** can mate and produce babies.
- No pregnancy or waiting time: babies appear when mating succeeds.
- Mating is **not instant from birth**: a creature must reach a specific age (maturity) before it can mate.
- **Females have a cooldown period** after giving birth before they can mate again.
- **Males have no cooldown or restrictions** on mating (I'm now reconsidering a short male cooldown, see below).
- **Females choose.** A female does not mate with every male. She decides which male is attractive and mates with that one.
- **Males have more freedom to roam.** They travel farther to find shelter, find more food, and attract (be found by) females.
- **Infants stay near their mother** for some time after birth, so they are protected and don't die early.
[Proposed] Details for the infant stage:
- Infants are weak while young (low health, can't fight well), so staying close to the mother is what keeps them alive.
- The mother can feed them (parents may already share food with babies, see section 10).
- When the infant stage ends, the young creature goes off on its own. It is not tied to maturity age; it could end earlier or around the same time.
- If the mother dies while the infants are young, they are in much more danger (they might stay near the father or other relatives, to be decided).
[Proposed] Safety rule: if one gender of a species drops too low, a few are added so the species doesn't die out from an unlucky ratio. Babies are born about 50/50 male and female.

[Proposed, not picked yet] Options with a recommendation for each:

*What makes a male attractive*
- Options: health, strength or size, prime age, remembered kindness (ties to social memory), shares food.
- Recommended: a mix of health, strength and remembered kindness. Each female weighs these differently (one cares most about strength, another about kindness), and the choice has a little randomness. This keeps one male from taking over every female.

*Do genders differ*
- Options: identical, mild difference, strong difference.
- Recommended: mild. Males a bit bigger and stronger but they use more energy and live a little shorter. Females live a bit longer and need slightly less food. The cost makes strength an honest sign of health.

*Maturity age and female cooldown*
- Alpha matures late and breeds slowly, so it has few babies.
- Gamma matures early and breeds fast, so it has many babies.
- Beta is in the middle.
- Babies per birth could also differ (Alpha fewest, Gamma most).
- Exact timings are not decided.

*Male cooldown*
- Problem with none: one very attractive male could father most of the next generation and the species loses variety.
- Options: none, short, or the same as females.
- Recommended: short, much shorter than the female one. Can also start with none and add it only if one male's family takes over.

[Proposed] Roaming details:
- Females, especially with infants, stay closer to home. Males range wider.
- Benefits of roaming: finds new food and shelter spots, more chances to meet females, and the group learns where things are.
- Costs of roaming: more energy used, more exposure to danger and to other species, and a higher chance of dying alone.
- Fits with males being a little more costly to run (see the gender differences options).
- A male who finds food or shelter can bring it back to family: share food with partner and babies (section 10).

Open:
- Do males come back to their family, or can they wander off for good?
- How far can males roam compared with females?
- How long do infants stay near the mother, and do they follow her automatically or by choice?
- If the mother dies, do infants stay with the father or other relatives?
- Does the father help look after infants at all?
- Pick: what makes a male attractive.
- Pick: how much the genders differ.
- Pick: maturity age and female cooldown per species.
- Pick: male cooldown (none, short, or same as female).

## 12. Killing within the same species [Idea]
I raised this idea: if there is nothing else to survive on, creatures can kill their own species. But if others of the same species see it happening, they begin to omit (shun) the killer.

[Proposed] How it could work:
- Killing your own kind is only possible as a last resort, when a creature is starving and has nothing else to eat.
- Only creatures that actually see it happen react, and they remember who did it (links to social memory).
- Mild reactions first: avoid the killer, refuse to share food with them, and females refuse to choose them as a mate.
- Stronger reactions are possible later: chase the killer away from the group, or the group attacks them.
- If witnesses can warn others, the killer's reputation spreads and more of the group shuns them.
- Killing babies or family is the worst case: mothers defend infants and the punishment is harsher.
- Eating a body that died naturally is not a crime. Only killing is punished.
- Killing should cost energy and risk injury, and the meat should be worth less than the creature that died, so nobody can farm their own species for easy food.
- Effect: natural population control. When a crowded species runs out of food, some kill each other, but those who do pay a social price.

Open:
- Is it allowed never, only when starving, or any time? (Recommended: only when starving.)
- Does it have to be seen to be punished, or does the news spread?
- How strong is the punishment?

## 13. Other ideas I raised, details still open
- **Creatures increasing their own abilities** (like leveling up). Open: does it pass to babies, how much, and what are the caps?
- **Communication between species.** Open: can all species communicate with each other, and are some signals universal or does the meaning evolve?

## 14. Food [Proposed, nothing chosen yet]
- Plant food: berries, big fruit (rare), poison plants, healing plants.
- Meat: comes from dead creatures; rots over time; plant eaters get little from it.
- Rule idea: meat must be worth less than what it cost to make the creature, so nobody can farm babies for meat.

## 15. Open questions
- Exact starting numbers for each species.
- Whether each species has a different level of intelligence.
- Which food types and extras go into the first version.
- Should meat come from every death, or only from kills?
- Social memory: how many individuals, how fast it fades, can it be shared or inherited?
- Killing within a species: allowed when, and how is it punished?
- World size.

## 16. Suggested order for building the first version [Proposed]
1. A small, simple world with food.
2. Two species first (Gamma and Alpha) with attack and mating.
3. Add Beta and shelter.
4. Add a simple graph of how many of each species exist over time, to check the balance.
5. Phase 2: add the extras one at a time. See `PHASE_2.md`.

## 17. Tech stack [Proposed, waiting for confirmation]
- **Language and platform:** Kotlin, native Android app, on the old POCO X5 5G.
- **Simulation core:** plain Kotlin module with no Android code. Fixed timestep, seeded random numbers, creatures stored in flat arrays. It can run on a laptop with no screen to test balance (needed for Phase 2: add one feature, check the numbers).
- **Brains:** small fixed-shape neural net per creature. The genome is a list of numbers, mutated with small random changes. "Brain capacity" later means a bigger hidden layer.
- **Keeping it running 24/7:** an Android foreground service with a wake lock and battery-optimization exemption.
- **Drawing:** `SurfaceView` with Canvas, flat 2D shapes. Drawing can pause when the screen is off.
- **Saving:** a snapshot every few minutes, plus a log file of population per species, gender ratio and rescue events. The graph reads from the log.
- **Optional later:** a small built-in web server so the world can be watched from another phone or a laptop.
- Not chosen: Godot (engine too heavy for flat shapes, harder to test headless), web/PWA or Termux (Android suspends them), Rust/C++ core (extra build work, not needed at this scale).

## 18. Look and screen [Decided: the user approved the mockup UI]
Mockup: https://claude.ai/artifact/1eijSGLAF9XvutJzxb8y42 (not a real simulation, only a visual loop).

- **World:** plain dark grid, nothing else drawn on it.
- **Species:** each is a different drawn animal, told apart by design and size.
  - Alpha: biggest, bulky bear or boar with tusks.
  - Beta: medium, slim fox with a long tail.
  - Gamma: smallest, rodent with big round ears.
- **No gender colors.** Each species has one color only, the same color as its shelter (Alpha amber, Beta violet, Gamma lime). In the mockup, gender shows as a small size difference (males about 12% larger than females) and as text in the creature's stats panel. To confirm: is that enough, or should gender show some other way?
- **Babies:** same animal, smaller. They grow to full size over time.
- **Plant food:** a small green sprout. It is eaten down while a creature feeds and regrows in the same spot.
- **Meat:** a small drumstick left where a creature was killed. It shrinks while eaten.
- **Shelter:** a block of grid cells. Each species has its own shelter with its own size and its own single color. In the mockup Alpha is the largest (amber, 8 by 6 cells), Beta is medium (violet, 6 by 5) and Gamma is the smallest (lime, 4 by 3). Other creatures stay out of it. Creatures of that species rest inside.
- **Animations:** walking (legs swing, body bobs, tail wags, creature turns), eating (head dips and chews), attacking (lunge, hit flash, slash marks), dying (falls over, fades, leaves meat).
- **Screen is kept light:** a thin top line with the three species counts and the generation number, the grid world, one small population graph, and a one-line event text (for example "Alpha attacks Beta 2/3"). No other menus in the first version.

## 19. How it works (rules shown or agreed so far)
- **A creature's day:** idle, walk, go to food, eat, rest in shelter, hunt, attack, mate.
- **Eating plants:** a creature walks to a full-grown sprout, eats it down over about two seconds, and the sprout grows back over time in the same place. A sprout being eaten cannot be taken by someone else.
- **Hunting:** a hunter picks a full-grown prey, chases it faster than it can run, and strikes. The mockup uses 3 hits to kill. The prey flees, then stops while being hit. A hunter ignores babies in the mockup.
- **Death:** the body fades and leaves meat. The killer (or anyone) can eat the meat down.
- **Nothing appears from nowhere:** new creatures only come from a birth next to a living mother. Food only regrows where it was. (The mockup scripts the birth from one mother, the real game will come from two parents mating.)
- **Alpha can be killed.** More health and a longer life do not make it immune. It can die from a pack of Gamma or Beta, starvation, old age, or injury that heals slowly outside a shelter. The trait costs (bigger body means more hunger) are what keep Alpha from winning every time.

## 20. To confirm (please check each one)
1. **Tech stack (section 17):** Kotlin native Android with the sim kept separate from the screen. Yes or change?
2. **Shelter rule:** does "enemies cannot enter" mean only the owner species can enter, or any creature can enter and only attackers are blocked? Right now the mockup keeps everyone except the sheltered creatures out.
3. **Shelter size:** is 6 by 5 cells fine, or bigger, or a different size per species?
4. **Rescue rule (section 11 and Phase 2 item 10):** it adds creatures when a species or gender runs very low. That clashes with "nothing appears from nowhere". Keep it as a rare, logged exception, drop it, or replace it with something like a migrant walking in from the edge?
5. **Kill count:** is 3 hits to kill a Beta right, or should it depend on health and strength?
6. **Gender colors:** blue for male and pink for female. Keep or change?
7. **Screen content:** is the thin top line, grid, one graph and one event line enough for version 1?
8. **Attack on babies:** can hunters kill babies, or are babies protected in the first version?
9. **Groups attacking an Alpha:** keep it possible (recommended), and show it in the mockup?
10. **Still open from before:** starting numbers per species, world size, which food types go in the first version, male cooldown, what makes a male attractive, maturity ages and female cooldown, social memory limits.
11. **Extinction:** what happens when a whole species dies out (see section 21, recommended: let it go extinct, log it, keep the world running).
12. **History log (section 21):** is this the right set of things to record?

## 21. History log [Proposed, this is the main output besides the screen]
The world runs for months, so the log is the real result. The screen only shows now; the log shows everything that happened. It is written by the simulation itself, so it works with the screen off and can be replayed or exported.

**Rules**
- Append only. Nothing is edited or deleted, so history can be trusted.
- Every record carries the time and the generation number.
- The seed and the version of the rules are stored with the log, so a run can be repeated exactly.
- The log never slows the world down: records are written in batches.
- Old detail is thinned out (for example per-minute data becomes per-hour after a week), but events are never thinned.

**1. Population over time** (one row per minute, per species)
- Count, males, females, babies, average age.
- Births, deaths, and deaths split by cause: killed, starved, old age, injury.
- Average of every trait (health, speed, strength, vision, lifespan and so on), so you can see evolution drift.
- Food available, meat available, creatures in shelter.

**2. Events** (one row each, forever)
- Birth: who, both parents, species, where.
- Death: who, cause, age, killer if any, where.
- Kill and attack: who, victim, hits, outcome.
- Mating: female, male, and why she chose him.
- Extinction of a species, a gender running out, a rescue (if the rule is used). Each is flagged as important.
- Famine, population crash and boom (detected automatically when a count changes by a set amount).
- Sharing food, grudges and shunning, once Phase 2 adds them.

**3. Family lines** (so lineage can be traced)
- Every creature ever born: id, species, gender, parents, birth time, death time, cause, final traits.
- This gives family trees, longest lines, and who is the ancestor of the living.

**4. Snapshots**
- Full world save every few minutes (survives crashes and reboots).
- A permanent snapshot at every milestone: each 10 generations, every extinction, every Phase 2 feature being switched on.

**5. Run notes**
- Settings used, which Phase 2 features are on, when they were switched on, and any change you made by hand. This makes it possible to explain why a change in the graphs happened.

**What you can do with it**
- Graphs of any of the above over any period.
- Find the exact moment and cause of a collapse.
- Compare two runs with different settings.
- Export to a file and read it on a laptop.

**Storage** (to confirm): one small database file on the phone, plus a copy exported to a laptop or cloud on a schedule so the phone dying does not lose months of history.

**Open**
- How long to keep per-minute detail before thinning.
- Where the backup copy goes.
- Whether to keep every creature's life record forever or only for those that had children.

## 22. World size and population numbers [Test numbers set by the user, rest proposed]
**Test run (decided by the user):** 100 Alpha, 500 Beta, 2,000 Gamma. That is 2,600 creatures, a ratio of 1 : 5 : 20.

**Proposed settings for the test run (to confirm)**
- Map: 256 by 256 cells (about 65,000 cells), about 25 cells per creature at the start.
- Hard cap on total creatures: 5,000, so growth cannot run away.
- Plants: about 6,500 to start (2.5 per creature), regrowing in place.
- Shelters (one per about 25 creatures of that species, own size and color per species): 4 Alpha, 20 Beta, 80 Gamma.
- Speed target on the phone: 10 steps per second, no more than 40% of one fast core, so the phone stays cool for 24/7.
- Map size, caps and starting counts are settings, not fixed in the code, so bigger worlds later are a settings change.

**Risks to watch in the test**
- 100 Alpha hunting 500 Beta could wipe Beta out quickly. Watch the Beta count in the first generations.
- 2,000 Gamma need a lot of plants. Plant regrowth time and scent range must be tuned against their numbers.
- These numbers are estimates until measured. The first build includes a speed test on the old phone to confirm 2,600 creatures run at 10 steps per second.

**Other sizes considered:** 128 by 128 with about 300 creatures for quick laptop tests, and 512 by 512 with about 8,000 creatures for a later big run.

## 23. First working build [Done, see README.md]
Code is in this folder. Shared Kotlin simulation (`core`), shared screen (`ui`), laptop app (`desktop`), phone app (`android`).

**What works (tested)**
- Runs on Mac (tested here), Windows and Linux (same Java code, not tested there) and on Android (installed and run on a Pixel 8 emulator, 10 steps per second; not yet tried on the real POCO X5).
- The test run: 100 Alpha, 500 Beta, 2,000 Gamma on 256 by 256 cells, cap 5,000, 6,500 plants, 4/20/80 shelters.
- Creatures: plant eating (only ripe plants, scent range 6 cells), Alpha hunting (sprint, attack, kill, meat), fleeing and hiding in own-species shelters, females choosing males, litters paid for from the mother's energy, babies following the mother and being fed by her, ageing, starvation, evolution (6 traits copied from the parents with small random changes, every strong trait costs energy).
- Screen: tap a creature for sight cone, hearing ring, footprints (last 40 steps) and stats (health, energy, age, kills, times mated, last mated, offspring, meals, distance, gender). Pinch or wheel to zoom, speed button 1x / 5x / 20x / max / pause.
- History log (CSV files) and full save/load, with the log flushed every minute. Phone app runs in a foreground service so it keeps going with the screen off.
- Six automated tests: same seed gives the same world, cap never passed, strangers never inside a shelter, plants only eaten when ripe, save and load, log files written.

**Speed measured on the Mac (headless, Intel)**: about 1,000 to 1,600 steps per second, which is 100 to 160 times normal speed with 2,600 to 5,000 creatures. The phone is not measured yet.

**Balance so far (rough, needs tuning)**
- Early boom: Gamma grows from 2,000 to about 3,500 in the first day or two before food limits it.
- 80,000 steps (about 2.2 hours of world time at normal speed): all three species survive with the current numbers, but Gamma ends low (about 100) and Alpha drifts down (about 60). Small changes to plant efficiency swing the winner: Gamma wins when Beta eats at 1.1 or less, Beta wins at 1.25.
- Alpha need many small kills and breed slowly, so they are the first to disappear when prey is scarce.
- Alpha only sees one tenth of the map around it, so hunts are rare. Planned fix: tune after watching a long run.

**Choices made without asking (change if wrong)**
- Gender shows as size (males 12% bigger) and in the stats panel, no gender colors.
- Strangers cannot enter a shelter (owner only).
- Species lose nothing when extinct: the run goes on, the extinction is logged, no rescue (switch exists, off).
- History is thinned: births and deaths are counted per minute, not listed one by one; only notable creatures (3 or more offspring or kills, or old age) get a life record. Reason: listing every creature would be about 200 MB a day.
- Brains: creatures use fixed instincts for what to do and evolve their body traits. Evolving real neural-net brains is left for Phase 2 (intelligence per species).

## 24. Learning brains (reinforcement learning) [Decided: the main goal is RL]
- Each species has its own brain: a small actor-critic neural network (24 inputs, 2 hidden layers of 32, 9 choices), trained with PPO. Written in plain Kotlin, so it learns on the laptop and on the phone, 24/7, with no Python.
- Senses (inputs): energy, health, age, mature, gender, baby, waiting to breed, in own shelter, hurt, and how close the nearest ripe plant, meat, predator, prey, mate, own shelter and mother are.
- Choices (outputs): explore, eat plant, eat meat, hunt, flee, go to shelter, mate, follow mother, rest. Choices that make no sense right now are masked out. The body carries out the choice (walking, eating, fighting).
- Reward: +0.01 for every decision alive, plus energy gained, minus health lost, +2 per baby for the mother and +1 for the father, -3 for dying by being killed or starving (no penalty for old age).
- Start: for the first 6,000 steps (10 minutes at normal speed) the brains copy the old instincts, then they act on their own and keep learning.
- Saved as `brains.bin` next to `world.save`. Learning progress is written to `training.csv`.
- Current test: 10 Alpha, 200 Beta, 50 Gamma on 128 by 128 cells, cap 1,500, rescue switched on (logged) so learning does not stop when a species dies out.
- First 60,000-step run: learning works (about 3,000 updates, policies become more decisive). Beta grows to about 700, Gamma falls to single digits, Alpha needed 4 rescues. Balance still needs work.
- Open: is the reward right? Should females also learn when to accept a mate (now a fixed rule)? Should creatures learn while alive only, or should brains also be passed to babies with mutation (evolution of brains)?

## 25. Changes after the first RL run [Decided by the user]
- One plant type only (tall plants removed). Plants reduced to 1,000 on the 128 by 128 map.
- Shelters are circles. Other species can walk in, but lose 3% of max health every step inside.
- A plant's scent range is only shown when you click that plant.
- No grid lines. Gamma redrawn as a round fluffy "Puffkin" in sky blue.
- Zoom: trackpad or wheel, + / - keys, and on-screen + / − / fit buttons.
- Brains start from random weights (no copying of instincts unless `--pretrain N`).
- Full creature description: `ANATOMY.md`.
- Start changed to 5 Alpha, 20 Beta, 100 Gamma. The top bar shows species names next to the counts.
- Plants reduced again to 400. Mac trackpad pinch zoom added (two-finger scroll and mouse wheel also zoom).
- Restart button added (tap twice): new world and new random brain weights. Rescue explained in ANATOMY.md.
- Litters per mating set by the user: Alpha 5-10, Beta 15-20, Gamma 30-50. A litter costs the mother 50% of her max energy (father 10%). Brain reward +5 to both parents per mating that gives babies (replaces +2/+1 per baby).
- Rescue now rebuilds a wiped-out species from the genes of its 6 best creatures ever (most days lived, then most matings, then most kills), with small random changes. Population graph shows one row per species with its own scale.
- Brains were hiding in shelters (reward paid for staying alive cheaply). Fix: reward food, matings, hurt and hunger only; death -3 unless old age with babies; look-ahead gamma 0.995; entropy 0.03.

## 26. Version 2: fixes for the shelter trap and for learning [Decided by the user: "apply all these fixes"]
- Found in v1 after 1.49 million steps: Alpha chose "shelter" 90% of the time and Beta 99%, at 0 energy, with 398 of 400 plants ripe. A shelter healed faster than starving hurt, so hiding forever was safe. Brains locked in (entropy 0.1). 530 rescues.
- All 15 fixes added, each a switch per version (table in `ANATOMY.md` section 8): no shelter healing at 0 energy, shelter time limit, shelter room, more hunger cost in a shelter, plants smelled farther, exploring reward, adaptive curiosity, optional copying of instincts (off: still random start), minimum population and faster learning for small species, brain reset after many rescues, faster plant regrowth, cap 900, healthier babies, learned female mate choice, evolving brain families.
- The exam: every 10,000 steps a frozen copy of the brains plays the same test world for 3,000 steps. Its score is the fair test of learning (live reward also moves with food and crowding).
- Versions: every run has its own folder, settings and logs (`~/LineageRuns/v2`, ...). On screen: list, open, new version with switches, compare chart. Restart makes a new version. Old `live` folder = v1 (view only).
- Screen: learning tab in the graph, verdict arrows in the top bar, help screen, follow button.

## 27. Platform: every threshold per version, and a stats page [Decided by the user]
- Every number is a setting of a version: world, reward (food x, mating, death, hurt x, hunger level and penalties, exploring), shelters, body, senses, breeding (per species: speed, health, lifespan, energy, hunger, bite, senses, maturity, litters, ...), and learning (brain size, decision interval, PPO numbers, curiosity, batch, families, exams). List: `Params.kt`.
- New versions are made on screen ("+ New version" or "copy+edit"), from defaults, from v1-like settings, or from any version, and can start from random brains or from another version's brains. Settings are fixed once a version starts.
- Stats page per species: verdict (exam, else live reward), charts of exam score, live reward, curiosity and population, current choices, deaths by cause, mate choice, brain families.

## 28. Room for every species [Decided by the user]
- Found: Gamma (30-50 babies per mating) filled the shared 900 cap by day 6, so Alpha and Beta could not have babies at all. Also a bug: a top-up of one creature was always a female, so Alpha ended with 0 males.
- Fixed: each species has its own cap (Alpha 100, Beta 300, Gamma 500, inside the 900 total); top-ups add the missing sex; Gamma litters are 5-10 by default (changeable per version).
- Versions can be deleted from the versions list (tap delete twice). The running version cannot be deleted.

## 29. World rules set by the user
- Anyone can eat anything: every species eats plants and meat (Beta and Gamma get 70% from meat) and every species may attack the other species.
- Meat only comes from an accident (killed, or injured in a stranger's shelter), as much as the energy the body still had. Starving and old age leave no meat.
- After eating to full, a creature cannot eat again until its energy is below 70% (setting "Can eat again below").
- Shelters send a signal their own species can pick up from 30 cells away (drawn as spreading rings; tap a shelter to see the range).
- Shelters drift around the map (0.25 cells/s), bounce off the edges and never overlap. Food under a shelter cannot be eaten; plants start outside shelters and meat is never dropped inside one.
- Nobody can mate inside a shelter. Shelters are only for protection.
- All of these are settings per version (World and Shelters tabs).

## 30. Forage choice, hungry-rest cost, bigger brain [Decided by the user]
- Found in v5: only 27 of 400 plants ripe at a time; a free ripe plant within smell for 0-1% of Gamma and Beta decisions, so "eat plant" was almost never possible; brains could only explore (random walk) or rest. Exam scores flat for Alpha and Gamma.
- Added: a 10th choice "forage" (walk toward food sensed within 25 cells), a 0.02 penalty for resting below 50% energy, and a bigger brain (64 units, 2 layers by default, layers settable 1-4).
- First 40,000-step check: resting fell from 35-42% to 6-11% of choices, forage is used 5-18%, eating plants rose (Gamma 2% to 5%, Alpha to 21%), Gamma energy 42% to 58%. Too early for exam verdicts.

## 31. Group attacks, seeing health, cleaner map [Decided by the user]
- Only ripe plants are drawn; the shelter signal ring is no longer drawn.
- Bites: Beta 18, Gamma 12, so 5-7 Gammas can kill an Alpha (tested: 6 Gammas win at least 8 times in 10).
- Every species sees the health of the animal it senses and how many of its own and other species are around (4 new inputs, 34 in all).
- Cost noted: the bigger brain (64 units) makes learning about 3x slower per step (about 58 steps/s with 500 creatures on the Mac).

## 32. Strangers' shelters survivable, slower shelters, version names [Decided by the user]
- Tests over 2,000-3,000 game days: wipe-outs did not fall with time; about two thirds of Alpha deaths and most Beta deaths were "injury" (drifting strangers' shelters rolling over them at 3% health per step).
- New defaults: damage in a stranger's shelter 0.003 per step (about 30 s to die instead of 3 s), shelters drift at 0.05 cells/s (was 0.25).
- Versions can be named (rename in the versions list, or a name when creating one). The name is kept in label.txt; the folder (v7) does not change.

## 33. Looking inside the brains [Decided by the user]
- Brain view per creature (senses, neurons, choice chances, integrated-gradient reasons), a Brains tab per species (sense importance, senses x choices, neuron health), and `--explain` CSV reports. Details in ANATOMY.md section 12.
- Running alongside: a 2-layer vs 3-layer test (same seed and settings) to decide whether more hidden layers help.

## 34. Plant-lock bug, and brains that evolve (fixes 16-19) [Decided by the user]
- **Bug found in v10 (day 1,926):** all three species sat at the rescue minimum (3/6/12) from about day 130, with 400 of 400 plants ripe and most deaths from starvation. Cause: a creature that stopped eating halfway (brain decides every 6 steps, eating takes 20) or died kept its claim on the plant forever, so nobody else could ever eat it (a test: 362 of 500 plants locked after 8,000 steps). Fixed for every version.
- Plant fix alone, 500 days, seed 1: ripe plants left standing 374 -> 85 of 400; Beta 57 -> 204 on average (642 deaths of old age); but Beta then learned to hunt Gamma (2,112 of 2,480 kills), and Gamma was wiped out 104 times.
- Added (user's chosen list): babies-grow-up reward and mating reward 5 -> 1 (fix 16); litter cost by size with a 20% reserve, 75% energy to mate (fix 17); immigrants at the map edge at most every 30 days instead of rescues and brain resets (fix 18); brain genes per family (size, layers, learning rate, curiosity, look-ahead, 7 motivations) that mutate when a weak family is replaced, and bigger brains cost energy (fix 19). Evolution tab on the stats page, `brain_genes.csv`.
- 500-day tests, fixes 16-19 on vs off (both with the plant fix):

  | Run | Alpha avg / wiped | Beta avg | Gamma avg (2nd half) / wiped |
  |---|---|---|---|
  | seed 1, off | 6.4 / 9 | 204 | 12 / 104 |
  | seed 1, on | 5.2 / 4 | 201 | 136 / 4 |
  | seed 2, off | 50 / 0 | 282 | 12 / 33 |
  | seed 2, on | 2.7 / 3 | 225 | 0.4 / 13 |

- Reading: fewer wipe-outs in both seeds, and seed 1 is far better; seed 2 is worse for Alpha. In every run Beta fills its cap (300) and hunts Gamma. The next limit is Beta vs Gamma balance, not food or learning. Evolution is visibly working: in seed 1 Gamma's families shrank their brains to 56 neurons, raised "food" to about 1.3-1.4x and cut "avoid death" to about 0.35-0.4x.

## 35. More food, new litter sizes [Decided by the user]
- Litters: Beta 10-15 (was 15-20), Gamma 15-20 (was 5-10). Food: 700 plants regrowing in 600 steps (was 400 and 1,000), about 2.9x the food. Fix 11 is now "More food" (its off values: 400 plants, 1,700 steps). A fix now counts as on whenever its settings differ from the version 1 values, so older versions still show it on.
- Why: by arithmetic, 400 plants at 1,000 steps gave about 40% of the food a healthy mix of the three species needs.
- 500-day tests (fixes 16-19 on), before -> after:

  | Seed | Alpha avg (2nd half) / wiped | Beta avg / wiped | Gamma avg (2nd half) / wiped | Starved (all) |
  |---|---|---|---|---|
  | 1 | 5.2 (6.4) / 4 -> 24.8 (46) / 1 | 201 / 2 -> 208 / 2 | 120 (136) / 4 -> 155 (178) / 7 | 2,902 -> 1,456 |
  | 2 | 2.7 (2.3) / 3 -> 38.8 (72) / 2 | 225 / 1 -> 150 / 2 | 8 (0.4) / 13 -> 394 (488) / 1 | 934 -> 1,598 |

- Reading: all three species now live together in both seeds; Alpha grew from a handful to 46-72; starving fell sharply for Beta. Gamma now dies mostly by being hunted (Alpha 2,808-6,283 kills, Beta 1,457-1,684): a food chain appeared. Seed 2 had no wipe-outs after day 258; seed 1 still lost Gamma 6 times between days 209 and 374.

## 36. Speed: training on every core, lighter drawing [Asked by the user: "it hangs, not using my processor"]
- Measured on v2 (900 creatures, all caps full) on the Intel i9 laptop (8 cores, 16 threads): 91% of the simulation's time was brain training (`Mlp.backward`) on one core. Each family's update froze the world for up to 535 ms, and the world ran at 31 steps/s (3x normal speed at "max").
- Training now splits every minibatch into 16 fixed pieces that run on all cores (`rl/Par.kt`, per-thread `Mlp.Pass`), with gradients added in a fixed order, so a seed still gives exactly the same run on any machine. It also skips the unused input gradient. Result: 31 -> 98 steps/s, longest freeze 535 -> 100 ms.
- Drawing: a creature's detail now follows its size on screen (`Art.detailFor`): a dot below 3 px, main shapes below 7 px, everything above. A full frame on the Retina screen went from 53 to 31 ms. The window draws a frame only when the last one had time to finish (at most about two thirds of its thread), and 15 frames/s while paused and untouched, so clicks and zooming never queue up. Window thread: about 63% -> 21% of a core.

## 37. No population cap [Decided by the user: "don't set the limit for the population as population can erupt"]
- Fix 20: no total cap and no cap per species by default (`hardCap` 0, `speciesCap` 0,0,0). Food, hunting, age and
  starving are the only limits, so a population can erupt and crash.
- The creature arrays and the brains' buffers now grow when full instead of being fixed at the cap. Growing does not
  change the run (same seed, same world), and saves load at any size.
- The caps are still settings (0 = off); old versions keep theirs. Details in `ANATOMY.md` section 14b.

## 38. A colour for each brain family [Asked by the user: "different colors for a family group, differs same species with different capabilities"]
- Each brain family is drawn in its own shade of its species' colour, so groups of one species with different evolved
  brains (size, learning, motivations) can be told apart on the map. On by default; the **families** button or `C` turns it off.
- The Evolution tab on the stats page shows each family in the same colour next to its genes.
- Families only differ once the family checks have replaced a weak family with a changed copy (every 3,000 steps).
