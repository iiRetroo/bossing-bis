package com.bossingbis;

import com.bossingbis.calc.Optimizer;
import com.bossingbis.calc.PlayerSetup;
import com.bossingbis.calc.StyleResult;
import com.bossingbis.data.AttackType;
import com.bossingbis.data.EquipmentSlot;
import com.bossingbis.data.GameData;
import com.bossingbis.data.GearItem;
import com.bossingbis.data.Monster;
import com.bossingbis.data.MonsterResolver;
import com.bossingbis.prep.PrepPlan;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of the whole-fight plan: the main set against the first monster, and switches for
 * the rest. No game client involved.
 */
public class FightPlanTest
{
	private static GameData data;
	private static MonsterResolver resolver;

	@BeforeClass
	public static void load() throws Exception
	{
		data = GameData.load(new Gson());
		resolver = new MonsterResolver(data);
	}

	@Test
	public void dagannothKingsGetSwitchesForEachKing()
	{
		PlayerSetup setup = PlayerSetup.builder().attack(99).strength(99).ranged(99).magic(99).hitpoints(99)
			.blowpipeDartStrength(35).build();
		List<Optimizer.Candidate> bank = new ArrayList<>();
		Map<Integer, String> names = new HashMap<>();
		for (String n : new String[]{"Abyssal whip", "Dragon defender", "Fighter torso", "Bandos tassets",
			"Helm of Neitiznot", "Barrows gloves", "Dragon boots", "Amulet of fury", "Fire cape", "Berserker ring (i)",
			"Rune crossbow", "Adamant bolts", "Trident of the Swamp", "Dragon warhammer"})
		{
			GearItem item = data.findItem(n);
			assertNotNull(n, item);
			bank.add(new Optimizer.Candidate(item, item.getId()));
			names.put(item.getId(), item.getName());
		}
		List<Monster> kings = resolver.forBoss(List.of("Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme"));
		Monster rex = kings.get(0);
		AtomicBoolean cancel = new AtomicBoolean();
		Optimizer optimizer = new Optimizer(setup, rex, cancel);
		StyleResult main = null;
		for (AttackType t : AttackType.values())
		{
			if (t == AttackType.BURST)
			{
				continue;
			}
			StyleResult r = optimizer.optimise(bank, t);
			if (r.isValid() && (main == null || r.getResult().getDps() > main.getResult().getDps()))
			{
				main = r;
			}
		}
		assertNotNull(main);

		List<OptimizationOutcome.Phase> phases = new ArrayList<>();
		List<PrepPlan.Switch> switches = new ArrayList<>();
		Set<String> weapons = new HashSet<>();
		BossingBisPlugin.planPhases(kings, rex, main, setup, cancel, bank, Collections.emptyMap(),
			Collections.emptyList(), 0, names, phases, switches, weapons);

		for (OptimizationOutcome.Phase p : phases)
		{
			System.out.printf("%s: %s %.2f DPS, switches %s%n", p.getMonster().getName(), p.getType(), p.getDps(),
				p.getSwitches().stream().map(c -> c.getItem().getName()).collect(java.util.stream.Collectors.toList()));
		}
		assertEquals(3, phases.size());
		assertEquals(rex, phases.get(0).getMonster());
		assertTrue(phases.get(0).getSwitches().isEmpty());
		// Prime and Supreme are weak to other styles than Rex, so their weapons are switches
		assertTrue(switches.size() >= 2);
		for (OptimizationOutcome.Phase p : phases.subList(1, 3))
		{
			assertTrue(p.getMonster().getName(), p.getType() != main.getType()
				|| !p.getSwitches().isEmpty() || p.getDps() > 0);
			for (Optimizer.Candidate c : p.getSwitches())
			{
				Optimizer.Candidate mainItem = main.getGear()[c.getItem().getSlot().ordinal()];
				assertTrue(mainItem == null || mainItem.getItem().getId() != c.getItem().getId());
			}
		}
		// every switch is listed once
		assertEquals(switches.size(), new HashSet<>(switches).size());
		assertTrue(switches.stream().allMatch(s -> s.getNote().startsWith("switch for ")));
		assertTrue(weapons.size() >= 1);
		assertTrue(main.getGear()[EquipmentSlot.WEAPON.ordinal()] != null);
	}
}
