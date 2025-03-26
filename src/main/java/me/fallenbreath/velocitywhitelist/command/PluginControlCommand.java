package me.fallenbreath.velocitywhitelist.command;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import me.fallenbreath.velocitywhitelist.PluginMeta;
import me.fallenbreath.velocitywhitelist.WhitelistManager;
import me.fallenbreath.velocitywhitelist.config.Configuration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import static me.fallenbreath.velocitywhitelist.command.CommandUtils.literal;

public class PluginControlCommand
{
	private final Logger logger;
	private final Configuration config;
	private final WhitelistManager manager;

	public PluginControlCommand(Logger logger, Configuration config, WhitelistManager whitelistManager)
	{
		this.logger = logger;
		this.config = config;
		this.manager = whitelistManager;
	}

	@SuppressWarnings("deprecation")  // Next time for sure...
	public void register(CommandManager commandManager)
	{
		var root = literal("velocitywhitelist").
				requires(s -> s.hasPermission(PluginMeta.ID + ".command")).
				executes(c -> showPluginInfo(c.getSource())).
				then(literal("reload").
						executes(c -> reloadAll(c.getSource()))
				);
		commandManager.register(new BrigadierCommand(root.build()));
	}

	private int reloadAll(CommandSource source)
	{
		try
		{
			this.config.reload();
			this.manager.loadLists();
			source.sendMessage(Component.text("Reloaded config, whitelist and blacklist"));
			return 1;
		}
		catch (Exception e)
		{
			this.logger.error("Failed to reload", e);
			source.sendMessage(Component.text("Reload failed, see console for details"));
		}
		return 0;
	}

	private int showPluginInfo(CommandSource source)
	{
		source.sendMessage(Component.text(String.format("%s v%s", PluginMeta.NAME, PluginMeta.VERSION)));
		source.sendMessage(Component.text(String.format("Identify Mode: %s", this.config.getIdentifyMode().name().toLowerCase())));
		source.sendMessage(Component.text(PluginMeta.REPOSITORY_URL, NamedTextColor.BLUE, TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(PluginMeta.REPOSITORY_URL)));
		source.sendMessage(Component.text("Whitelist:"));
		WhitelistCommand.showListStatus(source, this.manager.getWhitelist(), "  ");
		source.sendMessage(Component.text("Blacklist:"));
		WhitelistCommand.showListStatus(source, this.manager.getBlacklist(), "  ");
		return 0;
	}
}
