package me.fallenbreath.velocitywhitelist.command;

import com.google.common.base.Joiner;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import me.fallenbreath.velocitywhitelist.PluginMeta;
import me.fallenbreath.velocitywhitelist.WhitelistManager;
import me.fallenbreath.velocitywhitelist.config.PlayerList;
import net.kyori.adventure.text.Component;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static me.fallenbreath.velocitywhitelist.command.CommandUtils.*;

public class WhitelistCommand
{
	private final WhitelistManager manager;

	public WhitelistCommand(WhitelistManager whitelistManager)
	{
		this.manager = whitelistManager;
	}

	@SuppressWarnings("deprecation")  // Next time for sure...
	private void registerOne(CommandManager commandManager, String[] roots, PlayerList list)
	{
		if (roots.length == 0)
		{
			throw new IllegalArgumentException();
		}

		var root = literal(roots[0]).
				requires(s -> s.hasPermission(PluginMeta.ID + ".command")).
				executes(c -> showListStatus(c.getSource(), list)).
				then(literal("enable")
						.executes(c -> enable(c.getSource(), list))
				).
                then(
                        literal("disable")
                                .executes(c -> disable(c.getSource(), list))
                ).
				then(literal("add").
						then(argument("name", word()).
								executes(c -> addPlayer(c.getSource(), list, getString(c, "name")))
						)
				).
				then(literal("remove").
						then(argument("name", word()).
								suggests((c, sb) -> suggestMatching(this.manager.getValuesForRemovalSuggestion(list), sb)).
								executes(c -> removePlayer(c.getSource(), list, getString(c, "name")))
						)
				).
				then(literal("list").
						executes(c -> listPlayers(c.getSource(), list))
				).
				then(literal("reload").
						executes(c -> reloadList(c.getSource(), list))
				);
		commandManager.register(new BrigadierCommand(root.build()));

		for (int i = 1; i < roots.length; i++)
		{
			var alternative = literal(roots[i]).
					requires(s -> s.hasPermission(PluginMeta.ID + ".command")).
					redirect(root.build());

			commandManager.register(new BrigadierCommand(alternative.build()));
		}
	}

	public void register(CommandManager commandManager)
	{
		this.registerOne(commandManager, new String[]{"whitelist", "vwhitelist"}, this.manager.getWhitelist());
		this.registerOne(commandManager, new String[]{"blacklist", "vblacklist"}, this.manager.getBlacklist());
	}

	private int showListStatus(CommandSource source, PlayerList list)
	{
		source.sendMessage(Component.text(String.format("%s v%s", PluginMeta.NAME, PluginMeta.VERSION)));
		showListStatus(source, list, list.getName() + " ");
		return 1;
	}

	protected static void showListStatus(CommandSource source, PlayerList list, String prefix)
	{
		source.sendMessage(Component.text(String.format("%sActivated: %s (enabled: %s, load ok: %s)", prefix, list.isActivated(), list.isEnabled(), list.isLoadOk())));
		source.sendMessage(Component.text(String.format("%sSize: %d player names, %d player UUIDs", prefix, list.getPlayerNames().size(), list.getPlayerUuidMappingEntries().size())));
	}

	private int enable(CommandSource source, PlayerList list) {
		if (list.isEnabled()) {
			source.sendMessage(Component.text(String.format("%s is already enabled.", list.getName())));
			return 0;
		}
		manager.enableList(list, true);
		source.sendMessage(Component.text(String.format("%s was enabled.", list.getName())));
		return 1;
	}

	private int disable(CommandSource source, PlayerList list) {
		if (!list.isEnabled()) {
			source.sendMessage(Component.text(String.format("%s is already disabled", list.getName())));
			return 0;
		}
		manager.enableList(list, false);
		source.sendMessage(Component.text(String.format("%s was disabled.", list.getName())));
		return 1;
	}

	private int addPlayer(CommandSource source, PlayerList list, String playerName)
	{
		if (!list.isActivated())
		{
			source.sendMessage(Component.text(String.format("%s is not activated", list.getName())));
			return 0;
		}

		if (this.manager.addPlayer(source, list,playerName))
		{
			this.manager.saveList(list);
			return 1;
		}
		return 0;
	}

	private int removePlayer(CommandSource source, PlayerList list, String playerName)
	{
		if (!list.isActivated())
		{
			source.sendMessage(Component.text(String.format("%s is not activated", list.getName())));
			return 0;
		}

		if (this.manager.removePlayer(source, list, playerName))
		{
			this.manager.saveList(list);
			return 1;
		}
		return 0;
	}

	private int listPlayers(CommandSource source, PlayerList list)
	{
		if (!list.isActivated())
		{
			source.sendMessage(Component.text(String.format("%s is not activated", list.getName())));
			return 0;
		}

		var players = this.manager.getValuesForListing(list);
		source.sendMessage(Component.text(String.format("%s size: %d", list.getName(), players.size())));
		source.sendMessage(Component.text(String.format("%s players: %s", list.getName(), Joiner.on(", ").join(players))));
		return players.size();
	}

	private int reloadList(CommandSource source, PlayerList list)
	{
		if (this.manager.loadOneList(list))
		{
			source.sendMessage(Component.text(String.format("%s reloaded", list.getName())));
			return 1;
		}
		else
		{
			source.sendMessage(Component.text(String.format("%s reload failed, see console for more information", list.getName())));
			return 0;
		}
	}
}
