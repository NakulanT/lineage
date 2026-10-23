# Creature anatomy

How a Lineage creature is built: body, senses, brain, energy, breeding, genes, and how it learns.
Every number here is taken from the code (`core/src/main/kotlin/lineage/core/`). If you change a number in the
code, change it here too.

Time: one **step** is 0.1 s at normal speed. One **day** is 100 steps (10 s). Distances are in grid **cells**.

---

## 1. The three species

| | Alpha | Beta | Gamma |
|---|---|---|---|
| Creature | Hammerhorn | Trunkling | Puffkin |
| Colour (also its shelter) | amber | violet | sky blue |
| Look | big, wide hammer head, club tail, glowing snout | long snout, big round ears, glowing tail tip | small round fluffy ball, big round ears, glowing tail tuft |
| Diet | meat, and plants when there is nothing to hunt | plants | plants |
| Speed (cells/s) | 1.8 | 2.5 | 3.3 |
| Health | 200 | 100 | 50 |
| Lifespan (days) | 120 | 80 | 50 |
| Max energy | 200 | 120 | 100 |
| Hunger (energy per step) | 0.06 | 0.05 | 0.04 |
| Bite damage | 24 | 18 | 12 |
| Bite reach (cells) | 1.8 | 1.2 | 1.0 |
| Time between bites (steps) | 12 | 12 | 15 |
| Plant food value | 0.8 x | 1.15 x | 1.0 x |
| Meat food value | 1.0 x | none | none |
| Meat left when it dies | 220 | 120 | 50 |
| Shelter radius (cells) | 4 | 3.2 | 2.2 |

Only Alpha hunts. Beta and Gamma have a bite value, but nothing in their brain lets them attack yet.

---

## 2. Senses

| Sense | Alpha | Beta | Gamma | How it works |
|---|---|---|---|---|
| Sight range (cells) | 10 | 8 | 6 | Only in front, inside a cone |
| Sight cone (full angle) | 70° (narrow) | 110° | 190° (wide) | |
| Hearing (cells) | 5 | 6 | 8 | All around, any direction |
| Smell (cells) | 6 | 6 | 6 | Meat is smelled 1.5 x farther. Ripe plants are smelled `smellScale` (1.5) x farther: 9 cells (fix 5) |
| Mating call (cells) | 40 | 26 | 16 | How far a male can find a female. Rarer species call farther |

Sight range is multiplied by the creature's vision gene.

---

## 3. The brain

### 3.1 What a brain is
- Each species has a few **brain families** (fix 15): Alpha 2, Beta 2, Gamma 3. Every creature uses its family's
  brain, and its experience trains that brain. A baby gets its mother's family. See 3.9.
- A brain is two small neural networks:
  - **Policy (decides):** 30 inputs → 32 tanh → 32 tanh → 9 outputs (one score per choice).
  - **Value (judges how good the situation is):** 30 inputs → 32 tanh → 32 tanh → 1 output.
- Size: 2,345 numbers in the policy, 2,081 in the value network, **4,426 per brain**.
- Females also have a small **mate-choice** learner per species (fix 14, see 5.2).
- Hidden layer size (32) is the setting `brainSize`, the "brain capacity" from the plan.
- **Starts random:** weights drawn from a normal distribution scaled by 1 / √(inputs), the last policy layer scaled
  down 100 x, biases zero. At the start every allowed choice is about equally likely.

### 3.2 When it thinks
Every 6 steps (0.6 s), each creature looks at its 26 senses and picks one choice. Creatures are spread out so they do
not all think in the same step. Between decisions the body keeps doing the chosen thing.

### 3.3 The 30 inputs (all between 0 and 1)

| # | Input | # | Input |
|---|---|---|---|
| 0 | energy / max energy | 13 | a predator is near (Alpha, for Beta and Gamma) |
| 1 | health / max health | 14 | how close that predator is |
| 2 | age / lifespan | 15 | it is hurt while a predator is near |
| 3 | is adult | 16 | prey is in sight or hearing (Alpha only) |
| 4 | is female | 17 | how close that prey is |
| 5 | is a baby | 18 | that prey is a Beta (1) or a Gamma (0) |
| 6 | must wait before breeding again | 19 | a mate is within calling range (adult males) |
| 7 | is inside its own shelter | 20 | how close that mate is |
| 8 | was just hurt | 21 | own shelter within 40 cells |
| 9 | a ripe plant is within smell | 22 | how close that shelter is |
| 10 | how close that plant is | 23 | how close its mother is (young only) |
| 11 | meat is within smell (Alpha) | 24 | is inside another species' shelter (losing health) |
| 12 | how close that meat is | 25 | how close the nearest foreign shelter is (within 10 cells) |
| 26 | ripe plants within twice the smell range (8 or more = 1) | 27 | time spent in its shelter this stay / longest stay |
| 28 | must stay out of its shelter for now | 29 | share of the map (8 x 8 parts) it has been to |

