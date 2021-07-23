package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

public class Queue {

    private final ProxyServer proxyServer;
    private final BlockingQueue<UUID> queuedPlayers;
    private final Map<UUID, Instant> kickedPlayers;

    public Queue(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
        this.queuedPlayers = new LinkedBlockingDeque<>();
        this.kickedPlayers = new ConcurrentHashMap<>();

        // Schedule queue flusher
        proxyServer.getScheduler()
            .buildTask(Main.getInstance(), this::flushQueue)
            .delay(Duration.ofSeconds(1))
            .repeat(Duration.ofSeconds(2))
            .schedule();

        // Schedule player notification
        proxyServer.getScheduler()
            .buildTask(Main.getInstance(), this::sendUpdate)
            .delay(Duration.ofSeconds(1))
            .repeat(Duration.ofSeconds(10))
            .schedule();

    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent e) {
        queuedPlayers.add(e.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent e) {
        kickedPlayers.put(e.getPlayer().getUniqueId(), Instant.now());
    }

    public void flushQueue() {
        final RegisteredServer serverMain;
        final RegisteredServer serverQueue;
        try {
            serverMain = getServer(Config.serverMain);
            serverQueue = getServer(Config.serverQueue);
        } catch (ServerNotReachableException e) {
            Main.getInstance().log.warn(e.getMessage());
            return;
        }
        // check current player counts
        int onlinePlayersMain = serverMain.getPlayersConnected().size();
        int onlinePlayersQueue = serverQueue.getPlayersConnected().size();
        Main.getInstance().log.info("Online players: main " + onlinePlayersMain + " / queue " + onlinePlayersQueue);
        // get next player to move to main server
        UUID uuid;
        try {
            uuid = queuedPlayers.poll(1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Main.getInstance().log.warn(e.getMessage());
            return;
        }
        Optional<Player> playerOpt = serverQueue.getPlayersConnected().stream().filter(p -> p.getUniqueId().equals(uuid)).findAny();
        // skip if player was kicked recently
        if (kickedPlayers.containsKey(uuid) &&
            kickedPlayers.get(uuid).plus(1, ChronoUnit.MINUTES).isBefore(Instant.now()))
            return;
        // connect if player is found
        playerOpt.ifPresent(player -> player.createConnectionRequest(serverMain));
    }

    private RegisteredServer getServer(String serverName) throws ServerNotReachableException {
        // get server configured in velocity by name
        Optional<RegisteredServer> serverOpt = proxyServer.getServer(serverName);
        if (!serverOpt.isPresent()) {
            throw new ServerNotReachableException("Server " + serverName + " is not configured!");
        }
        final RegisteredServer serverQueue = serverOpt.get();
        // test server availability by pinging
        try {
            serverQueue.ping().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ServerNotReachableException("Server " + serverName + " is not reachable: " + e.getMessage());
        }
        return serverQueue;
    }

    public void sendUpdate() {
    }

    public void processKicked() {
    }

}
