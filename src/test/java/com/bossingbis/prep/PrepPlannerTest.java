package com.bossingbis.prep;

import com.bossingbis.data.AttackType;
import com.bossingbis.data.EquipmentSlot;
import com.bossingbis.data.GameData;
import com.bossingbis.data.MonsterResolver;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of the boss preparation data and suggestions. No game client involved.
 */
public class PrepPlannerTest
{
	private static PrepData data;
	private static PrepPlanner planner;
	private static GameData gameData;

	@BeforeClass
	public static void load() throws Exception
	{
		data = PrepData.load(new Gson());
		planner = new PrepPlanner(data);
		gameData = GameData.load(new Gson());
	}

	private static OwnedItems owned(Object... nameQty)
	{
		Map<String, Integer> q = new HashMap<>();
		Map<String, Set<Integer>> ids = new HashMap<>();
		for (int i = 0; i < nameQty.length; i += 2)
		{
			q.put((String) nameQty[i], (Integer) nameQty[i + 1]);
			ids.put((String) nameQty[i], new HashSet<>(Collections.singletonList(1000 + i)));
		}
		return OwnedItems.of(q, ids);
	}

	private static PrepPlanner.Request.RequestBuilder request(String boss, AttackType best, OwnedItems owned)
	{
		return PrepPlanner.Request.builder().boss(boss).bestType(best).owned(owned).prayerPotions(4)
			.combatPotions(1).fillFood(true);
	}

	private static List<String> names(PrepPlan plan)
	{
		List<String> out = new ArrayList<>();
		for (PrepPlan.InventoryLine l : plan.getInventory())
		{
			out.add(l.getName());
		}
		return out;
	}

	@Test
	public void namesMatchDosesAndCharges()
	{
		assertTrue(OwnedItems.matches("Prayer potion(3)", "Prayer potion"));
		assertTrue(OwnedItems.matches("Games necklace(8)", "Games necklace"));
		assertFalse(OwnedItems.matches("Magic potion(4)", "Divine magic potion"));
		assertFalse(OwnedItems.matches("Anti-venom+(4)", "Anti-venom"));
	}

	@Test
	public void everyBossHasMonstersLocationsAndTeleports()
	{
		MonsterResolver resolver = new MonsterResolver(gameData);
		assertTrue(data.bosses().size() >= 40);
		for (Map.Entry<String, PrepData.BossInfo> e : data.bosses().entrySet())
		{
			String key = e.getKey();
			PrepData.BossInfo info = e.getValue();
			assertEquals(key + " key must be lower case", key.toLowerCase(Locale.ROOT), key);
			assertNotNull(key + " has no name", info.name);
			assertFalse(key + " has no monsters", info.getMonsters().isEmpty());
			for (String m : info.getMonsters())
			{
				assertFalse(key + ": no monster stats for " + m, resolver.forBoss(List.of(m)).isEmpty());
			}
			assertNotNull(key + " has no locations", info.locations);
			for (String location : info.locations)
			{
				List<PrepData.Teleport> teleports = data.locations().get(location);
				assertNotNull(key + " lists unknown location " + location, teleports);
				assertFalse(location + " has no teleports", teleports.isEmpty());
				for (PrepData.Teleport t : teleports)
				{
					assertFalse(location + " has a teleport without text", t.text == null || t.text.trim().isEmpty());
				}
			}
		}
	}

	@Test
	public void slayerOnlyBossesAreMarked()
	{
		for (String boss : new String[]{"cerberus", "abyssal sire", "kraken", "thermonuclear smoke devil",
			"alchemical hydra", "grotesque guardians", "araxxor", "shellbane gryphon"})
		{
			assertNotNull(boss + " should need a Slayer task", data.boss(boss).getSlayerTask());
		}
		for (String boss : new String[]{"vorkath", "zulrah", "general graardor", "corporeal beast", "nex"})
		{
			assertEquals(boss + " needs no Slayer task", null, data.boss(boss).getSlayerTask());
		}
	}

