package me.fallenbreath.velocitywhitelist.config;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import me.fallenbreath.velocitywhitelist.utils.FileUtils;
import me.fallenbreath.velocitywhitelist.utils.UuidUtils;
import org.jetbrains.annotations.NotNull;
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
	private final Path filePath;
	private boolean loadOk = false;
	private final Object lock = new Object();
	private boolean enabled;

	public PlayerList(String name, Path filePath)
	{
		this.name = name;
		this.filePath = filePath;
	}

	public String getName()
	{
		return this.name;
	}

	public Path getFilePath()
	{
		return this.filePath;
	}

	public boolean isLoadOk()
	{
		synchronized (this.lock)
		{
			return this.loadOk;
		}
	}

	public boolean isEnabled() {
		synchronized (this.lock)
		{
			return this.enabled;
		}
	}

    public void setEnabled(boolean enabled)
    {
        synchronized (this.lock)
        {
            this.enabled = enabled;
        }
    }

	public boolean isActivated()
	{
		synchronized (this.lock)
		{
			return this.isLoadOk() && this.isEnabled();
		}
	}

	public ImmutableList<String> getPlayerNames()
	{
		synchronized (this.lock)
		{
			return ImmutableList.copyOf(this.names);
		}
	}

	public boolean checkPlayerName(String name)
	{
		synchronized (this.lock)
		{
			return this.names.contains(name);
		}
	}

	public boolean addPlayerName(String name)
	{
		synchronized (this.lock)
		{
			return this.names.add(name);
		}
	}

	public boolean removePlayerName(String name)
	{
		synchronized (this.lock)
		{
			return this.names.remove(name);
		}
	}

	public ImmutableList<Map.Entry<UUID, @Nullable String>> getPlayerUuidMappingEntries()
	{
		synchronized (this.lock)
		{
			return ImmutableList.copyOf(this.uuids.entrySet());
		}
	}

	public boolean checkPlayerUUID(UUID uuid)
	{
		synchronized (this.lock)
		{
			return this.uuids.containsKey(uuid);
		}
	}

	public static class PlayerUUIDComputeResult<T>
	{
		public boolean addNewValue = false;
		public @Nullable String newValue = null;
		public T ret = null;
	}

	@FunctionalInterface
	public interface PlayerUUIDComputeFunction<T>
	{
		PlayerUUIDComputeResult<T> compute(boolean exists, @Nullable String oldName);
	}

	public <T> T computePlayerUUID(UUID uuid, PlayerUUIDComputeFunction<T> func)
	{
		synchronized (this.lock)
		{
			boolean exists = this.uuids.containsKey(uuid);
			String oldName = this.uuids.get(uuid);
			PlayerUUIDComputeResult<T> result = func.compute(exists, oldName);
			if (result.addNewValue)
			{
				this.uuids.put(uuid, result.newValue);
			}
			return result.ret;
		}
	}

	public @Nullable String removePlayerUUID(UUID uuid)
	{
		synchronized (this.lock)
		{
			return this.uuids.remove(uuid);
		}
	}

	public void resetTo(@NotNull PlayerList newList)
	{
		synchronized (this.lock)
		{
			if (!this.name.equals(newList.getName()))
			{
				throw new IllegalArgumentException("Attempted to reset to a player list with different name");
			}
			if (!this.filePath.equals(newList.getFilePath()))
			{
				throw new IllegalArgumentException("Attempted to reset to a player list with different filePath");
			}
			if (!newList.loadOk)
			{
				throw new IllegalArgumentException("Attempted to reset to a player list with loadOk == false");
			}
			this.names.clear();
			this.names.addAll(newList.names);
			this.uuids.clear();
			this.uuids.putAll(newList.uuids);
			this.loadOk = true;
		}
	}

	public PlayerList createNewEmptyList()
	{
		return new PlayerList(this.name, this.filePath);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void load(Logger logger) throws IOException
	{
		Map<String, Object> options = Maps.newHashMap();
		String yamlContent = Files.readString(this.filePath);

		options = new Yaml().loadAs(yamlContent, options.getClass());

		synchronized (this.lock)
		{
			if (options.get("enabled") instanceof Boolean bool) {
				this.enabled = bool;
			} else {
				this.enabled = false;
			}

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

			this.loadOk = true;
			logger.info("{} loaded with {} names and {} uuids", this.name, this.names.size(), this.uuids.size());
		}
	}

	public void save() throws IOException
	{
		Map<String, Object> options = Maps.newLinkedHashMap();

		synchronized (this.lock)
		{
            options.put("enabled", this.enabled);
			options.put("names", Lists.newArrayList(this.names));
			List<Object> uuidList = this.uuids.entrySet().stream()
					.map(e -> e.getValue() != null ? Map.of(e.getKey().toString(), e.getValue()) : e.getKey().toString())
					.toList();
			options.put("uuids", uuidList);
		}

		FileUtils.dumpYaml(this.filePath, options);
	}
}
