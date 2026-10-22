package com.bossingbis.calc;

import com.bossingbis.data.AttackType;
import com.bossingbis.data.GameData;
import com.bossingbis.data.GearItem;
import com.bossingbis.data.Monster;
import com.bossingbis.data.TestMonsters;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * A worst-case bank: one of every equippable item in the game. Checks the search still finishes.
 */
public class OptimizerPerformanceTest
{
	@Test(timeout = 60_000)
	public void everyItemInTheGame() throws Exception
	{
		GameData data = GameData.load(new Gson());
		Monster target = TestMonsters.named("Abyssal demon");
		List<Optimizer.Candidate> bank = new ArrayList<>();
		for (int id = 0; id < 40_000; id++)
		{
			GearItem item = data.getItem(id);
			if (item != null && item.getId() == id)
			{
				bank.add(new Optimizer.Candidate(item, id));
			}
		}

		Optimizer optimizer = new Optimizer(DpsCalculatorTest.maxed(true), target);
		for (AttackType type : AttackType.values())
		{
			long start = System.nanoTime();
			StyleResult r = optimizer.optimise(bank, type);
			long ms = (System.nanoTime() - start) / 1_000_000;
			System.out.printf("%s: %.3f dps, %d -> %d items, %d combinations, %d ms, stopped early %s%n",
				type, r.getResult().getDps(), r.getItemsConsidered(), r.getItemsAfterPruning(),
				r.getCombinationsTried(), ms, r.isStoppedEarly());
			StringBuilder sb = new StringBuilder();
			for (Optimizer.Candidate c : r.getGear())
			{
				if (c != null)
				{
					sb.append(c.getItem().getName()).append(", ");
				}
			}
			System.out.println("  " + sb);
			assertTrue(r.isValid());
		}
	}

	@Test(timeout = 60_000)
	public void everyItemWithAMinimumWeight() throws Exception
	{
		GameData data = GameData.load(new Gson());
		Monster target = TestMonsters.named("Gryphon");
		List<Optimizer.Candidate> bank = new ArrayList<>();
		for (int id = 0; id < 40_000; id++)
		{
			GearItem item = data.getItem(id);
			if (item != null && item.getId() == id)
			{
				bank.add(new Optimizer.Candidate(item, id));
			}
		}
		Optimizer optimizer = new Optimizer(DpsCalculatorTest.maxed(true), target);
		for (AttackType type : new AttackType[]{AttackType.STAB, AttackType.RANGED, AttackType.MAGIC})
		{
			long start = System.nanoTime();
			StyleResult r = optimizer.optimise(bank, type, java.util.Collections.emptyMap(), 40000);
			System.out.printf("weighted %s: %.3f dps, %.1f kg, %d combinations, %d ms, stopped early %s%n", type,
				r.getResult().getDps(), Optimizer.weight(r.getGear()) / 1000.0, r.getCombinationsTried(),
				(System.nanoTime() - start) / 1_000_000, r.isStoppedEarly());
			assertTrue(r.isValid());
			assertTrue(Optimizer.weight(r.getGear()) >= 40000);
		}
	}
}
