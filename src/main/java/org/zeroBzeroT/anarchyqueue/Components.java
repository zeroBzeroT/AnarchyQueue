package org.zeroBzeroT.anarchyqueue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * helper method because the MiniMessage syntax is too verbose
 */
public final class Components {
    public static Component mm(String miniMessageString) { // mm, short for MiniMessage
        return MiniMessage.miniMessage().deserialize(miniMessageString);
    }
}
