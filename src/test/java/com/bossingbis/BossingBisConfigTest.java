package com.bossingbis;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BossingBisConfigTest
{
	@Test
	public void lootToolsAreOffByDefault()
	{
		// boss trips are short, so the inventory goes to supplies unless you ask for loot tools
		assertEquals(BossingBisConfig.LootTools.OFF, new BossingBisConfig()
		{
		}.lootTools());
	}
}
