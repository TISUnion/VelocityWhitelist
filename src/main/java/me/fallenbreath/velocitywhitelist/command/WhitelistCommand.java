package me.fallenbreath.velocitywhitelist.command;

import com.google.common.base.Joiner;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import me.fallenbreath.velocitywhitelist.PluginMeta;
import me.fallenbreath.velocitywhitelist.WhitelistManager;
import me.fallenbreath.velocitywhitelist.config.Configuration;
import net.kyori.adventure.text.Component;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static me.fallenbreath.velocitywhitelist.command.CommandUtils.*;

public class WhitelistCommand
{
	private final Configuration config;
	private final WhitelistManager whitelistManager;

	public WhitelistCommand(Configuration config, WhitelistManager whitelistManager)
	{
		this.config = config;
		this.whitelistManager = whitelistManager;
	}

	public void register(CommandManager commandManager)
	{
		var root = literal("whitelist").
				requires(s -> s.hasPermission(PluginMeta.ID + ".command")).
				executes(c -> showPluginInfo(c.getSource())).
				then(literal("add").
						then(argument("name", word()).
								executes(c -> addWhitelist(c.getSource(), getString(c, "name")))
						)
				).
				then(literal("remove").
						then(argument("name", word()).
								suggests((c, sb) -> suggestMatching(this.whitelistManager.getValuesForRemovalSuggestion(), sb)).
								executes(c -> removeWhitelist(c.getSource(), getString(c, "name")))
						)
				).
				then(literal("list").
						executes(c -> listWhitelist(c.getSource()))
				).
				then(literal("reload").
						executes(c -> reloadWhitelist(c.getSource()))
				);

		var alternative = literal("vwhitelist").
				requires(s -> s.hasPermission(PluginMeta.ID + ".command")).
				redirect(root.build());

		commandManager.register(new BrigadierCommand(root.build()));
		commandManager.register(new BrigadierCommand(alternative.build()));
	}

	private int showPluginInfo(CommandSource source)
	{
		source.sendMessage(Component.text(String.format("%s v%s", PluginMeta.NAME, PluginMeta.VERSION)));
		source.sendMessage(Component.text(String.format("Whitelist enabled: %s", this.config.isEnabled())));
		return 0;
	}

	private int addWhitelist(CommandSource source, String name)
	{
		if (this.whitelistManager.addPlayer(source, name))
		{
			this.whitelistManager.saveWhitelist();
			return 1;
		}
		return 0;
	}

	private int removeWhitelist(CommandSource source, String name)
	{
		if (this.whitelistManager.removePlayer(source, name))
		{
			this.whitelistManager.saveWhitelist();
			return 1;
		}
		return 0;
	}

	private int listWhitelist(CommandSource source)
	{
		var players = this.whitelistManager.getValuesForListing();
		source.sendMessage(Component.text(String.format("Whitelist size: %d", players.size())));
		source.sendMessage(Component.text(String.format("Whitelist players: %s", Joiner.on(", ").join(players))));
		return players.size();
	}

	private int reloadWhitelist(CommandSource source)
	{
		this.whitelistManager.reloadWhitelist();
		source.sendMessage(Component.text("Whitelist reloaded"));
		return 0;
	}
}
