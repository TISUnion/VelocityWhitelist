package me.fallenbreath.velocitywhitelist.command;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import me.fallenbreath.velocitywhitelist.PluginMeta;
import me.fallenbreath.velocitywhitelist.WhitelistManager;
import me.fallenbreath.velocitywhitelist.config.Configuration;
import me.fallenbreath.velocitywhitelist.config.PlayerList;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

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

	@SuppressWarnings("deprecation")  // Next time for sure...
	private void registerOne(CommandManager commandManager, String[] roots, Supplier<@Nullable PlayerList> listSupplier, Runnable listReloader)
	{
		if (roots.length == 0)
		{
			throw new IllegalArgumentException();
		}

		var root = literal(roots[0]).
				requires(s -> s.hasPermission(PluginMeta.ID + ".command")).
				executes(c -> showPluginInfo(c.getSource())).
				then(literal("add").
						then(argument("name", word()).
								executes(c -> addWhitelist(c.getSource(), listSupplier.get(), getString(c, "name")))
						)
				).
				then(literal("remove").
						then(argument("name", word()).
								suggests((c, sb) -> suggestMatching(this.whitelistManager.getValuesForRemovalSuggestion(listSupplier.get()), sb)).
								executes(c -> removeWhitelist(c.getSource(), listSupplier.get(), getString(c, "name")))
						)
				).
				then(literal("list").
						executes(c -> listWhitelist(c.getSource(), listSupplier.get()))
				).
				then(literal("reload").
						executes(c -> reloadWhitelist(c.getSource(), listReloader, listSupplier))
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
		this.registerOne(commandManager, new String[]{"whitelist", "vwhitelist"}, this.whitelistManager::getWhitelist, this.whitelistManager::loadWhitelist);
		this.registerOne(commandManager, new String[]{"blacklist", "vblacklist"}, this.whitelistManager::getBlacklist, this.whitelistManager::loadBlacklist);
	}

	private int showPluginInfo(CommandSource source)
	{
		source.sendMessage(Component.text(String.format("%s v%s", PluginMeta.NAME, PluginMeta.VERSION)));
		source.sendMessage(Component.text(String.format("Whitelist enabled: %s", this.config.isWhitelistEnabled())));
		source.sendMessage(Component.text(String.format("Blacklist enabled: %s", this.config.isBlacklistEnabled())));
		return 0;
	}

	private int addWhitelist(CommandSource source, @Nullable PlayerList list,  String name)
	{
		if (this.whitelistManager.addPlayer(source, list,name))
		{
			this.whitelistManager.saveList(list);
			return 1;
		}
		return 0;
	}

	private int removeWhitelist(CommandSource source, @Nullable PlayerList list, String name)
	{
		if (this.whitelistManager.removePlayer(source, list, name))
		{
			this.whitelistManager.saveList(list);
			return 1;
		}
		return 0;
	}

	private int listWhitelist(CommandSource source, @Nullable PlayerList list)
	{
		if (list == null)
		{
			source.sendMessage(Component.text("Uninitialized"));
			return 0;
		}

		var players = this.whitelistManager.getValuesForListing(list);
		source.sendMessage(Component.text(String.format("%s size: %d", list.getName(), players.size())));
		source.sendMessage(Component.text(String.format("%s players: %s", list.getName(), Joiner.on(", ").join(players))));
		return players.size();
	}

	private int reloadWhitelist(CommandSource source, Runnable listReloader, Supplier<@Nullable PlayerList> listGetter)
	{
		listReloader.run();
		String name = Optional.ofNullable(listGetter.get()).map(PlayerList::getName).orElse("?");
		source.sendMessage(Component.text(String.format("%s reloaded", name)));
		return 0;
	}
}
