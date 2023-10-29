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
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Whitelist
{
	private final Set<String> names = Sets.newLinkedHashSet();
	private final Path whitelistFilePath;
	private final Path whitelistTempFilePath;

	public Whitelist(Path whitelistFilePath)
	{
		this.whitelistFilePath = whitelistFilePath;
		this.whitelistTempFilePath = whitelistFilePath.resolveSibling(whitelistFilePath.getFileName().toString() + ".tmp");
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
		Files.writeString(this.whitelistTempFilePath, yamlContent, StandardCharsets.UTF_8);
		Files.move(this.whitelistTempFilePath, this.whitelistFilePath, StandardCopyOption.REPLACE_EXISTING);
	}

	public Set<String> getNames()
	{
		return this.names;
	}
}