"How close" is 1 when it is right there and 0 at the edge of the sense range.

### 3.4 The 9 choices

| # | Choice | Allowed when | What the body does |
|---|---|---|---|
| 0 | explore | always | wander at 55% speed, turning now and then |
| 1 | eat plant | a ripe plant is smelled | walk to it, eat for 20 steps |
| 2 | eat meat | meat is smelled (Alpha) | walk to it, eat 1.2 per step |
| 3 | hunt | prey sensed, not tired (Alpha) | sprint at 2 x speed, bite when in reach. Gives up after 140 steps, then rests 120 steps |
| 4 | flee | a predator is sensed | run away at 1.1 x speed |
| 5 | shelter | own shelter within 40 cells, with room, not sent out lately | go inside and stay. Heals there unless starving |
| 6 | mate | adult male, female within calling range | walk to her and ask. She decides (see 5.2) |
| 7 | follow mother | young, mother alive | stay close to her. She shares food |
| 8 | rest | always | stand still until the next decision |

Choices that are not allowed right now are switched off (their score is ignored), so the brain only picks from
choices that make sense. The brain decides *what* to do; the body knows *how* to walk, eat and bite.

### 3.5 Reward (what the brain is trying to get more of)
Added up between two decisions:

| Event | Reward |
|---|---|
| Eats | + energy from the food / max energy (a plant is about +0.4 to +0.5, meat adds up the same way) |
| Each mating that gives babies | +5 for the mother and +5 for the father |
| Gets hurt | − health lost / max health (healing gives nothing) |
| Very hungry (energy below 25%) | −0.02 at every decision, −0.04 inside its own shelter (fix 4) |
| Reaches a part of the map (one of 8 x 8) it has never been to | +0.05 (fix 6) |
| Dies (killed, starved, inside a foreign shelter, or old age without any babies) | −3 |
| Dies of old age after having babies | 0 |

Walking, resting, spending energy and just staying alive give nothing on their own. An earlier version paid for
staying alive and charged for every bit of energy spent, and the brains learned to sit in their shelters.
Version 1 then found a second trap: a shelter healed faster than starving hurt, so Alpha and Beta sat in their
shelters at 0 energy forever. Fixes 1 to 4 close it.

### 3.6 How it learns (PPO)
- Experience is collected per brain. Each time enough finished decisions are collected, the brain learns from
  them: Alpha 512, Beta 1,024, Gamma 2,048 (fix 9: small species learn more often).
- Advantage: GAE with discount γ = 0.995 (looks about 200 decisions, 2 minutes, ahead) and λ = 0.95, normalised per batch.
- Update: 4 passes over the batch in groups of 256. Policy step size 0.0003, value step size 0.0009 (Adam).
- Clip 0.2 (the policy may not change too much at once), entropy bonus 0.03 (keeps it curious so it does not lock in early),
  gradient size limited to 0.5.
- Fix 7: if a brain's entropy falls below 0.4 (it locked into one choice), its entropy bonus is multiplied by 1.5
  after each update (up to 0.25). Once entropy is above 0.9 again it goes back down towards 0.03.
- Learning never stops: it goes on as long as the world runs, on the laptop and on the phone.

### 3.7 Optional: copying instincts first
`--pretrain N` makes the brains watch fixed instincts for N steps and copy them before acting on their own.
Default is 0, so the world starts from random brains.

