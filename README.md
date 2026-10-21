# BossingBIS

**A planner for before the trip. It gives no help during a boss fight.** BossingBIS only works out what to wear, bring and teleport with, from your own bank, while you're banking. It has no overlays in combat and no attack, phase-timing or prayer advice. It doesn't track NPCs or tell you where to stand. That keeps it within RuneLite's and Jagex's rules for boss content.

## What it does

Pick a boss in the side panel. No Slayer task is needed. From your bank (plus potion storage, group storage and what you're wearing and carrying) it shows:

- **Your highest-DPS set for each combat style**, worked out with the OSRS Wiki DPS calculator's formulas. The set is outlined in your bank, and **Show in bank** filters your bank to it.
- **A whole-fight plan** for bosses with several monsters or phases (Dagannoth Kings, Barrows, Royal Titans, Moons of Peril, the Hueycoatl, Zulrah, Grotesque Guardians, Abyssal Sire, Alchemical Hydra, Phantom Muspah, Kalphite Queen). It picks the best set for each, wears the first, and carries the items that differ as switches. To keep the inventory free, it keeps your main set's items wherever that costs little DPS (*Fewer switches*, 10% by default), and the panel says what that gave up.
- **A 28-slot inventory**: entry items (a mith grapple for Kree'arra, a spade for Barrows), switches, antifire and anti-venom where needed, prayer potions, a potion for your best style, a spec weapon, loot tools, a teleport, and your best food (with karambwans for combo eating). When you don't have the best supply it falls back to the next one you own: moth mixes or sanfew serums for prayer, super attack and strength potions for a super combat, antipoison when there's no anti-venom, and so on.
- **Gear the boss needs**, kept in every set: boots of stone for Hydra, a Tortugan shield and 40 kg of armour for the Shellbane gryphon, a dragonfire shield for Vorkath if you have no super antifire.
- **How to get there**, ticked off when you can use the route. The route goes in the inventory too: the best one you can use, in the list's order. That's a teleport item you own, a spell you can cast (on its spellbook, with the Magic level, quest and runes; the runes go in your rune pouch), or a fairy ring (your Dramen or Lunar staff, unless you've done the elite Lumbridge Diary, plus your best way to a ring, such as an Ardougne cloak). The rune pouch line says which runes are for what: teleports, High Alchemy for alchable drops, bursting. Runes already in your pouch count as owned. An elemental staff in your planned set covers its runes, and combination runes (mist, dust, mud, smoke, steam, lava, aether) count as both of theirs; one that covers two needed runes is preferred, to save a pouch slot. Picking cheaper ammo or a spell that makes another style the best plans the inventory again for that style.
- **A rough kill time** (HP / DPS, for the whole fight when planning one).

The ammo, spell and food tiles can be clicked to pick a cheaper option. Picking cheaper ammo or a cheaper spell updates the style tabs and the bank outline. **Hide highlights**, next to Show in bank, turns the bank outlines off (say, if you stop part way), and **Feedback and suggestions** at the bottom of the panel opens the plugin's GitHub issues page. Bosses that can only be fought on a Slayer task are marked "(task only)" and get the Slayer helmet's on-task bonus; every other boss is worked out off task.

## Small and safe

- Read-only: it never equips, withdraws or moves items and adds no menu options.
- Offline: no network requests. All data is bundled.
- The search runs in the background at low priority.

## Settings

Prayers and potion boosts assumed in the maths (none by default), which storages to count, how many prayer and combat potions to bring, prayer potions or moth mixes first, switches on or off, wiki-recommended swaps (such as a weapon for a boss's minions) on or off, loot tools, spec weapon, Serpentine helm against venom, burst targets, highlight colours and the inventory view.

## Data

Boss entries live in `src/main/resources/com/bossingbis/bosses.json`: monsters, locations and teleports, entry items, required gear and tips. The format is described at the top of the file. A boss can also list items the wiki recommends for it (`prefer`), which are kept when they cost little DPS. To refresh item and monster stats from the wiki calculator:

```
python scripts/refresh_data.py
./gradlew build
```

The tests run the DPS maths against loadouts checked in the live wiki calculator. They never start the game.

## Credits

DPS formulas follow the [OSRS Wiki DPS calculator](https://tools.runescape.wiki/osrs-dps/) ([source](https://github.com/weirdgloop/osrs-dps-calc)). Item, monster, drop and weight data come from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) under CC BY-NC-SA 3.0; see `src/main/resources/com/bossingbis/DATA-NOTICE.txt`. Based on [SlayerBIS](https://github.com/iiRetroo/slayer-bis).
