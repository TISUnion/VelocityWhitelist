package me.fallenbreath.velocitywhitelist;

import com.google.common.base.Supplier;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public class WhitelistManager
{
	private final Logger logger;
	private final Configuration config;
	private final Path whitelistFilePath;
	private final Path blacklistFilePath;
	private final ProxyServer server;
	private final Object listLock = new Object();
	@Nullable private PlayerList whitelist = null;
	@Nullable private PlayerList blacklist = null;

	public WhitelistManager(Logger logger, Configuration config, Path dataDirectory, ProxyServer server)
	{
		this.logger = logger;
		this.config = config;
		this.whitelistFilePath = dataDirectory.resolve("whitelist.yml");
		this.blacklistFilePath = dataDirectory.resolve("blacklist.yml");
		this.server = server;
	}

	@Nullable
	public PlayerList getWhitelist()
	{
		return this.whitelist;
	}

	@Nullable
	public PlayerList getBlacklist()
	{
		return this.blacklist;
	}

	public void loadLists()
	{
		this.loadWhitelist();
		this.loadBlacklist();
	}

	public void loadWhitelist()
	{
		this.loadOneList(() -> new PlayerList("Whitelist", this.whitelistFilePath), list -> this.whitelist = list);
	}

	public void loadBlacklist()
	{
		this.loadOneList(() -> new PlayerList("Blacklist", this.blacklistFilePath), list -> this.blacklist = list);
	}

	private boolean isPlayerInList(GameProfile profile, @Nullable PlayerList list)
	{
		if (list == null)
		{
			return false;
		}
		synchronized (this.listLock)
		{
			return switch (this.config.getIdentifyMode())
			{
				case NAME -> list.getNames().contains(profile.getName());
				case UUID -> list.getUuidMapping().containsKey(profile.getId());
			};
		}
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

	public List<String> getValuesForRemovalSuggestion(@Nullable PlayerList list)
	{
		if (list == null)
		{
			return Collections.emptyList();
		}
		synchronized (this.listLock)
		{
			switch (this.config.getIdentifyMode())
			{
				case NAME:
					return Lists.newArrayList(list.getNames());
				case UUID:
					List<String> values = Lists.newArrayList();
					list.getUuidMapping().keySet().forEach(uuid -> values.add(uuid.toString()));
					list.getUuidMapping().values().forEach(name -> {
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

	public List<String> getValuesForListing(@Nullable PlayerList list)
	{
		if (list == null)
		{
			return Collections.emptyList();
		}
		synchronized (this.listLock)
		{
			return switch (this.config.getIdentifyMode())
			{
				case NAME -> Lists.newArrayList(list.getNames());
				case UUID -> list.getUuidMapping().entrySet().stream()
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

		synchronized (this.listLock)
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

	public boolean addPlayer(CommandSource source, @Nullable PlayerList list, String value)
	{
		if (list == null)
		{
			source.sendMessage(Component.text("Uninitialized"));
			return false;
		}
		return this.operatePlayer(
				source, value,
				(uuid, playerName) -> {
					if (list.getNames().add(playerName))
					{
						source.sendMessage(Component.text(String.format("Added player %s to the %s", playerName, list.getName())));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s is already in the %s", playerName, list.getName())));
					return false;
				},
				(uuid, playerName, displayName) -> {
					Map<UUID, String> uuids = list.getUuidMapping();
					if (uuids.containsKey(uuid))
					{
						String oldName = uuids.get(uuid);
						if (playerName != null && (oldName == null || !oldName.equals(playerName)))
						{
							uuids.put(uuid, playerName);  // set player name as a comment
							source.sendMessage(Component.text(String.format(
									"Player %s is already in the %s, updated player name for this uuid from %s to %s",
									displayName, list.getName(), oldName, playerName
							)));
						}
						else
						{
							source.sendMessage(Component.text(String.format("Player %s is already in the %s", displayName, list.getName())));
						}
						return false;
					}

					uuids.put(uuid, playerName);
					source.sendMessage(Component.text(String.format("Added player %s to the %s", displayName, list.getName())));
					return true;
				}
		);
	}

	public boolean removePlayer(CommandSource source, @Nullable PlayerList list, String value)
	{
		if (list == null)
		{
			source.sendMessage(Component.text("Uninitialized"));
			return false;
		}
		return this.operatePlayer(
				source, value,
				(uuid, playerName) -> {
					if (list.getNames().add(playerName))
					{
						source.sendMessage(Component.text(String.format("Removed player %s from the %s", playerName, list.getName())));
						return true;
					}
					source.sendMessage(Component.text(String.format("Player %s is already in the %s", playerName, list.getName())));
					return false;
				},
				(uuid, playerName, displayName) -> {
					if (list.getUuidMapping().remove(uuid) != null)
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

		if (this.config.isWhitelistEnabled() && this.whitelist != null)
		{
			if (!this.isPlayerInWhitelist(profile))
			{
				TextComponent message = Component.text(this.config.getWhitelistKickMessage());
				event.setResult(ResultedEvent.ComponentResult.denied(message));

				this.logger.info("Kicking player {} ({}) since it's not in the whitelist", profile.getName(), profile.getId());
			}
		}
		else if (this.config.isBlacklistEnabled() && this.blacklist != null)
		{
			if (this.isPlayerInBlacklist(profile))
			{
				TextComponent message = Component.text(this.config.getBlacklistKickMessage());
				event.setResult(ResultedEvent.ComponentResult.denied(message));

				this.logger.info("Kicking player {} ({}) since it's in the blacklist", profile.getName(), profile.getId());
			}
		}
	}

	private void loadOneList(Supplier<@NotNull PlayerList> listFactory, Consumer<@NotNull PlayerList> listAssigner)
	{
		PlayerList list = listFactory.get();
		try
		{
			if (!list.getFilePath().toFile().isFile())
			{
				this.logger.info("Creating default empty {} file", list.getName());
				list.save();
			}
			list.load(this.logger);

			synchronized (this.listLock)
			{
				listAssigner.accept(list);
			}
		}
		catch (IOException e)
		{
			String msg = String.format("Failed to load the %s, the plugin will not work correctly!", list.getName());
			this.logger.error(msg, e);
		}
	}

	public void saveList(@Nullable PlayerList list)
	{
		if (list == null)
		{
			return;
		}
		try
		{
			synchronized (this.listLock)
			{
				list.save();
			}
		}
		catch (IOException e)
		{
			String msg = String.format("Failed to save the %s", list.getName());
			this.logger.error(msg, e);
		}
	}
}
