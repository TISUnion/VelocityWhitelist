package me.fallenbreath.velocitywhitelist.utils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

public class MojangAPI
{
	// https://wiki.vg/Mojang_API#Username_to_UUID
	private static class ResponseObject
	{
		public String name;
		public String id;
		public String errorMessage;
	}

	private record QueryCacheEntry(String queryName, @Nullable QueryResult result, long expireAtMs)
	{
	}

	public record QueryResult(UUID uuid, String playerName)
	{
	}

	private static final int QUERY_CACHE_TTL_MS = 5 * 60 * 1000;  // 5min
	private static final int QUERY_CACHE_EMPTY_TTL_MS = 60 * 1000;  // 1min
	private static final int QUERY_CACHE_CAPACITY = 100;
	private static final List<QueryCacheEntry> queryCache = Lists.newLinkedList();

	public static Optional<QueryResult> queryPlayerByName(Logger logger, ProxyServer server, String name)
	{
		synchronized (queryCache)
		{
			long now = System.currentTimeMillis();
			queryCache.removeIf(e -> now > e.expireAtMs);
			for (QueryCacheEntry entry : queryCache)
			{
				if (Objects.equals(entry.queryName, name))
				{
					return Optional.ofNullable(entry.result());
				}
			}
		}
		BiConsumer<@Nullable QueryResult, Integer> addQueryCache = (qr, ttl) -> {
			synchronized (queryCache)
			{
				queryCache.add(new QueryCacheEntry(name, qr, System.currentTimeMillis() + ttl));
				while (queryCache.size() > QUERY_CACHE_CAPACITY)
				{
					queryCache.remove(0);
				}
			}
		};

		// TODO: use http proxy from my fork
		// TODO: simple expiring lru cache?
		String url = "https://api.mojang.com/users/profiles/minecraft/" + name;
		HttpClient client = createHttpClient(server);

		HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(5))
				.build();
		try
		{
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			ResponseObject obj = new Gson().fromJson(response.body(), ResponseObject.class);
			if (response.statusCode() == 204 || (obj.errorMessage != null && obj.errorMessage.startsWith("Couldn't find any profile with that name")))
			{
				addQueryCache.accept(null, QUERY_CACHE_EMPTY_TTL_MS);
			}

			if (Strings.isNullOrEmpty(obj.id))
			{
				return Optional.empty();
			}
			var ret = UuidUtils.tryParseUuid(obj.id).map(uuid -> new QueryResult(uuid, obj.name));
			ret.ifPresent(result -> addQueryCache.accept(result, QUERY_CACHE_TTL_MS));
			return ret;
		}
		catch (IOException | InterruptedException | IllegalArgumentException e)
		{
			logger.warn("Get UUID from mojang API failed: {}", e.toString());
			return Optional.empty();
		}
	}

	private static HttpClient createHttpClient(ProxyServer server)
	{
		try
		{
			// try using the auth proxy setting from https://github.com/TISUnion/Velocity
			Class<?> clazz = Class.forName("com.velocitypowered.proxy.VelocityServer");
			if (clazz.isInstance(server))
			{
				Method method = clazz.getMethod("createProxiedHttpClient");
				method.setAccessible(true);
				Object client = method.invoke(server);
				if (client instanceof HttpClient httpClient)
				{
					return httpClient;
				}
			}
		}
		catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | IllegalAccessException ignored)
		{
		}
		return HttpClient.newBuilder().build();
	}
}
