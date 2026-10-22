package com.bossingbis.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The combat styles the optimiser searches. Each one is optimised separately.
 */
@Getter
@RequiredArgsConstructor
public enum AttackType
{
	STAB("Stab"),
	SLASH("Slash"),
	CRUSH("Crush"),
	RANGED("Ranged"),
	MAGIC("Magic");

	private final String displayName;

	public boolean isMelee()
	{
		return this == STAB || this == SLASH || this == CRUSH;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