	@Test
	public void requiredAndPreferredItemNamesExistInTheItemData()
	{
		for (Map.Entry<String, PrepData.BossInfo> e : data.bosses().entrySet())
		{
			PrepData.BossInfo info = e.getValue();
			if (info.required != null)
			{
				for (List<String> names : info.required.values())
				{
					for (String n : names)
					{
						if (!n.equals("Boots of brimstone"))
						{
							assertNotNull(e.getKey() + ": unknown item " + n, gameData.findItem(n));
						}
					}
				}
			}
			if (info.prefer != null)
			{
				for (PrepData.Preference p : info.prefer)
				{
					assertNotNull(e.getKey() + ": unknown slot " + p.slot, EquipmentSlot.fromKey(p.slot));
					assertFalse(e.getKey() + ": a preference needs a reason", p.reason == null || p.reason.isEmpty());
					for (String n : p.items)
					{
						assertNotNull(e.getKey() + ": unknown item " + n, gameData.findItem(n));
					}
				}
			}
		}
	}

	/**
	 * The data must stay a planner for before the trip. Gear reasons may mention mechanics and prayer
	 * effects, as the wiki does, but never tell you what to do in the fight: no prayer instructions,
	 * timing, positioning or what's coming next.
	 */
	@Test
	public void bossDataHasNoFightHelp() throws Exception
	{
		String text;
		try (java.io.InputStream in = PrepData.class.getResourceAsStream("/com/bossingbis/bosses.json"))
		{
			text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
		}
		// the format note at the top names what's forbidden; check everything after it
		text = text.substring(text.indexOf("\"bosses\""));
		for (String banned : new String[]{
			// positioning
			"safespot", "safe spot", "stand ", "standing", "tile", "dodge",
			// prayer instructions
			"protect from", "pray against", "switch prayer", "turn on", "activate", "piety", "rigour", "augury",
			// timing and what's coming
			"flinch", "next attack", "when it", "when he", "when she", "when the", "as soon as", "swap to"})
		{
			assertFalse("boss data mentions \"" + banned + "\"", text.contains(banned));
		}
	}

	@Test
	public void entryItemsTeleportAndFood()
	{
		OwnedItems bank = owned("Mith grapple", 1, "Prayer potion(4)", 10, "Ranging potion(4)", 2, "Shark", 100);
		PrepPlan plan = planner.plan(request("kree'arra", AttackType.RANGED, bank).build());
		assertEquals("Mith grapple", plan.getInventory().get(0).getName());
		assertTrue(names(plan).contains("Ranging potion(4)") || names(plan).contains("Ranging potion"));
		assertEquals(PrepPlanner.INVENTORY_SLOTS, plan.getSlotsUsed());
		assertTrue(plan.getTeleports().stream().allMatch(t -> t.getLocation().equals("God Wars Dungeon")));
	}

	@Test
	public void crossbowForTheGrappleUnlessOneIsWorn()
	{
		OwnedItems bank = owned("Mith grapple", 1, "Rune crossbow", 1, "Armadyl crossbow", 1, "Shark", 100);
		PrepPlan carried = planner.plan(request("kree'arra", AttackType.RANGED, bank).build());
		assertTrue(names(carried).contains("Rune crossbow"));
		// wearing any crossbow in the planned set: no extra one
		PrepPlan worn = planner.plan(request("kree'arra", AttackType.RANGED, bank)
			.wornNames(Set.of("Armadyl crossbow")).build());
		assertTrue(names(worn).stream().noneMatch(n -> n.endsWith("crossbow")));
		assertTrue(names(worn).contains("Mith grapple"));
	}

	@Test
	public void godWarsBossesGetTheFollowersTip()
	{
		for (String boss : new String[]{"general graardor", "commander zilyana", "k'ril tsutsaroth", "kree'arra", "nex"})
		{
			assertTrue(boss, data.boss(boss).tips.stream().anyMatch(t -> t.contains("followers")));
		}
		assertFalse(planner.preferences("vorkath").isEmpty());
		assertEquals(EquipmentSlot.RING, planner.preferences("zulrah").get(0).getSlot());
	}