### 3.8 Files
- `brains.bin`: the weights of all brains and the mate-choice learners, saved with the world.
- `training.csv`: columns `tick,species,family,phase,update,a,b,c,d,e,f,g,samples`.
  - `learn` rows (one per update): `a` average reward per decision, `b` policy loss, `c` value loss, `d` entropy
    (how undecided the brain is), `e` approximate KL (how much it changed), `f` clip fraction, `g` entropy bonus used.
  - `exam` rows: `a` exam score (reward per decision in the test world), `b` meals per creature per day.
  - `choice` rows (mate choice, every 200 asks): `a` accept rate, `b` share of babies that grow up, `c` bias,
    `d` weight on his strength, `e` weight on his health, `f` weight on his kills.
  - `copy_of` and `reset` rows: a brain family was replaced (3.9) or reset (fix 10).

### 3.9 Brain families (fix 15: brains that evolve)
- Alpha has 2 families, Beta 2, Gamma 3. At the start the creatures are split evenly. A baby joins its mother's family.
- Every 3,000 steps, a family with no members left is replaced by a copy of the family with the most members
  (if it has at least 4), with a small random change to every weight (2%). A few members of the big family move over
  so the new copy gets tried out.
- So brains are both trained (PPO, during life) and selected (families whose creatures survive and breed spread).
- **On screen each family has its own shade** of the species' colour (the **families** button in the top bar, or `C`):
  family 1 keeps the species' colour, family 2 is paler, family 3 turns the hue away from the nearest other species
  (Alpha yellower, Beta more purple, Gamma teal), and so on up to 8. The stats page, Evolution tab, shows each family
  in its colour next to its genes, so you can see which group on the map has which brain size and motivations.

### 3.10 The exam (is it really learning?)
The live reward also moves with food and crowding, so it is not a fair test on its own. Every 10,000 steps, a
frozen copy of all brains plays the same test world (same map, same creatures, seed 777) for 3,000 steps, without
learning. Its score per species is the average reward per decision. Only the brains differ between exams, so a rising
exam score means better brains. The screen shows the exam's verdict once there are 5 exams: a straight line through
the scores must rise by more than twice its uncertainty to count as improving.

### 3.11 Reset (fix 10)
If a species needs 10 rescues (it died out 10 times) within 30,000 steps, all its brains get new random weights
(at most once per 30,000 steps). Logged in `events.csv` as `brain_reset`.

---

## 4. Body and energy

- **Growing up:** body size and max health grow from 50% at birth to 100% at adulthood.
  Max health = health × health gene × growth.
- **Energy use per step:** hunger × metabolism × (0.4 + 0.6 × growth) × (1 + 0.6 × how fast it moves).
- **Metabolism (traits cost food):** 0.5 + 0.15 × speed gene + 0.10 × strength gene + 0.12 × health gene
  + 0.08 × vision gene + 0.05 × lifespan gene. A creature with every gene at 1.0 has metabolism 1.0.
- **Starving:** at 0 energy it loses 0.3 health every step.
- **Healing:** inside its own shelter +0.4% of max health per step, but only if its energy is above 0 (fix 1).
  Elsewhere +0.03% per step if energy is above half.
- **Shelter stays (fix 2):** at most 300 steps in one go, then it must leave and stay out for 200 steps.
- **Shelter room (fix 3):** an Alpha shelter holds 4, a Beta shelter 6, a Gamma shelter 8 (inside or on the way in).
- **Inside another species' shelter:** loses 0.3% of max health every step (dead in about 30 seconds; was 3%).
- **Old age:** dies when age passes lifespan × lifespan gene.
- **Moving:** turns at most 0.35 rad per step. Stays inside the map.

---

## 5. Breeding

### 5.1 Who can breed
- Adult after: Alpha 40 days, Beta 18 days, Gamma 8 days.
- A female must be adult, not a baby, have at least 60% energy, and not be waiting (cooldown).
- Female waiting time after giving birth: Alpha 40 days, Beta 16 days, Gamma 8 days.
- A male waits 60 steps after each try.

