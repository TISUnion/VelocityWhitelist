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
	private final WhitelistManager manager;

	public WhitelistCommand(Configuration config, WhitelistManager whitelistManager)
	{
		this.config = config;
		this.manager = whitelistManager;
	}

	@SuppressWarnings("deprecation")  // Next time for sure...
	private void registerOne(CommandManager commandManager, String[] roots, Supplier<Boolean> enableStateGetter, Supplier<@Nullable PlayerList> listSupplier, Runnable listReloader)
	{
		if (roots.length == 0)
		{
			throw new IllegalArgumentException();
		}

		var root = literal(roots[0]).
				requires(s -> s.hasPermission(PluginMeta.ID + ".command")).
				executes(c -> showPluginInfo(c.getSource(), listSupplier.get(), enableStateGetter.get())).
				then(literal("add").
						then(argument("name", word()).
								executes(c -> addWhitelist(c.getSource(), listSupplier.get(), getString(c, "name")))
						)
				).
				then(literal("remove").
						then(argument("name", word()).
								suggests((c, sb) -> suggestMatching(this.manager.getValuesForRemovalSuggestion(listSupplier.get()), sb)).
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
		this.registerOne(commandManager, new String[]{"whitelist", "vwhitelist"}, this.config::isWhitelistEnabled, this.manager::getWhitelist, this.manager::loadWhitelist);
		this.registerOne(commandManager, new String[]{"blacklist", "vblacklist"}, this.config::isBlacklistEnabled, this.manager::getBlacklist, this.manager::loadBlacklist);
	}

	private int showPluginInfo(CommandSource source, @Nullable PlayerList list, boolean enabled)
	{
		if (list == null)
		{
			source.sendMessage(Component.text("Uninitialized"));
			return 0;
		}

		source.sendMessage(Component.text(String.format("%s v%s", PluginMeta.NAME, PluginMeta.VERSION)));
		source.sendMessage(Component.text(String.format("%s enabled: %s", list.getName(), enabled)));
		return enabled ? 2 : 1;
	}

	private int addWhitelist(CommandSource source, @Nullable PlayerList list, String name)
	{
		if (this.manager.addPlayer(source, list,name))
		{
			this.manager.saveList(list);
			return 1;
		}
		return 0;
	}

	private int removeWhitelist(CommandSource source, @Nullable PlayerList list, String name)
	{
		if (this.manager.removePlayer(source, list, name))
		{
			this.manager.saveList(list);
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

		var players = this.manager.getValuesForListing(list);
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
