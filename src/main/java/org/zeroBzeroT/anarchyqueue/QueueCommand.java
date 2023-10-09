package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

/**
 * QueueCommand
 */
public class QueueCommand extends Command {
    final Queue queue;

    public QueueCommand(Queue queue) {
        super("queue");
        this.queue = queue;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage(new TextComponent("§eThe queue is currently " + queue.queueLength() + " long."));
    }
}
