package me.fallenbreath.velocitywhitelist;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

	public record QueryResult(UUID uuid, String playerName)
	{
	}

	public static Optional<QueryResult> queryPlayerByName(Logger logger, String name)
	{
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
			return Utils.tryParseUuid(obj.id).map(uuid -> new QueryResult(uuid, obj.name));
		}
		catch (IOException | InterruptedException | IllegalArgumentException e)
		{
			logger.warn("Get UUID from mojang API failed: {}", e.toString());
			return Optional.empty();
		}
	}
}
