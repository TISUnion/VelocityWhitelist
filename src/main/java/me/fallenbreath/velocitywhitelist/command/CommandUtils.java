package me.fallenbreath.velocitywhitelist.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.CommandSource;

import java.util.concurrent.CompletableFuture;

public class CommandUtils
{
	public static LiteralArgumentBuilder<CommandSource> literal(final String name)
	{
		return LiteralArgumentBuilder.literal(name);
	}

	public static <T> RequiredArgumentBuilder<CommandSource, T> argument(final String name, final ArgumentType<T> type)
	{
		return RequiredArgumentBuilder.argument(name, type);
	}

	public static CompletableFuture<Suggestions> suggestMatching(Iterable<String> suggestions, SuggestionsBuilder suggestionsBuilder)
	{
		String remaining = suggestionsBuilder.getRemaining().toLowerCase();

		for (String suggestion : suggestions)
		{
			if (suggestion.toLowerCase().startsWith(remaining))
			{
				suggestionsBuilder.suggest(suggestion);
			}
		}

		return suggestionsBuilder.buildFuture();
	}
}
