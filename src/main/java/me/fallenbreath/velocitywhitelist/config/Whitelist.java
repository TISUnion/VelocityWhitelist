package me.fallenbreath.velocitywhitelist.config;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Whitelist
{
	private final Set<String> names = Sets.newLinkedHashSet();
	private final Path whitelistFilePath;

	public Whitelist(Path whitelistFilePath)
	{
		this.whitelistFilePath = whitelistFilePath;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void load() throws IOException
	{
		Map<String, Object> options = Maps.newHashMap();
		String yamlContent = Files.readString(whitelistFilePath);

		options = new Yaml().loadAs(yamlContent, options.getClass());

		this.names.clear();
		Object list = options.get("names");
		if (list instanceof List)
		{
			((List)list).forEach(entry -> this.names.add(entry.toString()));
		}
	}

	public void save() throws IOException
	{
		Map<String, Object> options = Maps.newHashMap();
		options.put("names", Lists.newArrayList(this.names));

		DumperOptions dumperOptions = new DumperOptions();
		dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

		String yamlContent = new Yaml(dumperOptions).dump(options);
		Files.writeString(this.whitelistFilePath, yamlContent, StandardCharsets.UTF_8);
	}

	public Set<String> getNames()
	{
		return this.names;
	}
}
