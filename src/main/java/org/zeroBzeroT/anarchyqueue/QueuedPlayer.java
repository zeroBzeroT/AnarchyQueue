package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.proxy.Player;

public record QueuedPlayer(Player player, long joinTime) {
}
