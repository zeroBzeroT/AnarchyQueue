package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

/**
 * SlotsCommand
 */
public class SlotsCommand extends Command {

    public SlotsCommand() {
        super("globalslots");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (sender != ProxyServer.getInstance().getConsole()) {
            sender.sendMessage(new TextComponent("§cUnknown command"));
            return;
        } else if (args.length < 1) {
            sender.sendMessage(new TextComponent("§cCorrect command usage: /globalslots <int:current>"));
            return;
        } else if (args[0].equalsIgnoreCase("current")) {
            sender.sendMessage(new TextComponent("§3Current max capacity: " + Main.GLOBAL_SLOTS));
            return;
        }

        int integer = Integer.parseInt(args[0]);
        sender.sendMessage(new TextComponent("§3New max capacity is: " + integer));
        Main.GLOBAL_SLOTS = integer;
    }
}