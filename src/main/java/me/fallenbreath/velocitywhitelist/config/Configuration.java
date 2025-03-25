package me.fallenbreath.velocitywhitelist.config;

import com.google.common.collect.Maps;
import me.fallenbreath.velocitywhitelist.IdentifyMode;
import me.fallenbreath.velocitywhitelist.PluginMeta;
import me.fallenbreath.velocitywhitelist.utils.FileUtils;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public class Configuration
{
	private final Map<String, Object> options = Maps.newLinkedHashMap();
	private final Logger logger;
	private final Path configFilePath;
	private final Path configTempFilePath;

	private IdentifyMode identifyMode = IdentifyMode.DEFAULT;

	public Configuration(Logger logger, Path configFilePath)
	{
		this.logger = logger;
		this.configFilePath = configFilePath;
		this.configTempFilePath = configFilePath.resolveSibling(configFilePath.getFileName().toString() + ".tmp");
	}

	@SuppressWarnings("unchecked")
	public void load(String yamlContent)
	{
		this.options.clear();
		this.options.putAll(new Yaml().loadAs(yamlContent, this.options.getClass()));
		this.migrate();

		this.identifyMode = this.makeIdentifyMode();
	}

	public void reload() throws IOException
	{
		String content = Files.readString(this.configFilePath);
		this.load(content);
	}

	private void migrate()
	{
		boolean migrated = false;
		if (this.options.get("_version") == null)
		{
			// migrate v0.2 -> v0.3
			this.logger.warn("Migrating config file from pre-v0.3");
			this.logger.warn("Please read the documentation for more information: {}", PluginMeta.REPOSITORY_URL);

			Map<String, Object> newOptions = Maps.newLinkedHashMap();
			newOptions.put("_version", 1);
			newOptions.put("identify_mode", Optional.ofNullable(this.options.get("identify_mode")).orElse("name"));
			newOptions.put("whitelist_enabled", Optional.ofNullable(this.options.get("enabled")).orElse(true));
			newOptions.put("whitelist_kick_message", Optional.ofNullable(this.options.get("kick_message")).orElse("You are not in the whitelist!"));
			newOptions.put("blacklist_enabled", Optional.ofNullable(this.options.get("enabled")).orElse(true));
			newOptions.put("blacklist_kick_message", "You are banned from the server!");

			this.options.clear();
			this.options.putAll(newOptions);
			migrated = true;
		}

		if (migrated)
		{
			try
			{
				this.save();
			}
			catch (IOException e)
			{
				this.logger.warn("Could not save the configuration file", e);
			}
		}
	}

	private void save() throws IOException
	{
		FileUtils.dumpYaml(this.configFilePath, this.configTempFilePath, this.options);
	}

	private IdentifyMode makeIdentifyMode()
	{
		Object mode = this.options.get("identify_mode");
		if (mode instanceof String)
		{
			try
			{
				return IdentifyMode.valueOf(((String)mode).toUpperCase());
			}
			catch (IllegalArgumentException e)
			{
				this.logger.warn("Invalid identify mode: {}, use default value {}", mode, IdentifyMode.DEFAULT.name().toLowerCase());
			}
		}
		return IdentifyMode.DEFAULT;
	}

	public boolean isWhitelistEnabled()
	{
		Object enabled = this.options.get("whitelist_enabled");
		if (enabled instanceof Boolean)
		{
			return (Boolean)enabled;
		}
		return false;
	}

	public boolean isBlacklistEnabled()
	{
		Object enabled = this.options.get("blacklist_enabled");
		if (enabled instanceof Boolean)
		{
			return (Boolean)enabled;
		}
		return false;
	}

	public IdentifyMode getIdentifyMode()
	{
		return this.identifyMode;
	}

	public String getWhitelistKickMessage()
	{
		Object maxPlayer = this.options.get("whitelist_kick_message");
		if (maxPlayer instanceof String)
		{
			return (String)maxPlayer;
		}
		return "You are not in the whitelist!";
	}

	public String getBlacklistKickMessage()
	{
		Object maxPlayer = this.options.get("blacklist_kick_message");
		if (maxPlayer instanceof String)
		{
			return (String)maxPlayer;
		}
		return "You are banned from the server!";
	}
}
