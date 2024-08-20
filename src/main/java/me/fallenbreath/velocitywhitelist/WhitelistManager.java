package me.fallenbreath.velocitywhitelist;

import com.google.common.collect.Lists;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.util.GameProfile;
import me.fallenbreath.velocitywhitelist.config.Configuration;
import me.fallenbreath.velocitywhitelist.config.Whitelist;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class WhitelistManager
{
	private final Logger logger;
	private final Configuration config;
	private final Path whitelistFilePath;
	private Whitelist whitelist = null;
	private final Object whitelistLock = new Object();

	public WhitelistManager(Logger logger, Configuration config, Path dataDirectory)
	{
		this.logger = logger;
		this.config = config;
		this.whitelistFilePath = dataDirectory.resolve("whitelist.yml");
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
			return this.whitelist.getNames().contains(profile.getName());
		}
	}

	/**
	 * @return true: added, false: already exists
	 */
	public boolean addPlayer(String name)
	{
		synchronized (this.whitelistLock)
		{
			return this.whitelist.getNames().add(name);
		}
	}

	/**
	 * @return true: removed, false: not exists
	 */
	public boolean removePlayer(String name)
	{
		synchronized (this.whitelistLock)
		{
			return this.whitelist.getNames().remove(name);
		}
	}

	public List<String> getPlayers()
	{
		synchronized (this.whitelistLock)
		{
			return Lists.newArrayList(this.whitelist.getNames());
		}
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
			whitelist.load();

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
