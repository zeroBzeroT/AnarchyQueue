package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

public class Queue {

    private final Logger log;
    private final ProxyServer proxyServer;

    /**
     * We dont use ConcurrentLinkedQueue for this because we want index based access to players.
     */
    private final List<Player> queuedPlayers = new CopyOnWriteArrayList<>();

    public Queue(ProxyServer proxyServer) {
        this.log = Main.getInstance().log;
        this.proxyServer = proxyServer;
        // process queue
        proxyServer.getScheduler()
            .buildTask(Main.getInstance(), this::process)
            .delay(Duration.ofSeconds(1))
            .repeat(Duration.ofSeconds(1))
            .schedule();
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent e) {
        queuedPlayers.add(e.getPlayer());
    }

    public void process() {
        // check queue server reachability
        final RegisteredServer serverQueue;
        try {
            serverQueue = getServer(Config.serverQueue);
        } catch (ServerNotReachableException e) {
            log.warn(e.getMessage());
            return;
        }
        // check main server reachability
        final RegisteredServer serverMain;
        try {
            serverMain = getServer(Config.serverMain);
        } catch (ServerNotReachableException e) {
            serverQueue.getPlayersConnected().forEach(queuedPlayer ->
                queuedPlayer.sendMessage(TextComponent.of(
                    "Server is currently offline!"
                ).color(TextColor.RED)));
            return;
        }
        // check main server full
        boolean full = serverMain.getPlayersConnected().size() > 200;
        // send infos every 10 seconds
        if (Instant.now().getEpochSecond() % 10 == 0) {
            sendInfos(serverQueue, full);
        }
        if (full) return;
        if (queuedPlayers.size() == 0) return;
        // connect next player
        UUID uuid = queuedPlayers.remove(0).getUniqueId();
        // lookup player from queue server and ping to be safe the player is connected
        serverQueue.getPlayersConnected().stream()
            .filter(p -> p.getUniqueId().equals(uuid))
            .filter(p -> p.getPing() != -1)
            .findAny().ifPresent(p -> p.createConnectionRequest(serverMain));
    }

    private void sendInfos(RegisteredServer serverQueue, boolean full) {
        for (int i = 0; i < queuedPlayers.size(); i++) {
            queuedPlayers
                .get(i)
                .sendMessage(TextComponent.of(
                    "Position in queue: " + i + "/" + queuedPlayers.size()
                ).color(TextColor.DARK_AQUA));
        }
        if (full) {
            serverQueue.getPlayersConnected().forEach(queuedPlayer ->
                queuedPlayer.sendMessage(TextComponent.of(
                    "Server is currently full!"
                ).color(TextColor.DARK_AQUA)));
        }
    }

    private RegisteredServer getServer(String name) throws ServerNotReachableException {
        // get server configured in velocity.toml by name
        Optional<RegisteredServer> serverOpt = proxyServer.getServer(name);
        if (!serverOpt.isPresent()) {
            throw new ServerNotReachableException("Server " + name + " is not configured!");
        }
        final RegisteredServer server = serverOpt.get();
        // test server availability by pinging
        try {
            server.ping().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ServerNotReachableException("Server " + name + " is not reachable: " + e.getMessage());
        }
        return server;
    }

}
