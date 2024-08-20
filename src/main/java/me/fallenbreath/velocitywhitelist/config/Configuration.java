package me.fallenbreath.velocitywhitelist.config;

import com.google.common.collect.Maps;
import me.fallenbreath.velocitywhitelist.IdentifyMode;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

public class Configuration
{
	private final Map<String, Object> options = Maps.newHashMap();
	private final Logger logger;

	private IdentifyMode identifyMode = IdentifyMode.DEFAULT;

	public Configuration(Logger logger)
	{
		this.logger = logger;
	}

	@SuppressWarnings("unchecked")
	public void load(String yamlContent)
	{
		this.options.clear();
		this.options.putAll(new Yaml().loadAs(yamlContent, this.options.getClass()));
		this.identifyMode = this.makeIdentifyMode();
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

	public boolean isEnabled()
	{
		Object enabled = this.options.get("enabled");
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

	public String getKickMessage()
	{
		Object maxPlayer = this.options.get("kick_message");
		if (maxPlayer instanceof String)
		{
			return (String)maxPlayer;
		}
		return "You are not in the whitelist!";
	}
}
