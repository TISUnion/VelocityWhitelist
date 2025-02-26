package me.fallenbreath.velocitywhitelist.utils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public class UuidUtils
{
	private static String insertDashesIntoUUIDString(String uuid)
	{
		StringBuilder sb = new StringBuilder(uuid);
		sb.insert(8, "-");
		sb.insert(13, "-");
		sb.insert(18, "-");
		sb.insert(23, "-");
		return sb.toString();
	}

	public static Optional<UUID> tryParseUuid(String value)
	{
		try
		{
			return Optional.of(UUID.fromString(value));
		}
		catch (IllegalArgumentException ignored)
		{
		}

		if (value.length() == 32)
		{
			return tryParseUuid(insertDashesIntoUUIDString(value));
		}

		return Optional.empty();
	}

	public static UUID getOfflinePlayerUuid(String playerName)
	{
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
	}
}
