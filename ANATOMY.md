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
| Bite damage | 24 | 8 | 3 |
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
| Smell (cells) | 6 | 6 | 6 | Finds ripe plants. Meat is smelled 1.5 x farther |
| Mating call (cells) | 40 | 26 | 16 | How far a male can find a female. Rarer species call farther |

Sight range is multiplied by the creature's vision gene.

---

## 3. The brain

### 3.1 What a brain is
- Each species has **one brain** (one set of weights). Every creature of that species uses it, and every creature's
  experience trains it. So "Beta learns" means the shared Beta brain gets better.
- The brain is two small neural networks:
  - **Policy (decides):** 26 inputs → 32 tanh → 32 tanh → 9 outputs (one score per choice).
  - **Value (judges how good the situation is):** 26 inputs → 32 tanh → 32 tanh → 1 output.
- Size: 2,217 numbers in the policy, 1,953 in the value network, **4,170 per species**.
- Hidden layer size (32) is the setting `brainSize`, the "brain capacity" from the plan.
- **Starts random:** weights drawn from a normal distribution scaled by 1 / √(inputs), the last policy layer scaled
  down 100 x, biases zero. At the start every allowed choice is about equally likely.

### 3.2 When it thinks
Every 6 steps (0.6 s), each creature looks at its 26 senses and picks one choice. Creatures are spread out so they do
not all think in the same step. Between decisions the body keeps doing the chosen thing.

### 3.3 The 26 inputs (all between 0 and 1)

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

"How close" is 1 when it is right there and 0 at the edge of the sense range.

### 3.4 The 9 choices

| # | Choice | Allowed when | What the body does |
|---|---|---|---|
| 0 | explore | always | wander at 55% speed, turning now and then |
| 1 | eat plant | a ripe plant is smelled | walk to it, eat for 20 steps |
| 2 | eat meat | meat is smelled (Alpha) | walk to it, eat 1.2 per step |
| 3 | hunt | prey sensed, not tired (Alpha) | sprint at 2 x speed, bite when in reach. Gives up after 140 steps, then rests 120 steps |
| 4 | flee | a predator is sensed | run away at 1.1 x speed |
| 5 | shelter | own shelter within 40 cells | go inside and stay. Heals there |
| 6 | mate | adult male, female within calling range | walk to her and ask. She decides (see 5.2) |
| 7 | follow mother | young, mother alive | stay close to her. She shares food |
| 8 | rest | always | stand still until the next decision |

Choices that are not allowed right now are switched off (their score is ignored), so the brain only picks from
choices that make sense. The brain decides *what* to do; the body knows *how* to walk, eat and bite.

### 3.5 Reward (what the brain is trying to get more of)
Added up between two decisions:

| Event | Reward |
|---|---|
| Still alive at a decision | +0.01 |
| Energy change | + gained / max energy (eating is good, spending is bad) |
| Health change | + change / max health (getting hurt is bad, healing is good) |
| Each baby born | +2 for the mother, +1 for the father |
| Dies from being killed, starving or a foreign shelter | -3 |
| Dies of old age | 0 |

### 3.6 How it learns (PPO)
- Experience is collected per species. Each time 2,048 finished decisions are collected, the brain learns from them.
- Advantage: GAE with discount γ = 0.98 and λ = 0.95, normalised per batch.
- Update: 4 passes over the batch in groups of 256. Policy step size 0.0003, value step size 0.0009 (Adam).
- Clip 0.2 (the policy may not change too much at once), entropy bonus 0.01 (keeps some curiosity),
  gradient size limited to 0.5.
- Learning never stops: it goes on as long as the world runs, on the laptop and on the phone.

### 3.7 Optional: copying instincts first
`--pretrain N` makes the brains watch fixed instincts for N steps and copy them before acting on their own.
Default is 0, so the world starts from random brains.

### 3.8 Files
- `brains.bin`: the weights of all three brains, saved with the world.
- `training.csv`: one row per learning update. For `learn` rows the columns `a` to `f` are: average reward per
  decision, policy loss, value loss, entropy (how undecided the brain is), approximate KL (how much it changed),
  clip fraction.

---

## 4. Body and energy

- **Growing up:** body size and max health grow from 50% at birth to 100% at adulthood.
  Max health = health × health gene × growth.
- **Energy use per step:** hunger × metabolism × (0.4 + 0.6 × growth) × (1 + 0.6 × how fast it moves).
- **Metabolism (traits cost food):** 0.5 + 0.15 × speed gene + 0.10 × strength gene + 0.12 × health gene
  + 0.08 × vision gene + 0.05 × lifespan gene. A creature with every gene at 1.0 has metabolism 1.0.
- **Starving:** at 0 energy it loses 0.3 health every step.
- **Healing:** inside its own shelter +0.4% of max health per step. Elsewhere +0.03% per step if energy is above half.
- **Inside another species' shelter:** loses 3% of max health every step (dead in about 3 seconds).
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
When a male reaches her, the female scores him:
score = 0.5 × his health fraction + 0.5 × (his strength gene − 0.6, between 0 and 1).
She accepts if score + a small random amount (±0.12) is above her own pickiness (0.30 to 0.55, fixed per female).
This is still a fixed rule, not learned.

### 5.3 Babies
- Litter: Alpha 1, Beta 1 to 2, Gamma 2 to 4, scaled by the parents' fertility gene.
- Each baby costs the mother 33% of her max energy. She can only have as many as her energy allows,
  so breeding depends on food. The father pays 4% of his max energy.
- A newborn starts with 30% energy, appears next to its mother, and stays a baby: Alpha 8 days, Beta 6, Gamma 3.
- A mother gives 12 energy to her young when it is below 60% and she is above 55% (at most every 40 steps).
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
- The brain is **not** passed on by genes yet: all members of a species share the same learning brain.

---

## 7. The world

| Setting | Value |
|---|---|
| Map | 128 × 128 cells |
| Start | 5 Alpha, 20 Beta, 100 Gamma |
| Population cap | 1,500 |
| Plants | 400, one kind only |
| Plant food | 50 energy (times the species' plant value) |
| Plant regrowth | from 0 to ripe in 1,700 steps, in the same place. Only ripe plants can be eaten |
| Meat | left by every death. Rots after 3,000 steps |
| Shelters | circles: 1 Alpha, 5 Beta, 20 Gamma. Anyone can walk in; strangers lose health fast |
| Rescue | on: if a species dies out, 6 are added (logged in events.csv) |
| Mutation | 0.03 |

---

## 8. Not built yet
- Females learning their choice of mate.
- Brains passed to babies with mutation (evolving brains).
- Social memory, food sharing beyond mother and baby, signals, killing within a species (see `PHASE_2.md`).
