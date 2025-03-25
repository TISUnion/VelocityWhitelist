package me.fallenbreath.velocitywhitelist.config;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import me.fallenbreath.velocitywhitelist.utils.FileUtils;
import me.fallenbreath.velocitywhitelist.utils.UuidUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerList
{
	private final Set<String> names = Sets.newLinkedHashSet();
	private final Map<UUID, @Nullable String> uuids = Maps.newLinkedHashMap();
	private final String name;
	private final Path listFilePath;
	private final Path listTempFilePath;

	public PlayerList(String name, Path listFilePath)
	{
		this.name = name;
		this.listFilePath = listFilePath;
		this.listTempFilePath = listFilePath.resolveSibling(listFilePath.getFileName().toString() + ".tmp");
	}

	public String getName()
	{
		return this.name;
	}

	public Path getFilePath()
	{
		return this.listFilePath;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void load(Logger logger) throws IOException
	{
		Map<String, Object> options = Maps.newHashMap();
		String yamlContent = Files.readString(listFilePath);

		options = new Yaml().loadAs(yamlContent, options.getClass());

		this.names.clear();
		if (options.get("names") instanceof List list)
		{
			list.forEach(entry -> this.names.add(entry.toString()));
		}

		this.uuids.clear();
		if (options.get("uuids") instanceof List list)
		{
			list.forEach(item -> {
				if (item instanceof String s)
				{
					UuidUtils.tryParseUuid(s).ifPresentOrElse(
							uuid -> this.uuids.put(uuid, null),
							() -> logger.warn("Skipping invalid UUID \"{}\"", s)
					);
				}
				else if (item instanceof Map<?, ?> map)
				{
					if (map.size() != 1)
					{
						logger.warn("Skipping invalid map item with size {}", map.size());
					}
					else
					{
						Map.Entry<?, ?> entry = map.entrySet().iterator().next();
						if (entry.getKey() instanceof String s && (entry.getValue() instanceof String || entry.getValue() == null))
						{
							String name = (String)entry.getValue();
							UuidUtils.tryParseUuid(s).ifPresentOrElse(
									uuid -> this.uuids.put(uuid, name),
									() -> logger.warn("Skipping invalid UUID \"{}\" ({})", s, name)
							);
						}
					}
				}
				else
				{
					logger.warn("Skipping invalid UUID list item {}", item);
				}
			});
		}
	}

	public void save() throws IOException
	{
		Map<String, Object> options = Maps.newHashMap();
		options.put("names", Lists.newArrayList(this.names));
		List<Object> uuidList = this.uuids.entrySet().stream()
				.map(e -> e.getValue() != null ? Map.of(e.getKey().toString(), e.getValue()) : e.getKey().toString())
				.toList();
		options.put("uuids", uuidList);

		FileUtils.dumpYaml(this.listFilePath, this.listTempFilePath, options);
	}

	public Set<String> getNames()
	{
		return this.names;
	}

	public Map<UUID, @Nullable String> getUuidMapping()
	{
		return this.uuids;
	}
}
