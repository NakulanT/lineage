# Phase 2: features to add after the basics work

This file is a plan only. No technology choices here.

Phase 1 is the core in `PROJECT_CONTEXT.md`: three species, males and females, maturity age, female cooldown, female choice, attack, eat, mate, shelter, infants staying near their mother, food sharing with family, and simple family memory (partner, babies, parents).

Phase 2 holds the features that could hurt the whole ecosystem if added too early. Rule for this phase: **add one feature at a time, and check how the species numbers behave after each one before adding the next.**

---

## 1. Full social memory
- What: creatures remember specific individuals beyond family, including who attacked them, who helped, and how good or bad that was.
- Why wait: Phase 1 only remembers family. Wider memory changes behavior a lot.
- Do first: family memory working and stable.
- Risk: creatures remember too many threats, get scared, hide in shelters and stop going out to eat.
- Safeguard: fear fades with time, and hunger always pushes them out.

## 2. Remembered kindness in attractiveness
- What: a female also considers whether she remembers a male as friendly or helpful, not only his health and strength.
- Why wait: depends on social memory working.
- Do first: full social memory.
- Risk: weak or broken memory makes the choice unpredictable.
- Phase 1 version: females choose by health and strength only.

## 3. Male roaming
- What: males travel farther to find shelter, food and females, and can bring food home to family.
- Why wait: it depends on several other systems (infants near the mother, food sharing, food that regrows unevenly).
- Do first: infants near mother, then food sharing with family.
- Risk: males die faster than females, so fewer males remain and females can't find partners.
- Safeguard: keep the roaming risk modest, and watch the male/female ratio.

## 4. Killing within the same species
- What: when starving with nothing else to eat, a creature can kill its own kind. Others who see it shun the killer.
- Why wait: one famine could set off a spiral of killings and revenge.
- Do first: social memory, so witnesses can remember the killer.
- Risk: killing becomes easy food, or shunning becomes so harsh that groups tear apart.
- Safeguard: only when starving, mild punishment first (avoid, no sharing, no mating), killing costs energy and risk, and meat is worth less than the creature that died.

## 5. Leveling up and passing boosts to babies
- What: creatures increase their own abilities during life, and maybe pass part of it on.
- Why wait: boosts can snowball down a family line until one line is unbeatable.
- Do first: stable evolution without it.
- Risk: runaway strong families, species blurring together.
- Safeguard: caps on every boost, and start with boosts that are not inherited.

## 6. Communication between species (signals)
- What: creatures send signals that nearby creatures can sense.
- Why wait: it is slow to appear, unpredictable, and hard to debug.
- Do first: everything else stable.
- Risk: signals mean nothing, or are used to mislead.
- Safeguard: small energy cost per signal, and consider a couple of fixed signals if nothing useful appears.

## 7. Different intelligence per species
- What: each species has a different level of brain capacity.
- Why wait: one species could simply win.
- Do first: all species equal.
- Risk: the smartest species dominates everything.
- Safeguard: bigger brains cost more food.

## 8. Mutation rate that can change
- What: how fast a species changes between generations is itself allowed to change.
- Why wait: it can drift to zero (no change) or too high (chaos).
- Phase 1 version: fixed for everyone.

## 9. Smell
- What: creatures sense meat scent and trails.
- Why wait: one more sense to balance before the basic senses work.
- Do first: vision and hearing.

## 10. Rescue rule
- What: if a species or one gender drops too low, a few creatures are added so the species doesn't die out.
- Why wait: if used too easily it hides real collapses and removes real selection.
- Safeguard: use rarely, and record every time it happens.

---

## Suggested order inside Phase 2
1. Male roaming
2. Full social memory
3. Remembered kindness in attractiveness
4. Killing within the same species
5. Leveling up (not inherited at first)
6. Smell
7. Communication
8. Different intelligence per species
9. Mutation rate that can change
10. Rescue rule, only if needed

## Open questions
- Which of these are actually wanted at all?
- Anything to add to or remove from this list?
