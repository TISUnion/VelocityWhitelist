package me.fallenbreath.velocitywhitelist;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.fallenbreath.velocitywhitelist.command.PluginControlCommand;
import me.fallenbreath.velocitywhitelist.command.WhitelistCommand;
import me.fallenbreath.velocitywhitelist.config.Configuration;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@Plugin(
		id = PluginMeta.ID, name = PluginMeta.NAME, version = PluginMeta.VERSION,
		url = PluginMeta.REPOSITORY_URL,
		description = "A simple whitelist plugin for velocity",
		authors = {"Fallen_Breath"}
)
public class VelocityWhitelistPlugin
{
	private final ProxyServer server;
	private final Logger logger;
	private final Path dataDirectory;
	private final Path configFilePath;
	private final Configuration config;
	private final WhitelistManager whitelistManager;

	@Inject
	public VelocityWhitelistPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory)
	{
		this.server = server;
		this.logger = logger;
		this.dataDirectory = dataDirectory;
		this.configFilePath = dataDirectory.resolve("config.yml");
		this.config = new Configuration(this.logger, this.configFilePath);
		this.whitelistManager = new WhitelistManager(logger, this.config, this.dataDirectory, this.server);
	}

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event)
	{
		if (!this.prepareConfig())
		{
			this.logger.error("Failed to prepare config, the plugin will not work");
			return;
		}

		// now the config dir definitely exists
		this.whitelistManager.loadLists();

		this.server.getEventManager().register(this, LoginEvent.class, this.whitelistManager::onPlayerLogin);
		new WhitelistCommand(this.config, this.whitelistManager).register(this.server.getCommandManager());
		new PluginControlCommand(this.logger, this.config, this.whitelistManager).register(this.server.getCommandManager());
	}

	private boolean prepareConfig()
	{
		if (!this.dataDirectory.toFile().exists() && !this.dataDirectory.toFile().mkdir())
		{
			this.logger.error("Create data directory failed");
			return false;
		}

		File file = this.configFilePath.toFile();

		if (!file.exists())
		{
			try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("config.yml"))
			{
				Files.copy(Objects.requireNonNull(in), file.toPath());
			}
			catch (Exception e)
			{
				this.logger.error("Generate default config failed", e);
				return false;
			}
		}

		try
		{
			this.config.load(Files.readString(file.toPath()));
		}
		catch (Exception e)
		{
			this.logger.error("Read config failed", e);
			return false;
		}

		return true;
	}
}
