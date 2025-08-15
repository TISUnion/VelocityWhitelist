package me.fallenbreath.velocitywhitelist;

import com.google.common.collect.Lists;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import me.fallenbreath.velocitywhitelist.config.Configuration;
import me.fallenbreath.velocitywhitelist.config.PlayerList;
import me.fallenbreath.velocitywhitelist.utils.MojangAPI;
import me.fallenbreath.velocitywhitelist.utils.UuidUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WhitelistManager
{
	private final Logger logger;
	private final Configuration config;
	private final ProxyServer server;
	private final PlayerList whitelist;
	private final PlayerList blacklist;

	public WhitelistManager(Logger logger, Configuration config, Path dataDirectory, ProxyServer server)
	{
		this.logger = logger;
		this.config = config;
		this.whitelist = new PlayerList("Whitelist", dataDirectory.resolve("whitelist.yml"));
		this.blacklist = new PlayerList("Blacklist", dataDirectory.resolve("blacklist.yml"));
		this.server = server;
	}

	public PlayerList getWhitelist()
	{
		return this.whitelist;
	}

	public PlayerList getBlacklist()
	{
		return this.blacklist;
	}

	public void loadLists()
	{
		this.loadOneList(this.whitelist);
		this.loadOneList(this.blacklist);
	}

	public void enableList(PlayerList list, boolean enabled)
	{
		list.setEnabled(enabled);
		saveList(list);
	}

	private boolean isPlayerInList(GameProfile profile, PlayerList list)
	{
		return switch (this.config.getIdentifyMode())
		{
			case NAME -> list.checkPlayerName(profile.getName());
			case UUID -> list.checkPlayerUUID(profile.getId());
		};
	}

	public boolean isPlayerInWhitelist(GameProfile profile)
	{
		return this.isPlayerInList(profile, this.whitelist);
	}

	public boolean isPlayerInBlacklist(GameProfile profile)
	{
		return this.isPlayerInList(profile, this.blacklist);
	}

	private static String pretty(@NotNull UUID uuid, @Nullable String name)
	{
		return name != null ? String.format("%s (%s)", name, uuid) : uuid.toString();
	}

	public List<String> getValuesForRemovalSuggestion(PlayerList list)
	{
		switch (this.config.getIdentifyMode())
		{
			case NAME:
				return list.getPlayerNames();
			case UUID:
				List<String> values = Lists.newArrayList();
				var entries = list.getPlayerUuidMappingEntries();
				entries.forEach(e -> values.add(e.getKey().toString()));
				entries.forEach(e -> {
					var name = e.getValue();
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

	public List<String> getValuesForListing(PlayerList list)
	{
		return switch (this.config.getIdentifyMode())
		{
			case NAME -> list.getPlayerNames();
			case UUID -> list.getPlayerUuidMappingEntries().stream()
					.map(e -> pretty(e.getKey(), e.getValue()))
					.toList();
		};
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
		final Optional<UUID> inputUuid = UuidUtils.tryParseUuid(value);

		Optional<UUID> uuid = inputUuid;
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

		switch (this.config.getIdentifyMode())
		{
			case NAME:
				if (inputUuid.isPresent())
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
		return false;
	}

	public boolean addPlayer(CommandSource source, PlayerList list, String value)
	{
		boolean isBlacklist = list == this.getBlacklist();
		return this.operatePlayer(
				source, value,
				(uuid, playerName) -> {
					if (isBlacklist)
					{
						this.server.getPlayer(playerName).ifPresent(this::handlePlayerAddedToBlacklist);
					}

					if (list.addPlayerName(playerName))
					{
						source.sendMessage(Component.text(String.format("Added player %s to the %s", playerName, list.getName())));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s is already in the %s", playerName, list.getName())));
					return false;
				},
				(uuid, playerName, displayName) -> {
					if (isBlacklist)
					{
						this.server.getPlayer(uuid).ifPresent(this::handlePlayerAddedToBlacklist);
					}

					return list.computePlayerUUID(uuid, (exists, oldName) -> {
						var result = new PlayerList.PlayerUUIDComputeResult<Boolean>();
						if (exists)
						{
							result.ret = false;
							if (playerName != null && (oldName == null || !oldName.equals(playerName)))
							{
								// set player name as a comment
								result.addNewValue = true;
								result.newValue = playerName;
								source.sendMessage(Component.text(String.format(
										"Player %s is already in the %s, updated player name for this uuid from %s to %s",
										displayName, list.getName(), oldName, playerName
								)));
							}
							else
							{
								// don't modify
								result.addNewValue = false;
								source.sendMessage(Component.text(String.format("Player %s is already in the %s", displayName, list.getName())));
							}
						}
						else  // not exists
						{
							result.addNewValue = true;
							result.newValue = playerName;
							result.ret = true;
							source.sendMessage(Component.text(String.format("Added player %s to the %s", displayName, list.getName())));
						}
						return result;
					});
				}
		);
	}

	private void handlePlayerAddedToBlacklist(Player player)
	{
		var profile = player.getGameProfile();
		this.logger.info("Kicking player {} ({}) since it's being added to the blacklist", profile.getName(), profile.getId());
		Component message = MiniMessage.miniMessage().deserialize(this.config.getBlacklistKickMessage());
		player.disconnect(message);
	}

	public boolean removePlayer(CommandSource source, PlayerList list, String value)
	{
		return this.operatePlayer(
				source, value,
				(uuid, playerName) -> {
					if (list.removePlayerName(playerName))
					{
						source.sendMessage(Component.text(String.format("Removed player %s from the %s", playerName, list.getName())));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s is already in the %s", playerName, list.getName())));
					return false;
				},
				(uuid, playerName, displayName) -> {
					if (list.removePlayerUUID(uuid) != null)
					{
						source.sendMessage(Component.text(String.format("Removed player %s from the %s", displayName, list.getName())));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s is not in the %s", displayName, list.getName())));
					return false;
				}
		);

	}

	public void onPlayerLogin(LoginEvent event)
	{
		GameProfile profile = event.getPlayer().getGameProfile();

		if (this.whitelist.isActivated())
		{
			if (!this.isPlayerInWhitelist(profile))
			{
				Component message = MiniMessage.miniMessage().deserialize(this.config.getWhitelistKickMessage());
				event.setResult(ResultedEvent.ComponentResult.denied(message));

				this.logger.info("Kicking player {} ({}) since it's not in the whitelist", profile.getName(), profile.getId());
			}
		}
		else if (this.blacklist.isActivated())
		{
			if (this.isPlayerInBlacklist(profile))
			{
				Component message = MiniMessage.miniMessage().deserialize(this.config.getBlacklistKickMessage());
				event.setResult(ResultedEvent.ComponentResult.denied(message));

				this.logger.info("Kicking player {} ({}) since it's in the blacklist", profile.getName(), profile.getId());
			}
		}
	}

	public boolean loadOneList(PlayerList destList)
	{
		PlayerList newList = destList.createNewEmptyList();
		try
		{
			if (!newList.getFilePath().toFile().isFile())
			{
				this.logger.info("Creating default empty {} file", newList.getName());
				newList.save();
			}
			newList.load(this.logger);

			destList.resetTo(newList);
			return true;
		}
		catch (IOException e)
		{
			String msg = String.format("Failed to load the %s, the plugin might not work correctly!", newList.getName());
			this.logger.error(msg, e);
			return false;
		}
	}

	public void saveList(PlayerList list)
	{
		try
		{
			list.save();
		}
		catch (IOException e)
		{
			String msg = String.format("Failed to save the %s", list.getName());
			this.logger.error(msg, e);
		}
	}
}