### 5.2 Female choice
**Learned (fix 14, the default):** when a male reaches her, she looks at 8 things: his health, his strength, speed
and health genes, his age, his kills, her own energy and her own age. A small learner per species (a logistic
choice) gives the chance of saying yes. It starts at about 73% yes for everyone.
- Saying no pays 0.
- Saying yes pays 0.2 plus (share of that litter that grows up − the species' usual share), worked out when the
  last baby of the litter grows up or dies.
- So she learns to prefer males whose babies survive more often than usual.

**Fixed rule (fix 14 off):** score = 0.5 × his health fraction + 0.5 × (his strength gene − 0.6, between 0 and 1).
She accepts if score + a small random amount (±0.12) is above her own pickiness (0.30 to 0.55, fixed per female).

### 5.3 Babies
- Babies from one mating: Alpha 5 to 10, Beta 15 to 20, Gamma 5 to 10 (was 30 to 50, which filled the cap in 6 days),
  scaled by the parents' fertility gene. There is no cap by default (fix 20), so a population can erupt. A version that
  sets a total cap or a species' own cap has its litters cut to fit.
- Babies per mating: Alpha 5-10, Beta 10-15, Gamma 15-20 (set by the user).
- One litter costs the mother 50% of her max energy, whatever its size (she needs at least 60% to mate). With fix 17 the cost follows the litter size (section 14).
  The father pays 10% of his max energy.
- A newborn starts with 50% energy (fix 13; was 30%), appears next to its mother, and stays a baby: Alpha 8 days,
  Beta 6, Gamma 3.
- A mother gives 16 energy (fix 13; was 12) to her young when it is below 60% and she is above 55% (at most every
  40 steps).
  She loses exactly what the baby gains.

---

## 6. Genes (evolution of the body)

Six numbers per creature, 1.0 = species average:

| Gene | Changes |
|---|---|
| speed | walking and running speed |
| strength | bite damage, and how attractive a male is |
| vision | sight range |
| health | max health |
| lifespan | how long it can live |
| fertility | litter size |

- The first creatures get 1.0 ± a little (normal, 0.05), kept between 0.6 and 1.5.
- A baby takes each gene from one parent at random, then adds a small random change (normal, 0.03), kept between
  0.5 and 1.8.
- Every gene above 1 costs extra food (see metabolism), so nothing can be best at everything.
- The brain is passed on by family, not by genes: a baby uses its mother's brain family (see 3.9).

---

## 7. The world

| Setting | Value |
|---|---|
| Map | 128 × 128 cells |
| Start | 5 Alpha, 20 Beta, 100 Gamma |
| Population cap | none (fix 20): any number can be born. Fix 12 had 900 in total and Alpha 100, Beta 300, Gamma 500; version 1 had 1,500 |
| Plants | 700, one kind only (fix 11; was 400) |
| Plant food | 50 energy (times the species' plant value) |
| Plant regrowth | from 0 to ripe in 600 steps (fix 11; was 1,700, then 1,000), in the same place. Only ripe plants can be eaten |
| Meat | left by every death. Rots after 3,000 steps |
| Shelters | circles: 1 Alpha, 5 Beta, 20 Gamma. Anyone can walk in; strangers lose health fast |
| Rescue | on: if a species dies out, 6 are added (see below) |
| Minimum population | Alpha 3, Beta 6, Gamma 12 (fix 9): checked every 600 steps, topped up from the best genes |
| Gene mutation | 0.03 |
| Restart button | a new version with the same settings, new map, new creatures, new random brain weights (tap twice) |

**How a species comes back (rescue):** the world keeps the 6 best creatures each species ever had. "Best" is
decided in this order: lived the most days, then mated the most, then killed the most. The moment a species reaches 0,
6 new adults appear at random places, alternating male and female, with full energy and health and generation 1.
Their genes are copied from those 6 best creatures (one each), with a small random change (normal, 0.03) so they are
not exact copies. They use the species' existing brain, so what the species learned is kept too. Each rescue is
written to `events.csv` as `rescue`, naming the creatures whose genes were used.
Switch it off with `--no-rescue`; then a species that dies out stays gone.

---

## 8. Versions
Every run is a **version** with its own folder (`~/LineageRuns/v2`, `v3`, ... on the laptop) holding
`settings.txt`, `note.txt`, the logs, the world and the brains. All 15 fixes can be switched on or off per version
(the "+ New version" screen, or `--fixes-off 1,4` / `--v1` on the command line). The old folder `live` is shown as v1.

| # | Fix | Off (v1) | On (default) |
|---|---|---|---|
| 1 | Shelter does not heal a starving creature | heals | no healing at 0 energy |
| 2 | Shelter stays have a time limit | none | 300 steps, then 200 out |
| 3 | Shelters have limited room | none | 4 / 6 / 8 |
| 4 | Hunger costs more inside a shelter | −0.02 | −0.04 |
| 5 | Plants are smelled farther | 6 cells | 9 cells, plus a "rich area" input |
| 6 | Reward for exploring new places | 0 | +0.05 per new part of the map |
| 7 | More curiosity when a brain locks in | fixed 0.03 | raised while entropy < 0.4 |
| 8 | Copy instincts first | off | off by default (on = 3,000 steps of copying) |
| 9 | Minimum population, small species learn faster | none, batch 2,048 | 3 / 6 / 12, batch 512 / 1,024 / 2,048 |
| 10 | Reset a brain that keeps dying out | never | 10 rescues in 30,000 steps |
| 11 | More food | 400 plants, 1,700 steps | 700 plants, 600 steps (about 2.9x the food of 400 at 1,000) |
| 12 | Lower cap, room for each species | 1,500 total | 900 total, 100 / 300 / 500 per species |
| 13 | Healthier babies | 30% energy, mother gives 12 | 50%, mother gives 16 |
| 14 | Females learn mate choice | fixed rule | learned |
| 15 | Brain families that evolve | 1 brain per species | 2 / 2 / 3 families |
| 20 | No population cap | fix 12's caps (900, 100 / 300 / 500) | no cap at all (`hardCap` 0, `speciesCap` 0,0,0) |

---

## 9. World rules (set by the user)
- **Anyone can eat anything.** Every species eats plants and meat (meat value: Alpha 1.0, Beta 0.7, Gamma 0.7) and may attack the other species ("hunt" is open to all when another species is seen or heard).
- **Meat comes only from accidents:** a body that was killed or died in a stranger's shelter leaves meat equal to the energy it still had. Starving and old age leave none.
- **Full means full:** after eating to full energy, a creature cannot eat again until its energy is below 70%.
- **Shelters send a signal** their own species picks up from 30 cells (inputs 21 and 22 use this range).
- **Shelters drift** at 0.05 cells per second (was 0.25), turn now and then, bounce off the edges and never overlap. Food under a shelter cannot be eaten.
- **No mating inside a shelter.**

## 10. Forage, hungry resting, bigger brain
- **Forage** (choice 9): walk toward the middle of the food it senses within the forage range (25 cells: ripe free plants and
  meat, closer food counts more). Once close, smell takes over and "eat plant" / "eat meat" become possible. Input 26 is now
  how much food is within the forage range. Before this, "explore" (a random walk) was the only way to look for food.
- **Resting while hungry** costs 0.02 per decision when energy is below 50%, so searching pays more than standing still.
- **Bigger brain:** 64 units per layer (was 32), 2 layers (setting "Brain layers", 1 to 4). About 6,700 numbers in the
  policy and 6,200 in the value network per brain. Brains saved by older versions (32 units, 9 choices) do not fit and
  start again from random weights.

## 11. Group attacks and seeing health
- Bites are stronger: Beta 18 (was 8), Gamma 12 (was 3). Six adult Gammas biting together bring down a healthy adult
  Alpha (200 health) in about 3 rounds, before it can kill more than two of them.
- Four more inputs (34 in all): 30 = health of the prey it senses, 31 = health of the predator it senses,
  32 = own species nearby (8 or more = 1), 33 = other species nearby. So a brain can learn that a weak or
  outnumbered animal is worth attacking, for every species.
- On screen, only ripe plants are drawn, and shelters no longer show the spreading signal ring (tap a shelter to see
  its signal range).

## 12. Looking inside a brain (interpretability)
- **One creature:** tap it, then **brain** in its panel. Shows its 34 senses (value and push), both hidden layers
  (64 squares each; orange = firing +, blue = firing -), the chance of each of the 10 choices, and the top reasons
  for the choice it made.
- **Push (feature attribution):** integrated gradients, 16 steps from "senses nothing" (all inputs 0) to the real input,
  on the log-chance of the chosen action. The pushes add up to how much more (or less) likely the choice is than with no
  senses at all.
- **A whole species:** stats, **Brains** tab. From one decision in 20 (setting `explainEvery`; gradient x input, one
  pass): the senses that matter most, a senses x choices map (orange = makes the choice more likely), and neuron health
  per layer (working, mostly off = dead, mostly maxed out) with what the most active neurons react to. Old samples fade,
  so it shows the brains as they are now.
- **Command line:** `--explain FOLDER [--ticks N]` loads a version, watches its frozen brains for N steps (nothing is
  saved) and writes `brain_report.csv` (species, choice, sense, mean push, mean strength) and `brain_neurons.csv`.
  Headless runs with `--out` write both files at the end too.

## 13. Every species has every ability
- All 10 choices are open to every species when they make sense. Alpha can now flee too.
- A **threat** (inputs 13-15 and 31) is the nearest animal of another species that is hunting or biting it, or of a
  stronger species. So a Gamma sees Alpha and Beta as threats, and an Alpha sees a group of Gammas hunting it as one.

## 14. Brains that evolve (fixes 16-19)
- **Plants no longer get locked (bug fix, all versions).** A creature that stopped halfway through eating (the brain
  chose something else after 6 steps, eating takes 20) kept its claim on the plant forever, and so did a dead one. Within
  a few hundred days nearly every plant was claimed by nobody, so 400 ripe plants stood untouched while everyone starved.
  Now a claim only counts while its owner is really going for that plant or eating it.
- **Fix 16, babies that grow up are rewarded.** Mating gives 1 (was 5). When a baby becomes adult, each parent still
  alive gets `grownUpReward / average litter size` (6 for a whole average litter). So mating and then starving no longer pays.
- **Fix 17, litters cost by size.** Each baby costs `litterCost / average litter size` of the mother's max energy; the
  litter is made smaller rather than take her below `litterReserve` (20%). A female needs 75% energy to mate (was 60%).
- **Fix 18, immigrants instead of rescues.** A species below its minimum gets newcomers at the map edge (best genes,
  best brain family), at most once every `immigrationEvery` steps (3000 = 30 days). Brains are never reset. A species
  can stay wiped out until the newcomers come, so failure is real and shows in the charts.
- **Fix 19, brain genes.** Each brain family carries genes: neurons per layer, layers, learning rate, curiosity
  (entropy bonus), look-ahead (gamma) and 7 motivations (food, mating, babies grow up, avoid hurt, avoid hunger,
  exploring, avoid death). The brain learns from the reward weighted by its motivations; the exam and live stats use the
  plain reward so scores stay comparable.
  - Every `familyCheckEvery` steps, a family with under `familyReplaceShare` (25%) of the biggest family's members (or
    none) becomes a changed copy of the biggest: weights plus a little noise, learning settings and motivations times
    about e^(±0.2), and with chance `brainGrowChance` (30%) 8 neurons more or fewer per layer (sometimes a layer).
  - Growing keeps behaviour exactly (new neurons start with 0 outgoing weights); shrinking drops the neurons with the
    weakest outgoing weights; a new layer starts as a pass-through.
  - A brain costs energy: upkeep x (1 + `brainEnergyCost` x (neurons / starting neurons - 1)). Twice the starting size
    eats 10% more, a smaller brain saves.
  - Stats page **Evolution** tab: brain size, learning rate, curiosity, plans-ahead and motivations over the run, and each
    family's genes now. `brain_genes.csv` in the version folder has one row per family per check. The creature brain view
    shows its family's genes.
- Old versions keep running as before: a settings file without these keys gets them switched off.

## 14b. No population cap (fix 20)
- `hardCap` and `speciesCap` default to 0, which means no cap: births are never cut, so a population can erupt and is
  held back only by food, hunting, age and starving.
- The creature arrays start with room for 1,024 (or twice the starting creatures) and double whenever every slot is
  taken (`World.growTo`); the brains' buffers grow with them. A grown world runs exactly like one that started that big,
  so a seed still gives the same run. A save remembers its size and loads into a world that grows to fit.
- Both caps are still settings: a version can set them again (0 = off). Switching fix 20 off gives fix 12's caps;
  switching 12 off too gives version 1's 1,500. Old versions keep the caps written in their settings file.
- Memory and step time now grow with the population (each slot is under 1 KB, plus the brains' buffers); nothing stops
  a run that outgrows the phone, so watch it there.

## 14c. Bigger comebacks (`rescueGrowth`)
- A wiped-out species comes back with `rescueGrowth` more creatures than the time before: the first comeback is the
  usual rescue (`rescueCount`, or the minimum with immigrants), then + `rescueGrowth` for every earlier wipe-out.
  With 6 and 6: 6, 12, 18, 24, ... The count of wipe-outs comes from `events.csv`, so it carries on after a reload.
- 0 (the default) keeps the old behaviour: always the same number.

## 15. Not built yet
- Social memory, food sharing beyond mother and baby, signals, killing within a species (see `PHASE_2.md`).
