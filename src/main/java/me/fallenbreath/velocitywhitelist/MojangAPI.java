package me.fallenbreath.velocitywhitelist;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MojangAPI
{
	// https://wiki.vg/Mojang_API#Username_to_UUID
	private static class ResponseObject
	{
		public String name;
		public String id;
	}

	private record QueryCacheEntry(QueryResult result, long timestampMs)
	{
	}

	public record QueryResult(UUID uuid, String playerName)
	{
	}

	private static final int QUERY_CACHE_TTL_MS = 5 * 60 * 1000;  // 5min
	private static final int QUERY_CACHE_CAPACITY = 100;
	private static final List<QueryCacheEntry> queryCache = Lists.newLinkedList();

	public static Optional<QueryResult> queryPlayerByName(Logger logger, String name)
	{
		synchronized (queryCache)
		{
			long now = System.currentTimeMillis();
			queryCache.removeIf(e -> now - e.timestampMs > QUERY_CACHE_TTL_MS);
		}

		// TODO: use http proxy from my fork
		// TODO: simple expiring lru cache?
		String url = "https://api.mojang.com/users/profiles/minecraft/" + name;
		HttpClient client = HttpClient.newBuilder().build();

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(5))
				.build();
		try
		{
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			ResponseObject obj = new Gson().fromJson(response.body(), ResponseObject.class);

			if (Strings.isNullOrEmpty(obj.id))
			{
				return Optional.empty();
			}
			var ret = Utils.tryParseUuid(obj.id).map(uuid -> new QueryResult(uuid, obj.name));

			ret.ifPresent(result -> {
				synchronized (queryCache)
				{
					queryCache.add(new QueryCacheEntry(result, System.currentTimeMillis()));
					while (queryCache.size() > QUERY_CACHE_CAPACITY)
					{
						queryCache.remove(0);
					}
				}
			});
			return ret;
		}
		catch (IOException | InterruptedException | IllegalArgumentException e)
		{
			logger.warn("Get UUID from mojang API failed: {}", e.toString());
			return Optional.empty();
		}
	}
}