	@Test
	public void batchTwoReviewData()
	{
		// a dragonfire-protecting shield for the KBD, a recoil ring for the Kalphite Queen, Aranea boots for Sarachnis
		assertEquals(EquipmentSlot.SHIELD, planner.preferences("king black dragon").get(0).getSlot());
		assertEquals(EquipmentSlot.RING, planner.preferences("kalphite queen").get(0).getSlot());
		assertEquals(EquipmentSlot.FEET, planner.preferences("sarachnis").get(0).getSlot());
		assertTrue(planner.poisons("dagannoth kings"));
		assertTrue(planner.poisons("kalphite queen"));
		// optional entry items only show when owned
		PrepPlan without = planner.plan(request("dagannoth kings", AttackType.RANGED, owned("Shark", 50)).build());
		assertFalse(names(without).contains("Rune thrownaxe"));
		PrepPlan with = planner.plan(request("dagannoth kings", AttackType.RANGED,
			owned("Rune thrownaxe", 1, "Pet rock", 1, "Shark", 50)).build());
		assertTrue(names(with).containsAll(List.of("Rune thrownaxe", "Pet rock")));
	}

	@Test
	public void mechanicReasonsFromTheWiki()
	{
		// Spectral spirit shield for its prayer-drain reduction
		assertEquals("Spectral spirit shield", planner.preferences("k'ril tsutsaroth").get(0).getItems().get(0));
		assertEquals("Spectral spirit shield", planner.preferences("nex").get(0).getItems().get(0));
		// Ghommal's hilt for the Barrows tunnels, best tier owned
		PrepPlan barrows = planner.plan(request("barrows brothers", AttackType.MAGIC,
			owned("Spade", 1, "Ghommal's hilt 2", 1, "Ghommal's hilt 4", 1, "Shark", 50)).build());
		assertTrue(names(barrows).contains("Ghommal's hilt 4"));
		assertFalse(names(barrows).contains("Ghommal's hilt 2"));
		// Corp: the stat-draining special attack weapons, alongside the usual spec weapon
		PrepPlan corp = planner.plan(request("corporeal beast", AttackType.STAB,
			owned("Arclight", 1, "Bandos godsword", 1, "Dragon warhammer", 1, "Shark", 50))
			.monsterHitpoints(2000).suggestSpecWeapon(true).build());
		assertTrue(names(corp).containsAll(List.of("Arclight", "Bandos godsword", "Dragon warhammer")));
		// more ways to Slepe
		assertTrue(planner.plan(request("the nightmare", AttackType.SLASH, owned("Ectophial", 1)).build())
			.getTeleports().stream().anyMatch(t -> t.getText().startsWith("Ectophial") && Boolean.TRUE.equals(t.getOwned())));
	}

	@Test
	public void wikiSwitchesAreOwnedOnlyAndCanBeTurnedOff()
	{
		OwnedItems bank = owned("Hallowfell", 1, "Blisterwood stake", 1, "Shark", 50);
		PrepPlan on = planner.plan(request("phosani's nightmare", AttackType.CRUSH, bank).build());
		assertTrue(names(on).containsAll(List.of("Hallowfell", "Blisterwood stake")));
		// Dinh's bulwark isn't owned, so it isn't suggested
		assertFalse(names(on).contains("Dinh's bulwark"));
		assertTrue(on.getInventory().stream().filter(l -> l.getName().equals("Hallowfell"))
			.allMatch(l -> l.getNote().startsWith("wiki switch: ")));
		PrepPlan off = planner.plan(request("phosani's nightmare", AttackType.CRUSH, bank).wikiSwitches(false).build());
		assertFalse(names(off).contains("Hallowfell"));
		// an item already worn in the set isn't carried as a switch too
		PrepPlan worn = planner.plan(request("phosani's nightmare", AttackType.CRUSH, bank)
			.wornNames(Set.of("Hallowfell")).build());
		assertFalse(names(worn).contains("Hallowfell"));
	}

