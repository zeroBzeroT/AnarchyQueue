package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

// velocity api event docs:
// https://jd.papermc.io/velocity/3.3.0/com/velocitypowered/api/event/package-summary.html

public class Queue {
    private final Logger log;

    private final ProxyServer proxyServer;

    private final Semaphore processSemaphore = new Semaphore(1);

    /**
     * We don't use ConcurrentLinkedQueue for this because we want index-based access to players.
     */
    private final List<QueuedPlayer> queuedPlayers = new CopyOnWriteArrayList<>();

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
    public void onServerConnectedEvent(ServerConnectedEvent e) {
        if (!e.getServer().getServerInfo().getName().equals(Config.serverQueue))
            return;

        log.info("Queuing " + e.getPlayer().getUsername() + " (" + e.getPlayer().getUniqueId().toString() + ")");
        queuedPlayers.add(new QueuedPlayer(e.getPlayer(), System.currentTimeMillis()));
    }

    public void process() {
        if (!processSemaphore.tryAcquire()) return;

        // check queue server reachability
        final RegisteredServer serverQueue;

        try {
            serverQueue = getServer(Config.serverQueue);
        } catch (ServerNotReachableException e) {
            log.warn(e.getMessage());
            return;
        }

        // skip if no players queued
        if (queuedPlayers.isEmpty())
            return;

        // check the main server reachability
        final RegisteredServer serverMain;

        try {
            serverMain = getServer(Config.serverMain);
        } catch (ServerNotReachableException e) {
            if (Instant.now().getEpochSecond() % 10 == 0) {
                serverQueue.getPlayersConnected().forEach(queuedPlayer ->
                        queuedPlayer.sendMessage(Identity.nil(), Component.text(
                                Config.messageOffline
                        )));
            }
            return;
        }

        // check main server full
        boolean full = serverMain.getPlayersConnected().size() >= Config.maxPlayers;

        // send info every 10 seconds
        if (Instant.now().getEpochSecond() % 10 == 0) {
            sendInfo(serverQueue, full);
        }

        if (full)
            return;

        QueuedPlayer currPlayer = queuedPlayers.getFirst();

        //wait till delay has passed
        if (currPlayer.joinTime() + Config.joinDelay > System.currentTimeMillis())
            return;

        // connect next player
        UUID uuid = currPlayer.player().getUniqueId();

        log.info("Processing " + uuid.toString());

        // lookup player from queue server and ping to be safe the player is connected
        serverQueue.getPlayersConnected().stream()
                .filter(p -> p.getUniqueId().equals(uuid))
                .findAny().ifPresentOrElse(p -> {
                            p.sendMessage(Identity.nil(), Component.text(Config.messageConnecting));
                            try {
                                if (p.createConnectionRequest(serverMain).connect().get().isSuccessful()) queuedPlayers.removeFirst();
                            } catch (InterruptedException | ExecutionException e) {
                                log.error("Unable to connect " + p.getUsername() + "(" + p.getUniqueId().toString() + ") to " + Config.serverMain + ": " + e.getMessage());
                            }
                        },
                        () -> {
                            log.error("Unable to connect " + currPlayer.player().getUsername() + "(" + currPlayer.player().getUniqueId().toString() + ") to " + Config.serverMain + ": player is not connected to " + serverQueue.getServerInfo().getName());
                            queuedPlayers.removeFirst();
                        }
                );

        processSemaphore.release();
    }

    private void sendInfo(RegisteredServer serverQueue, boolean full) {
        for (int i = 0; i < queuedPlayers.size(); i++) {
            queuedPlayers
                    .get(i)
                    .player()
                    .sendMessage(Identity.nil(), Component.text(
                            Config.messagePosition + (i + 1) + "/" + queuedPlayers.size()
                    ));
        }

        if (full) {
            serverQueue.getPlayersConnected().forEach(queuedPlayer ->
                    queuedPlayer.sendMessage(Identity.nil(), Component.text(
                            Config.messageFull
                    )));
        }
    }

    private RegisteredServer getServer(String name) throws ServerNotReachableException {
        // get server configured in velocity.toml by name
        Optional<RegisteredServer> serverOpt = proxyServer.getServer(name);

        if (serverOpt.isEmpty()) {
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
