package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class QueueCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        int queueSize = Main.getInstance().getQueue().getSize();

        source.sendMessage(Component.text("Queue is currently " + queueSize + " long.", NamedTextColor.GOLD));
    }
}

