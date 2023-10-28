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
				requires(s -> s.hasPermission("command.whitelist")).
				executes(c -> showPluginInfo(c.getSource())).
				then(literal("add").
						then(argument("name", word()).
								executes(c -> addWhitelist(c.getSource(), getString(c, "name")))
						)
				).
				then(literal("remove").
						then(argument("name", word()).
								suggests((c, sb) -> suggestMatching(this.whitelistManager.getPlayers(), sb)).
								executes(c -> removeWhitelist(c.getSource(), getString(c, "name")))
						)
				).
				then(literal("list").
						executes(c -> listWhitelist(c.getSource()))
				);

		commandManager.register(new BrigadierCommand(root.build()));
	}

	private int showPluginInfo(CommandSource source)
	{
		source.sendMessage(Component.text(String.format("%s v%s", PluginMeta.NAME, PluginMeta.VERSION)));
		source.sendMessage(Component.text(String.format("Whitelist enabled: %s", this.config.isEnabled())));
		return 0;
	}

	private int addWhitelist(CommandSource source, String name)
	{
		boolean ok = this.whitelistManager.addPlayer(name);
		if (ok)
		{
			source.sendMessage(Component.text(String.format("Added player %s to the whitelist", name)));
			this.whitelistManager.saveWhitelist();
		}
		else
		{
			source.sendMessage(Component.text(String.format("Player %s is already in the whitelist", name)));
		}
		return ok ? 1 : 0;
	}

	private int removeWhitelist(CommandSource source, String name)
	{
		boolean ok = this.whitelistManager.removePlayer(name);
		if (ok)
		{
			source.sendMessage(Component.text(String.format("Removed player %s from the whitelist", name)));
			this.whitelistManager.saveWhitelist();
		}
		else
		{
			source.sendMessage(Component.text(String.format("Player %s does not in the whitelist", name)));
		}
		return ok ? 1 : 0;
	}

	private int listWhitelist(CommandSource source)
	{
		var players = this.whitelistManager.getPlayers();
		source.sendMessage(Component.text(String.format("Whitelist size: %d", players.size())));
		source.sendMessage(Component.text(String.format("Whitelist players: %s", Joiner.on(", ").join(players))));
		return players.size();
	}
}