	@Test
	public void awakenersOrbOnlyForAwakenedFights()
	{
		OwnedItems bank = owned("Awakener's orb", 2, "Pestle and mortar", 1, "Shark", 50);
		PrepPlan normal = planner.plan(request("duke sucellus", AttackType.CRUSH, bank)
			.monsterVersion("Post-quest, Awake").build());
		assertFalse(names(normal).contains("Awakener's orb"));
		assertTrue(names(normal).contains("Pestle and mortar"));
		PrepPlan awakened = planner.plan(request("duke sucellus", AttackType.CRUSH, bank)
			.monsterVersion("Awakened, Awake").build());
		assertTrue(names(awakened).contains("Awakener's orb"));
		// the Whisperer can't be started without a blackstone fragment
		PrepPlan whisperer = planner.plan(request("the whisperer", AttackType.MAGIC, owned()).build());
		assertTrue(whisperer.getNotes().stream().anyMatch(n -> n.contains("Blackstone fragment")));
	}

	@Test
	public void batchFiveReviewData()
	{
		// a gas mask also protects against the smoke
		assertTrue(planner.requirements("thermonuclear smoke devil", owned(), Set.of("Gas mask")).get(0).isOwned());
		// Kraken: the Sanguinesti staff is preferred, fishing explosives only when owned
		assertEquals(EquipmentSlot.WEAPON, planner.preferences("kraken").get(0).getSlot());
		PrepPlan kraken = planner.plan(request("kraken", AttackType.MAGIC, owned("Fishing explosive", 30, "Shark", 50)).build());
		assertTrue(names(kraken).contains("Fishing explosive"));
		// Cerberus: the key master teleport scroll stacks in one slot
		PrepPlan cerberus = planner.plan(request("cerberus", AttackType.SLASH,
			owned("Key master teleport", 5, "Spectral spirit shield", 1, "Shark", 50)).build());
		assertTrue(cerberus.getInventory().stream()
			.anyMatch(l -> l.getName().equals("Key master teleport") && l.isStackable()));
		assertTrue(names(cerberus).contains("Spectral spirit shield"));
		// Grotesque Guardians: no nose peg when a Slayer helmet is worn
		PrepPlan guardians = planner.plan(request("grotesque guardians", AttackType.CRUSH,
			owned("Nose peg", 1, "Rock hammer", 1, "Shark", 50)).wornNames(Set.of("Slayer helmet (i)")).build());
		assertFalse(names(guardians).contains("Nose peg"));
	}

	@Test
	public void batchSixReviewData()
	{
		// a looting bag for the Wilderness bosses, when owned
		PrepPlan scorpia = planner.plan(request("scorpia", AttackType.MAGIC, owned("Looting bag", 1, "Shark", 50)).build());
		assertTrue(names(scorpia).contains("Looting bag"));
		// stackable switches bring the whole stack in one slot
		PrepPlan venenatis = planner.plan(request("venenatis", AttackType.RANGED,
			owned("Black chinchompa", 200, "Shark", 50)).build());
		assertTrue(venenatis.getInventory().stream()
			.anyMatch(l -> l.getName().equals("Black chinchompa") && l.isStackable() && l.getQuantity() == 200));
		// two or more Ghorrock teleports stack in one slot for the Chaos Fanatic
		PrepPlan fanatic = planner.plan(request("chaos fanatic", AttackType.MAGIC,
			owned("Ghorrock teleport", 3, "Shark", 50)).build());
		assertTrue(fanatic.getInventory().stream()
			.anyMatch(l -> l.getName().equals("Ghorrock teleport") && l.isStackable() && l.getQuantity() == 3));
		// the Deranged archaeologist's path needs a dose of antipoison
		PrepPlan deranged = planner.plan(request("deranged archaeologist", AttackType.MAGIC, owned("Shark", 50)).build());
		assertTrue(deranged.getNotes().stream().anyMatch(n -> n.contains("Antidote++")));
		// Wilderness sword 4 reaches Callisto
		assertTrue(planner.plan(request("callisto", AttackType.RANGED, owned("Wilderness sword 4", 1)).build())
			.getTeleports().stream().anyMatch(t -> t.getText().startsWith("Wilderness sword 4")
				&& Boolean.TRUE.equals(t.getOwned())));
	}

