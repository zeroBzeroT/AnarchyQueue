package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import static org.zeroBzeroT.anarchyqueue.Components.mm;

// velocity api event docs:
// https://jd.papermc.io/velocity/3.3.0/com/velocitypowered/api/event/package-summary.html

public class Queue {
    private final ComponentLogger log;

    private final ProxyServer proxyServer;

    private final Semaphore queueSemaphore = new Semaphore(1);

    private final Deque<QueuedPlayer> queuedPlayers = new LinkedList<>();

    /**
     * Initializes the queue.
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
     * This event is fired once the player has successfully connected to the
     * target server and the connection to the previous server has been de-established.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (!event.getServer().getServerInfo().getName().equals(Config.serverQueue))
            return;

        // stop adding the player several times
        if (queuedPlayers.stream().anyMatch(p -> p.player() == event.getPlayer()))
            return;

        // Add Player to queue
        try {
            queueSemaphore.acquire();
            queuedPlayers.add(new QueuedPlayer(event.getPlayer(), System.currentTimeMillis()));
            log.info(mm("<white>" + event.getPlayer().getUsername() + "<dark_aqua> was added to the <light_purple>queue<dark_aqua>. Queue count is " + queuedPlayers.size() + "."));
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        } finally {
            queueSemaphore.release();
        }
    }

    /**
     * Fired when a player is kicked from a server.
     */
    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        log.info(mm("<white>" + event.getPlayer().getUsername() + "<dark_aqua> was kicked from <light_purple>" + event.getServer().getServerInfo().getName() + "<dark_aqua> for <light_purple>").append(event.getServerKickReason().isPresent() ? event.getServerKickReason().get() : mm("<empty>")).append(mm("<dark_aqua>.")));
    }

    /**
     * Try to connect one player to the server.
     */
    public void flushQueue() {
        // Ignore if queue is empty
        if (queuedPlayers.isEmpty())
            return;

        // check queue server reachability
        final RegisteredServer serverQueue;

        try {
            serverQueue = getServer(Config.serverQueue);
        } catch (ServerNotReachableException e) {
            log.warn(e.getMessage());
            return;
        }

        // check the main server reachability
        final RegisteredServer serverMain;

        try {
            serverMain = getServer(Config.serverMain);
        } catch (ServerNotReachableException e) {
            if (Instant.now().getEpochSecond() % 10 == 0) {
                serverQueue.getPlayersConnected().forEach(queuedPlayer -> queuedPlayer.sendMessage(Component.text(Config.messageOffline)));
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

            // lookup player from queue server and ping to be safe the player is connected
            QueuedPlayer finalCurrPlayer = currPlayer;

            serverQueue.getPlayersConnected().stream()
                    .filter(p -> p.getUniqueId().equals(uuid))
                    .findAny().ifPresentOrElse(p -> {
                                p.sendMessage(Component.text(Config.messageConnecting));
                                try {
                                    if (p.createConnectionRequest(serverMain).connect().get().isSuccessful()) {
                                        queuedPlayers.removeFirst();
                                        log.info(mm("<white>" + p.getUsername() + "<dark_aqua> connected to server <aqua>" + serverMain.getServerInfo().getName() + "<dark_aqua>. Queue count is " + serverQueue.getPlayersConnected().size() + ". Main count is " + (serverMain.getPlayersConnected().size()) + " of " + Config.maxPlayers + "."));
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    log.error(mm("<white>" + p.getUsername() + "s<red> connection to server <aqua>" + Config.serverMain + "<red> failed: " + e.getMessage()));
                                    // FIXME: requeue
                                    queuedPlayers.removeFirst();
                                    queuedPlayers.add(new QueuedPlayer(finalCurrPlayer.player(), System.currentTimeMillis()));
                                }
                            },
                            () -> {
                                log.error(mm("<white>" + finalCurrPlayer.player().getUsername() + "s<red> connection to server <aqua>" + Config.serverMain + "<red> failed: player is not connected to " + serverQueue.getServerInfo().getName()));
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
        int i = 1;

        for (QueuedPlayer player : queuedPlayers) {
            player.player().sendMessage(Component.text(Config.messagePosition + (i + 1) + "/" + queuedPlayers.size()));
            i++;
        }

        if (full) {
            serverQueue.getPlayersConnected().forEach(queuedPlayer -> queuedPlayer.sendMessage(Component.text(Config.messageFull)));
        }
    }

    private RegisteredServer getServer(String name) throws ServerNotReachableException {
        // Get server configured in velocity.toml by name
        Optional<RegisteredServer> serverOpt = proxyServer.getServer(name);

        if (serverOpt.isEmpty()) {
            throw new ServerNotReachableException("Server " + name + " is not configured!");
        }

        final RegisteredServer server = serverOpt.get();

        // Test server availability by pinging
        try {
            server.ping().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ServerNotReachableException("Server " + name + " is not reachable: " + e.getMessage());
        }

        return server;
    }
}
