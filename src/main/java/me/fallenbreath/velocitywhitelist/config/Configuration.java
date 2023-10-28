package me.fallenbreath.velocitywhitelist.config;

import com.google.common.collect.Maps;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

public class Configuration
{
	private final Map<String, Object> options = Maps.newHashMap();

	@SuppressWarnings("unchecked")
	public void load(String yamlContent)
	{
		this.options.clear();
		this.options.putAll(new Yaml().loadAs(yamlContent, this.options.getClass()));
	}

	public boolean isEnabled()
	{
		Object playerCount = this.options.get("enabled");
		if (playerCount instanceof Boolean)
		{
			return (Boolean)playerCount;
		}
		return false;
	}

	public String getKickMessage()
	{
		Object maxPlayer = this.options.get("kick_message");
		if (maxPlayer instanceof String)
		{
			return (String)maxPlayer;
		}
		return "You are not in whitelist!";
	}
}
