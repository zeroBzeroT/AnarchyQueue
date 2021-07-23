package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.text.format.TextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * SlotsCommand
 */
public class SlotsCommand implements Command {

    @Override
    public void execute(@NonNull CommandSource source, String[] args) {

        if (!(source instanceof ConsoleCommandSource)) {
            source.sendMessage(net.kyori.text.TextComponent.of("Unknown command").color(TextColor.RED));
            return;
        }

        if (args.length != 1) {
            source.sendMessage(net.kyori.text.TextComponent.of("Current maximum player capacity is " + Config.maxPlayers).color(TextColor.DARK_AQUA));
            return;
        }

        int maxPlayers = Integer.parseInt(args[0]);

        source.sendMessage(net.kyori.text.TextComponent.of("Changed maximum player capacity to " + maxPlayers).color(TextColor.DARK_AQUA));
        Config.maxPlayers = maxPlayers;

    }

}
