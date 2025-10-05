package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import com.velocitypowered.api.proxy.Player;

public class DropCommand implements SimpleCommand {

    private final ProxyServer server;

    public DropCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (source instanceof ConsoleCommandSource && args.length > 0) {
            // Use the plugin's proxy server instance
            Player target = server.getPlayer(args[0]).orElse(null);

            if (target != null) {
                target.disconnect(Component.text("You have been disconnected."));
            } else {
                source.sendMessage(Component.text("Player not found!"));
            }
        }
    }
}