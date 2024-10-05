package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
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

    private final Semaphore queueSemaphore = new Semaphore(1);

    /**
     * We don't use ConcurrentLinkedQueue for this because we want index-based access to players.
     */
    private final List<QueuedPlayer> queuedPlayers = new CopyOnWriteArrayList<>();

    /**
     * Initializes a queue
     */
    public Queue(ProxyServer proxyServer) {
        this.log = Main.getInstance().log;
        this.proxyServer = proxyServer;

        // Schedule queue flusher
        proxyServer.getScheduler()
                .buildTask(Main.getInstance(), this::flushQueue)
                .delay(Duration.ofSeconds(1))
                .repeat(Duration.ofSeconds(2))
                .schedule();
    }

    /**
     * This event is called once a connection to a server is fully operational.
     * Add a Player to the queue if they join the queue server.
     */
    @Subscribe
    public void onServerConnectedEvent(ServerConnectedEvent e) {
        if (!e.getServer().getServerInfo().getName().equals(Config.serverQueue))
            return;

        var queuedPlayer = new QueuedPlayer(e.getPlayer(), System.currentTimeMillis());

        if (queuedPlayers.contains(queuedPlayer))
            return;

        // Add Player to queue
        try {
            queueSemaphore.acquire();
            queuedPlayers.add(queuedPlayer);
            log.info("\u00A7f" + e.getPlayer().getUsername() + "\u00A73 was added to the §dplayer queue\u00A73. Queue count is " + queuedPlayers.size() + ".");
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        } finally {
            queueSemaphore.release();
        }
    }

    /**
     * Try to connect one player to the server
     */
    public void flushQueue() {
        // check queue server reachability
        final RegisteredServer serverQueue;

        try {
            serverQueue = getServer(Config.serverQueue);
        } catch (ServerNotReachableException e) {
            log.warn(e.getMessage());
            return;
        }

        // Ignore if queue is empty
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

        try {
            queueSemaphore.acquire();

            QueuedPlayer currPlayer = null;

            // try to find the first player that got not kicked recently
            for (QueuedPlayer testPlayer : queuedPlayers) {
                if (testPlayer.queueTime() + Config.joinDelay > System.currentTimeMillis())
                    continue;

                currPlayer = testPlayer;
                break;
            }

            if (currPlayer == null) {
                queueSemaphore.release();
                return;
            }

            // connect next player
            UUID uuid = currPlayer.player().getUniqueId();

            log.info("Processing " + uuid.toString());

            // lookup player from queue server and ping to be safe the player is connected
            QueuedPlayer finalCurrPlayer = currPlayer;

            serverQueue.getPlayersConnected().stream()
                    .filter(p -> p.getUniqueId().equals(uuid))
                    .findAny().ifPresentOrElse(p -> {
                                p.sendMessage(Identity.nil(), Component.text(Config.messageConnecting));
                                try {
                                    if (p.createConnectionRequest(serverMain).connect().get().isSuccessful()) {
                                        queuedPlayers.removeFirst();
                                        log.info("\u00A7f" + p.getUsername() + "\u00A73 connected to server §b" + serverMain.getServerInfo().getName() + "\u00A73. Queue count is " + serverQueue.getPlayersConnected().size() + ". Main count is " + (serverMain.getPlayersConnected().size()) + " of " + Config.maxPlayers + ".");
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    log.error("\u00A7f" + p.getUsername() + "s\u00A7c connection to server §b" + Config.serverMain + "\u00A7c failed: " + e.getMessage());
                                    // FIXME: requeue
                                    queuedPlayers.removeFirst();
                                    queuedPlayers.add(new QueuedPlayer(finalCurrPlayer.player(), System.currentTimeMillis()));
                                }
                            },
                            () -> {
                                log.error("\u00A7f" + finalCurrPlayer.player().getUsername() + "s\u00A7c connection to server §b" + Config.serverMain + "\u00A7c failed: player is not connected to " + serverQueue.getServerInfo().getName());
                                queuedPlayers.removeFirst();
                            }
                    );
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            queueSemaphore.release();
        }
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
