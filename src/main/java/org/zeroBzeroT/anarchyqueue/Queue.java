package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import static org.zeroBzeroT.anarchyqueue.Components.mm;

// velocity api event docs:
// https://jd.papermc.io/velocity/3.3.0/com/velocitypowered/api/event/package-summary.html
// TODO: direct connection to the main server if the queue is empty
// TODO: replace player objects with uuids

public class Queue {
    private final ComponentLogger log;

    private final ProxyServer proxyServer;

    private final ReentrantLock lock = new ReentrantLock();

    private final Deque<Player> playerQueue;

    private final HashMap<Player, Long> kickedPlayers;

    /**
     * Initializes the queue.
     */
    public Queue(ProxyServer proxyServer) {
        this.log = Main.getInstance().log;
        this.proxyServer = proxyServer;
        playerQueue = new LinkedList<>();
        kickedPlayers = new HashMap<>();

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

        // Schedule kicked players cool down
        proxyServer.getScheduler()
                .buildTask(Main.getInstance(), () -> kickedPlayers.entrySet().removeIf(pair -> pair.getValue() + Config.waitOnKick < Instant.now().getEpochSecond()))
                .delay(Duration.ofSeconds(0))
                .repeat(Duration.ofSeconds(1))
                .schedule();
    }

    /**
     * This event is fired once the player has successfully connected to the
     * target server and the connection to the previous server has been de-established.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (!event.getServer().getServerInfo().getName().equals(Config.queue))
            return;

        // stop adding the player several times
        if (playerQueue.contains(event.getPlayer()))
            return;

        // Add Player to queue
        lock.lock();
        try {
            playerQueue.add(event.getPlayer());
            log.info(mm("<white>" + event.getPlayer().getUsername() + "<dark_aqua> was added to the <light_purple>queue<dark_aqua>. Queue count is " + playerQueue.size() + "."));
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fired when a player is kicked from a server.
     */
    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        if (event.getServer().getServerInfo().getName().equals(Config.target)) {
            // kick from target server
            lock.lock();
            try {
                Player player = event.getPlayer();
                Component reason = event.getServerKickReason().isPresent() ? event.getServerKickReason().get() : mm("Kicked without a reason.");

                KickOrRequeue(player, reason);
            } catch (Exception e) {
                log.error(e.getMessage());
            } finally {
                lock.unlock();
            }
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        lock.lock();
        try {
            Player player = event.getPlayer();
            if (playerQueue.contains(player)) {
                playerQueue.remove(player);
                kickedPlayers.remove(player);
                log.info(mm("<white>" + player.getUsername() + "<dark_aqua> disconnected and was removed from the <light_purple>queue<dark_aqua>."));
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * Kick a player if kicking is allowed in the config
     *
     * @param player Player to kick
     * @param reason kicking reason from the target server
     */
    private void KickOrRequeue(Player player, Component reason) {
        // is kicking  enabled?
        if (!Config.kick) {
            // save the disconnection time
            kickedPlayers.put(player, Instant.now().getEpochSecond());

            // send message
            player.sendMessage(mm("<gold>You were sent back to the queue for: <red>").append(reason).append(mm("<reset>")));
            log.info(mm("<white>" + player.getUsername() + "<dark_aqua> was sent back to server <yellow>" + Config.queue + "<dark_aqua> after a disconnection (\"").append(reason).append(mm("<dark_aqua>\"). Kicked count is " + kickedPlayers.size() + ".")));
        } else {
            playerQueue.remove(player);

            // set the disconnect reason from the parameter (not the velocity message)
            player.disconnect(reason);
            log.info(mm("<white>" + player.getUsername() + "<dark_aqua> was kicked from the server (\"").append(reason).append(mm("<dark_aqua>\"). Queue count is " + playerQueue.size() + ".")));
        }
    }

    /**
     * Try to connect one player to the server.
     */
    public void flushQueue() {
        // Ignore if queue is empty
        if (playerQueue.isEmpty())
            return;

        // check queue server reachability
        final RegisteredServer serverQueue;

        try {
            serverQueue = getServer(Config.queue);
        } catch (ServerNotReachableException e) {
            log.warn(e.getMessage());
            return;
        }

        // check the main server reachability
        final RegisteredServer serverMain;

        try {
            serverMain = getServer(Config.target);
        } catch (ServerNotReachableException e) {
            // TODO: offline notification
            return;
        }

        // check main server full
        if (serverMain.getPlayersConnected().size() >= Config.maxPlayers)
            // TODO: full notification
            return;

        lock.lock();
        try {
            Player currPlayer = null;

            // try to find the first player that got not kicked recently
            for (Player testPlayer : playerQueue) {
                if (kickedPlayers.containsKey(testPlayer))
                    continue;

                currPlayer = testPlayer;
                break;
            }

            // no player to connect to the main server
            if (currPlayer != null) {

                // connect next player
                UUID uuid = currPlayer.getUniqueId();
                Player finalCurrPlayer = currPlayer;

                serverQueue.getPlayersConnected().stream()
                        .filter(p -> p.getUniqueId().equals(uuid))
                        .findAny().ifPresentOrElse(p -> {
                                    p.sendMessage(mm(Config.messageConnecting));
                                    try {
                                        var conReq = p.createConnectionRequest(serverMain).connect().get();

                                        if (conReq.isSuccessful()) {
                                            playerQueue.removeFirst();
                                            // Clear the title
                                            if (Config.sendTitle) {
                                                sendTitle(finalCurrPlayer, mm(" "), mm(" "), 0, 0, 0);
                                            }
                                            log.info(mm("<white>" + p.getUsername() + "<dark_aqua> connected to server <aqua>" + serverMain.getServerInfo().getName() + "<dark_aqua>. Queue count is " + serverQueue.getPlayersConnected().size() + ". Main count is " + (serverMain.getPlayersConnected().size()) + " of " + Config.maxPlayers + "."));
                                        } else {
                                            var reason = conReq.getReasonComponent().isPresent() ? conReq.getReasonComponent().get() : mm("Connection to the main server failed.");
                                            KickOrRequeue(finalCurrPlayer, reason);
                                        }
                                    } catch (InterruptedException | ExecutionException e) {
                                        log.error(mm("<white>" + p.getUsername() + "s<red> connection to server <aqua>" + Config.target + "<red> failed with an exception: " + e.getMessage()));
                                        // server down?
                                        kickedPlayers.put(finalCurrPlayer, Instant.now().getEpochSecond());
                                    }
                                },
                                // player is in the queue, but not connected to the queue server
                                playerQueue::removeFirst
                        );
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tells players their queue position
     */
    public void sendUpdate() {
        int i = 1;

        for (Player player : playerQueue) {
            player.sendMessage(mm(Config.messagePosition.replaceAll("%position%", Integer.toString(i)).replaceAll("%size%", Integer.toString(playerQueue.size()))));

            // Title in the center of the screen
            if (Config.sendTitle) {
                // 10 Seconds + 1 s buffer
                sendTitle(player, mm(" "), mm(Config.messagePosition.replaceAll("%position%", Integer.toString(i)) + "<reset>"), 0, 20 * 11, 0);
            }

            i++;
        }
    }

    /**
     * Test a server connection and return the server object.
     */
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

    /**
     * Sends a title and a subtitle message to the player.
     *
     * @param title    Title text
     * @param subtitle Subtitle text
     * @param fadeIn   Time in ticks for titles to fade in. Defaults to 10.
     * @param stay     Time in ticks for titles to stay. Defaults to 70.
     * @param fadeOut  Time in ticks for titles to fade out. Defaults to 20.
     */
    public void sendTitle(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Title bungeeTitle = Title.title(
                    title,
                    subtitle,
                    Title.Times.times(Duration.ofMillis(fadeIn * 20L),
                            Duration.ofMillis(stay * 20L),
                            Duration.ofMillis(fadeOut * 20L)));
            player.showTitle(bungeeTitle);
        } catch (Exception e) {
            log.error(mm("<red>Could not send title to <white>" + player.getUsername() + "<red>: " + e.getMessage()));
        }
    }
}
