package me.fallenbreath.velocitywhitelist;

import com.google.common.collect.Lists;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import me.fallenbreath.velocitywhitelist.config.Configuration;
import me.fallenbreath.velocitywhitelist.config.Whitelist;
import me.fallenbreath.velocitywhitelist.utils.MojangAPI;
import me.fallenbreath.velocitywhitelist.utils.UuidUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WhitelistManager
{
	private final Logger logger;
	private final Configuration config;
	private final Path whitelistFilePath;
	private final ProxyServer server;
	private Whitelist whitelist = null;
	private final Object whitelistLock = new Object();

	public WhitelistManager(Logger logger, Configuration config, Path dataDirectory, ProxyServer server)
	{
		this.logger = logger;
		this.config = config;
		this.whitelistFilePath = dataDirectory.resolve("whitelist.yml");
		this.server = server;
	}

	public void init()
	{
		this.loadWhitelist();
	}

	public void reloadWhitelist()
	{
		this.loadWhitelist();
	}

	public boolean isPlayerInWhitelist(GameProfile profile)
	{
		synchronized (this.whitelistLock)
		{
			return switch (this.config.getIdentifyMode())
			{
				case NAME -> this.whitelist.getNames().contains(profile.getName());
				case UUID -> this.whitelist.getUuidMapping().containsKey(profile.getId());
			};
		}
	}

	private static String pretty(@NotNull UUID uuid, @Nullable String name)
	{
		return name != null ? String.format("%s (%s)", name, uuid) : uuid.toString();
	}

	public List<String> getValuesForRemovalSuggestion()
	{
		synchronized (this.whitelistLock)
		{
			switch (this.config.getIdentifyMode())
			{
				case NAME:
					return Lists.newArrayList(this.whitelist.getNames());
				case UUID:
					List<String> values = Lists.newArrayList();
					this.whitelist.getUuidMapping().keySet().forEach(uuid -> values.add(uuid.toString()));
					this.whitelist.getUuidMapping().values().forEach(name -> {
						if (name != null)
						{
							values.add(name);
						}
					});
					return values;
				default:
					throw new IllegalStateException("Unknown identify mode " + this.config.getIdentifyMode());
			}
		}
	}

	public List<String> getValuesForListing()
	{
		synchronized (this.whitelistLock)
		{
			return switch (this.config.getIdentifyMode())
			{
				case NAME -> Lists.newArrayList(this.whitelist.getNames());
				case UUID -> this.whitelist.getUuidMapping().entrySet().stream()
						.map(e -> pretty(e.getKey(), e.getValue()))
						.toList();
			};
		}
	}

	private interface NameModeHandler
	{
		boolean handle(@Nullable UUID uuid, @NotNull String playerName);
	}

	private interface UuidHandler
	{
		boolean handle(@NotNull UUID uuid, @Nullable String playerName, @NotNull String displayName);
	}

	@SuppressWarnings("EnhancedSwitchMigration")
	private boolean operatePlayer(
			CommandSource source,
			String value,
			NameModeHandler handleNameMode,
			UuidHandler handleUuidMode
	)
	{
		Optional<UUID> uuid = UuidUtils.tryParseUuid(value);
		Optional<GameProfile> profile = this.server.getPlayer(value).map(Player::getGameProfile);  // get online player by name

		if (uuid.isEmpty())
		{
			uuid = profile.map(GameProfile::getId);
		}
		if (uuid.isEmpty() && profile.isEmpty() && this.config.getIdentifyMode() != IdentifyMode.NAME)  // no need to lookup for name mode
		{
			// uuid == null && profile == null  -> input is name, player not online
			if (this.server.getConfiguration().isOnlineMode())
			{
				profile = MojangAPI.queryPlayerByName(this.logger, this.server, value)
						.map(r -> new GameProfile(r.uuid(), r.playerName(), List.of()));
			}
			else
			{
				UUID offlineUuid = UuidUtils.getOfflinePlayerUuid(value);
				profile = Optional.of(new GameProfile(offlineUuid, value, List.of()));
				source.sendPlainMessage(String.format("Inferred offline uuid from player name %s: %s", value, offlineUuid));
			}
		}
		if (uuid.isEmpty())
		{
			uuid = profile.map(GameProfile::getId);
		}
		if (profile.isEmpty())
		{
			profile = uuid.flatMap(this.server::getPlayer).map(Player::getGameProfile);
		}

		// uuid: get from value directly, or mojang api (lookup by input value)
		// profile: get from server online player, lookuped by input value (name / uuid)

		synchronized (this.whitelistLock)
		{
			switch (this.config.getIdentifyMode())
			{
				case NAME:
					if (uuid.isPresent())
					{
						source.sendPlainMessage("WARN: Trying to use UUID in NAME mode. Nothing will happen");
						return false;
					}
					return handleNameMode.handle(profile.map(GameProfile::getId).orElse(null), value);

				case UUID:
					// uuid == null, profile == null  ->  input is name, query mojang api failed
					// uuid != null, profile == null  ->  input is uuid, player not online, query mojang api failed
					// uuid == null, profile != null  ->  impossible
					// uuid != null, profile != null  ->  input is uuid, player is online; input is name, player is online or query mojang api ok
					if (uuid.isEmpty() && profile.isEmpty())
					{
						source.sendPlainMessage("WARN: Trying to use a player name in UUID mode, and the player is not valid. Nothing will happen");
						return false;
					}

					UUID playerUuid = uuid.isPresent() ? uuid.get() : profile.get().getId();
					String playerName = profile.map(GameProfile::getName).orElse(null);
					return handleUuidMode.handle(playerUuid, playerName, pretty(playerUuid, playerName));
			}
		}
		return false;
	}

	public boolean addPlayer(CommandSource source, String value)
	{
		return this.operatePlayer(
				source, value,
				(uuid, playerName) -> {
					if (this.whitelist.getNames().add(playerName))
					{
						source.sendMessage(Component.text(String.format("Added player %s to the whitelist", playerName)));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s is already in the whitelist", playerName)));
					return false;
				},
				(uuid, playerName, displayName) -> {
					Map<UUID, String> uuids = this.whitelist.getUuidMapping();
					if (uuids.containsKey(uuid))
					{
						String oldName = uuids.get(uuid);
						if (playerName != null && (oldName == null || !oldName.equals(playerName)))
						{
							uuids.put(uuid, playerName);  // set player name as a comment
							source.sendMessage(Component.text(String.format(
									"Player %s is already in the whitelist, updated player name for this uuid from %s to %s",
									displayName, oldName, playerName
							)));
						}
						else
						{
							source.sendMessage(Component.text(String.format("Player %s is already in the whitelist", displayName)));
						}
						return false;
					}

					uuids.put(uuid, playerName);
					source.sendMessage(Component.text(String.format("Added player %s to the whitelist", displayName)));
					return true;
				}
		);
	}

	public boolean removePlayer(CommandSource source, String value)
	{
		return this.operatePlayer(
				source, value,
				(uuid, playerName) -> {
					if (this.whitelist.getNames().add(playerName))
					{
						source.sendMessage(Component.text(String.format("Removed player %s from the whitelist", playerName)));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s is already in the whitelist", playerName)));
					return false;
				},
				(uuid, playerName, displayName) -> {
					if (this.whitelist.getUuidMapping().remove(uuid) != null)
					{
						source.sendMessage(Component.text(String.format("Removed player %s from the whitelist", displayName)));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s does not in the whitelist", displayName)));
					return false;
				}
		);

	}

	public void onPlayerLogin(LoginEvent event)
	{
		if (!this.config.isEnabled())
		{
			return;
		}
		if (this.whitelist == null)
		{
			return;
		}

		GameProfile profile = event.getPlayer().getGameProfile();
		boolean allowed = this.isPlayerInWhitelist(profile);
		if (!allowed)
		{
			TextComponent message = Component.text(this.config.getKickMessage());
			event.setResult(ResultedEvent.ComponentResult.denied(message));

			this.logger.info("Kicking player {} ({}) since it's not in the whitelist", profile.getName(), profile.getId());
		}
	}

	private void loadWhitelist()
	{
		try
		{
			Whitelist whitelist = new Whitelist(this.whitelistFilePath);
			if (!this.whitelistFilePath.toFile().isFile())
			{
				this.logger.info("Creating default empty whitelist file");
				whitelist.save();
			}
			whitelist.load(this.logger);

			synchronized (this.whitelistLock)
			{
				this.whitelist = whitelist;
			}
		}
		catch (IOException e)
		{
			this.logger.error("Failed to load whitelist, the plugin will not work!", e);
		}
	}

	public void saveWhitelist()
	{
		if (this.whitelist == null)
		{
			return;
		}
		try
		{
			synchronized (this.whitelistLock)
			{
				this.whitelist.save();
			}
		}
		catch (IOException e)
		{
			this.logger.error("Failed to save whitelist", e);
		}
	}
}
