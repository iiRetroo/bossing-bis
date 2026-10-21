package com.bossingbis.prep;

import com.bossingbis.data.EquipmentSlot;
import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * What to wear, carry and use for a boss trip. Suggestions only: nothing is withdrawn or equipped.
 */
@Value
public class PrepPlan
{
	/**
	 * A slot that must hold one of the listed items for this boss.
	 */
	@Value
	public static class Requirement
	{
		EquipmentSlot slot;
		List<String> accepted;
		String reason;
		/** Whether the player owns any accepted item. */
		boolean owned;
	}

	/**
	 * One line of the suggested inventory.
	 */
	@Value
	public static class InventoryLine
	{
		/** Item name, or the first preferred name when none is owned. */
		String name;
		int quantity;
		/** How many of this item the player owns across bank, inventory and worn. */
		int owned;
		String note;
		boolean required;
		/** An owned item id to show the icon of, or -1 when none is owned. */
		int itemId;
		/** All of the quantity fits in one slot. */
		boolean stackable;
	}

	@Value
	public static class FoodOption
	{
		String name;
		int heal;
		/** How many the player owns. */
		int owned;
		int itemId;
	}

	/**
	 * A worn item recommended for this boss for reasons the DPS maths can't see.
	 */
	@Value
	public static class Preference
	{
		EquipmentSlot slot;
		List<String> items;
		/** Kept when the best set without it is no more than this many percent better. */
		int withinPercent;
		String reason;
	}

	/**
	 * A gear switch carried for another phase or monster of the fight.
	 */
	@Value
	public static class Switch
	{
		String name;
		String note;
	}

	@Value
	public static class TeleportOption
	{
		String location;
		String text;
		/** null when the teleport needs no item (spell, fairy ring). */
		Boolean owned;
	}

	List<Requirement> requirements;
	List<InventoryLine> inventory;
	List<TeleportOption> teleports;
	/** Warnings: missing items and supplies. */
	List<String> notes;
	/** Tips about getting to and preparing for the boss. */
	List<String> tips;
	int slotsUsed;
	/** Bank item ids of the suggested supplies, for highlighting. */
	Set<Integer> supplyBankIds;
	/** Every food the player owns, best first, for picking a different main food. */
	List<FoodOption> foodOptions;
}