	@Test
	public void batchSevenReviewData()
	{
		// Obor's chest uses up a giant key every kill
		PrepPlan obor = planner.plan(request("obor", AttackType.SLASH, owned("Shark", 50)).build());
		assertTrue(obor.getNotes().stream().anyMatch(n -> n.contains("Giant key")));
		assertTrue(planner.poisons("bryophyta"));
		// Skotizo: an Arclight for the altars, a Xeric's talisman route
		PrepPlan skotizo = planner.plan(request("skotizo", AttackType.SLASH,
			owned("Dark totem", 1, "Arclight", 1, "Xeric's talisman", 1, "Shark", 50)).build());
		assertTrue(names(skotizo).contains("Arclight"));
		assertTrue(skotizo.getTeleports().stream().anyMatch(t -> t.getText().startsWith("Xeric's talisman")
			&& Boolean.TRUE.equals(t.getOwned())));
	}

	@Test
	public void doomHasALarvaSwapForEachStyleAndNoDrainSpec()
	{
		PrepPlan doom = planner.plan(request("doom of mokhaiotl", AttackType.RANGED,
			owned("Scorching bow", 1, "Arclight", 1, "Slayer's staff", 1, "Dragon warhammer", 1, "Shark", 50))
			.monsterHitpoints(2000).suggestSpecWeapon(true).build());
		assertTrue(names(doom).containsAll(List.of("Scorching bow", "Arclight", "Slayer's staff")));
		assertFalse(names(doom).contains("Dragon warhammer"));
	}

	@Test
	public void meleePotionFallbacks()
	{
		// a super combat potion first
		assertTrue(names(planner.plan(request("general graardor", AttackType.CRUSH,
			owned("Super combat potion(4)", 2, "Super attack(4)", 2, "Super strength(4)", 2)).build()))
			.contains("Super combat potion"));
		// super attack and strength beat a plain combat potion
		List<String> pair = names(planner.plan(request("general graardor", AttackType.CRUSH,
			owned("Combat potion(4)", 2, "Super attack(4)", 2, "Super strength(4)", 2)).build()));
		assertTrue(pair.containsAll(List.of("Super attack", "Super strength")));
		assertFalse(pair.contains("Combat potion"));
		// a missing super potion is made up with the plain one for that stat
		List<String> mixed = names(planner.plan(request("general graardor", AttackType.CRUSH,
			owned("Super attack(4)", 2, "Strength potion(4)", 2)).build()));
		assertTrue(mixed.containsAll(List.of("Super attack", "Strength potion")));
		// then a combat potion, then attack and strength potions
		assertTrue(names(planner.plan(request("general graardor", AttackType.CRUSH,
			owned("Combat potion(4)", 2, "Attack potion(4)", 2)).build())).contains("Combat potion"));
		List<String> plain = names(planner.plan(request("general graardor", AttackType.CRUSH,
			owned("Attack potion(4)", 2, "Strength potion(4)", 2)).build()));
		assertTrue(plain.containsAll(List.of("Attack potion", "Strength potion")));
		assertTrue(planner.plan(request("general graardor", AttackType.CRUSH, owned()).build())
			.getNotes().contains("No melee boosting potion found."));
	}

	@Test
	public void supplyFallbacks()
	{
		// sanfew serums restore prayer when there are no prayer potions or super restores
		assertTrue(names(planner.plan(request("giant mole", AttackType.SLASH, owned("Sanfew serum(4)", 6)).build()))
			.contains("Sanfew serum"));
		// moth mixes make up for missing prayer potions, unless the setting leaves them out, which a note says
		assertTrue(names(planner.plan(request("giant mole", AttackType.SLASH, owned("Moonlight moth mix (2)", 10))
			.build())).contains("Moonlight moth mix"));
		PrepPlan potionsOnly = planner.plan(request("giant mole", AttackType.SLASH, owned("Moonlight moth mix (2)", 10))
			.prayerRestore(PrayerRestore.POTIONS_ONLY).build());
		assertTrue(potionsOnly.getNotes().stream().anyMatch(n -> n.contains("You have moonlight moth mixes")));
		// an antipoison when there's no anti-venom, with a note that it doesn't stop venom
		PrepPlan antipoison = planner.plan(request("zulrah", AttackType.MAGIC, owned("Superantipoison(4)", 1)).build());
		assertTrue(antipoison.getInventory().stream().anyMatch(l -> l.getName().equals("Superantipoison")
			&& l.getNote().contains("not venom")));
		// an antifire mix counts as antifire
		assertTrue(names(planner.plan(request("vorkath", AttackType.RANGED, owned("Super antifire mix(2)", 2)).build()))
			.contains("Super antifire mix"));
		// forgotten brew for magic when there's no magic potion
		assertTrue(names(planner.plan(request("zulrah", AttackType.MAGIC, owned("Forgotten brew(4)", 1)).build()))
			.contains("Forgotten brew"));
	}

	@Test
	public void bossesAddedFromTheWikisBossList()
	{
		for (String boss : new String[]{"royal titans", "amoxliatl", "the hueycoatl", "yama", "doom of mokhaiotl",
			"moons of peril", "sol heredit", "gemstone crab", "hespori", "the mimic", "revenant maledictus", "brutus",
			"demonic brutus", "mad angel", "maggot king"})
		{
			assertNotNull(boss, data.boss(boss));
		}
		// the Royal Titans are fought together, and each counts for its own giant Slayer task
		assertTrue(data.boss("royal titans").isWholeFight());
		assertEquals(List.of("Branda the Fire Queen", "Eldric the Ice King"), data.boss("royal titans").getMonsters());
		PrepPlan titans = planner.plan(request("royal titans", AttackType.MAGIC, owned("Giantsoul amulet", 1)).build());
		assertTrue(titans.getTips().stream().anyMatch(t -> t.contains("fire giant")));
		assertTrue(names(titans).contains("Giantsoul amulet"));
		// Demonic Brutus can't be started without an abyssal potato
		assertTrue(planner.plan(request("demonic brutus", AttackType.SLASH, owned()).build())
			.getNotes().stream().anyMatch(n -> n.contains("Abyssal potato")));
	}

	@Test
	public void newBossesReviewData()
	{
		assertEquals(EquipmentSlot.RING, planner.preferences("doom of mokhaiotl").get(0).getSlot());
		assertEquals(EquipmentSlot.HANDS, planner.preferences("maggot king").get(0).getSlot());
		assertTrue(planner.poisons("maggot king"));
		// the Hueycoatl's tail swaps, when owned
		PrepPlan huey = planner.plan(request("the hueycoatl", AttackType.CRUSH,
			owned("Dual macuahuitl", 1, "Blood moon helm", 1, "Shark", 50)).build());
		assertTrue(names(huey).containsAll(List.of("Dual macuahuitl", "Blood moon helm")));
		// the Mimic needs its casket, and Watson's house is the way there
		PrepPlan mimic = planner.plan(request("the mimic", AttackType.MAGIC, owned("Watson teleport", 3)).build());
		assertTrue(mimic.getNotes().stream().anyMatch(n -> n.contains("Mimic")));
		assertTrue(names(mimic).contains("Watson teleport"));
		// Hespori: knives or darts for the flowers, the whole stack in one slot
		PrepPlan hespori = planner.plan(request("hespori", AttackType.SLASH,
			owned("Spade", 1, "Rune knife", 100, "Shark", 50)).build());
		assertTrue(hespori.getInventory().stream()
			.anyMatch(l -> l.getName().equals("Rune knife") && l.isStackable() && l.getQuantity() == 100));
	}

	@Test
	public void infernoEntryAndRoutes()
	{
		PrepPlan zuk = planner.plan(request("tzkal-zuk", AttackType.RANGED, owned("Achievement diary cape", 1)).build());
		assertTrue(zuk.getTips().stream().anyMatch(t -> t.contains("fire cape")));
		// a worn teleport is a tip, not an inventory slot
		assertTrue(zuk.getTips().stream().anyMatch(t -> t.startsWith("Wear your Achievement diary cape")));
		assertFalse(names(zuk).contains("Achievement diary cape"));
	}

	@Test
	public void inertTalismanIsNotATeleport()
	{
		PrepPlan inert = planner.plan(request("sarachnis", AttackType.SLASH, owned("Xeric's talisman (inert)", 1)).build());
		assertTrue(inert.getTeleports().stream().filter(t -> t.getText().startsWith("Xeric's talisman"))
			.allMatch(t -> Boolean.FALSE.equals(t.getOwned())));
		PrepPlan charged = planner.plan(request("sarachnis", AttackType.SLASH, owned("Xeric's talisman", 1)).build());
		assertTrue(names(charged).contains("Xeric's talisman"));
	}

	@Test
	public void missingEntryItemIsFlagged()
	{
		PrepPlan plan = planner.plan(request("kree'arra", AttackType.RANGED, owned()).build());
		assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("Mith grapple")));
	}

	@Test
	public void requiredGearForBosses()
	{
		List<PrepPlan.Requirement> hydra = planner.requirements("alchemical hydra", owned(), Collections.emptySet());
		assertEquals(EquipmentSlot.FEET, hydra.get(0).getSlot());
		List<PrepPlan.Requirement> shellbane = planner.requirements("shellbane gryphon", owned(), Collections.emptySet());
		assertTrue(shellbane.stream().anyMatch(r -> r.getAccepted().contains("Tortugan shield")));
		assertEquals(40000, planner.minWeight("shellbane gryphon"));
		// Vorkath: a dragonfire shield unless you have a super antifire
		assertEquals(1, planner.requirements("vorkath", owned(), Collections.emptySet()).size());
		assertEquals(0, planner.requirements("vorkath", owned("Extended super antifire(4)", 2),
			Collections.emptySet()).size());
	}

	@Test
	public void antifireAntivenomAndKarambwans()
	{
		OwnedItems bank = owned("Extended super antifire(4)", 3, "Anti-venom+(4)", 2, "Cooked karambwan", 20,
			"Shark", 100);
		PrepPlan plan = planner.plan(request("vorkath", AttackType.RANGED, bank).build());
		List<String> names = names(plan);
		assertTrue(names.contains("Extended super antifire(4)") || names.contains("Extended super antifire"));
		assertTrue(names.stream().anyMatch(n -> n.startsWith("Anti-venom")));
		assertTrue(names.contains("Cooked karambwan"));
		// a charged Serpentine helm in the set makes anti-venom unnecessary
		PrepPlan immune = planner.plan(request("vorkath", AttackType.RANGED, bank).venomImmune(true).build());
		assertTrue(names(immune).stream().noneMatch(n -> n.startsWith("Anti-venom")));
	}

	@Test
	public void switchesAreCarriedBeforeSupplies()
	{
		OwnedItems bank = owned("Toxic blowpipe", 1, "Trident of the swamp", 1, "Prayer potion(4)", 10, "Shark", 100);
		PrepPlan plan = planner.plan(request("zulrah", AttackType.MAGIC, bank)
			.switches(List.of(new PrepPlan.Switch("Toxic blowpipe", "switch for Zulrah (Magma)"))).build());
		assertEquals("Toxic blowpipe", plan.getInventory().get(0).getName());
		assertEquals(PrepPlanner.INVENTORY_SLOTS, plan.getSlotsUsed());
	}

	@Test
	public void specWeaponSkipsPlannedWeapons()
	{
		// a plain hammer for the door, so the Dragon warhammer isn't the entry item
		OwnedItems bank = owned("Hammer", 1, "Dragon warhammer", 1, "Bandos godsword", 1, "Shark", 100);
		PrepPlan plan = planner.plan(request("general graardor", AttackType.CRUSH, bank).monsterHitpoints(255)
			.suggestSpecWeapon(true).plannedWeapons(Set.of("Dragon warhammer")).build());
		assertTrue(names(plan).contains("Bandos godsword"));
		assertFalse(names(plan).contains("Dragon warhammer"));
	}

	@Test
	public void wornTeleportsAreATipNotASlot()
	{
		PrepPlan plan = planner.plan(request("general graardor", AttackType.CRUSH,
			owned("Ghommal's hilt 4", 1, "Hammer", 1)).build());
		assertTrue(plan.getTips().stream().anyMatch(t -> t.contains("Ghommal's hilt")));
	}

	@Test
	public void karuulmBootsApplyToHydra()
	{
		PrepPlan plan = planner.plan(request("alchemical hydra", AttackType.RANGED, owned("Shark", 10)).build());
		assertFalse(plan.getTips().stream().anyMatch(t -> t.startsWith("If you go to")));
	}

	@Test
	public void pickedFoodComesFirst()
	{
		OwnedItems bank = owned("Shark", 100, "Anglerfish", 100);
		PrepPlan plan = planner.plan(request("giant mole", AttackType.SLASH, bank).prayerPotions(0)
			.preferredFood("Shark").build());
		assertTrue(names(plan).contains("Shark"));
		assertFalse(names(plan).contains("Anglerfish"));
		assertEquals(2, plan.getFoodOptions().size());
	}
	@Test
	public void bestUsableTeleportGoesInTheInventory()
	{
		// Skotizo: Kourend Castle Teleport is the Catacombs' first route; its runes go in the rune pouch
		OwnedItems bank = owned("Dark totem", 1, "Divine rune pouch", 1, "Law rune", 100, "Fire rune", 100,
			"Water rune", 100, "Xeric's talisman", 1);
		PrepPlanner.Request.RequestBuilder skotizo = request("skotizo", AttackType.SLASH, bank).spellbook("standard")
			.magicLevel(99);
		PrepPlan cast = planner.plan(skotizo.finishedQuests(Set.of("CLIENT_OF_KOUREND")).build());
		assertTrue(cast.getInventory().stream().anyMatch(l -> l.getName().equals("Divine rune pouch")
			&& l.getNote().contains("teleport to Catacombs of Kourend")));
		assertFalse(names(cast).contains("Xeric's talisman"));
		// too low a Magic level: the next route
		assertTrue(names(planner.plan(skotizo.magicLevel(40).build())).contains("Xeric's talisman"));
		// a fairy ring route: the staff and the best way to a ring
		PrepPlan queen = planner.plan(request("kalphite queen", AttackType.STAB,
			owned("Lunar staff", 1, "Amulet of glory(4)", 1, "Shark", 50)).build());
		assertTrue(names(queen).containsAll(List.of("Lunar staff", "Amulet of glory")));
	}
	@Test
	public void elementalStavesAndCombinationRunes()
	{
		// Skotizo: Kourend Castle Teleport needs fire and water; steam runes count as both
		PrepPlanner.Request.RequestBuilder skotizo = request("skotizo", AttackType.SLASH,
			owned("Dark totem", 1, "Rune pouch", 1, "Law rune", 100, "Steam rune", 100)).spellbook("standard")
			.magicLevel(99).finishedQuests(Set.of("CLIENT_OF_KOUREND"));
		assertTrue(planner.plan(skotizo.build()).getInventory().stream()
			.anyMatch(l -> l.getName().equals("Rune pouch") && l.getNote().contains("steam")));
		// a worn Twinflame staff gives fire and water, so laws alone do
		PrepPlan staff = planner.plan(request("skotizo", AttackType.MAGIC, owned("Dark totem", 1, "Rune pouch", 1,
			"Law rune", 100)).spellbook("standard").magicLevel(99).finishedQuests(Set.of("CLIENT_OF_KOUREND"))
			.wornNames(Set.of("Twinflame staff")).build());
		assertTrue(staff.getInventory().stream().anyMatch(l -> l.getName().equals("Rune pouch")
			&& l.getNote().contains("your staff gives the fire and water")));
	}
}
