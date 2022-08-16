package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.time.Instant;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Queue implements Listener {
    private final Deque<ProxiedPlayer> playersQueue;
    private final HashMap<ProxiedPlayer, Long> kickedPlayers = new HashMap<>();
    private final Semaphore mutex = new Semaphore(1);

    /**
     * Initializes a queue
     */
    public Queue() {
        playersQueue = new LinkedList<>();

        Main.getInstance().getProxy().getScheduler().schedule(Main.getInstance(), () -> {
            Set<Entry<ProxiedPlayer, Long>> entrySet = kickedPlayers.entrySet();
            entrySet.removeIf(pair -> pair.getValue() + Config.waitOnKick < Instant.now().getEpochSecond());
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * check if a specific player is not connected to a server
     *
     * @param player the player whose uuid is checked against the players on the server
     * @param server the server that should be checked if the player is online
     * @return true if the player is not connected
     */
    private static boolean isNotConnected(ProxiedPlayer player, ServerInfo server) {
        for (ProxiedPlayer serverPlayer : server.getPlayers()) {
            if (player.getUniqueId().equals(serverPlayer.getUniqueId())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Try to connect one player to the server
     */
    public void flushQueue() {
        // Ignore if queue is empty
        if (playersQueue.isEmpty())
            return;

        // Get status of target server
        ServerInfoGetter mainServerInfo = ServerInfoGetter.awaitServerInfo(Config.target);

        // Allow player to join the main server - 1s (ping timeout) + ~500ms (connection time) < 2s (interval)
        if (mainServerInfo.isOnline && mainServerInfo.playerCount < Config.maxPlayers && Math.min(Config.maxPlayers - mainServerInfo.playerCount, playersQueue.size()) > 0) {
            try {
                mutex.acquire();

                ProxiedPlayer player = null;

                // try to find the first player that got not kicked recently
                for (ProxiedPlayer testPlayer : playersQueue) {
                    if (kickedPlayers.containsKey(testPlayer)) continue;

                    player = testPlayer;
                    break;
                }

                // if no player was found return
                if (player == null) return;

                playersQueue.remove(player);

                if (player.isConnected()) {
                    ProxiedPlayer finalPlayer = player;
                    ServerInfo targetServer = ProxyServer.getInstance().getServerInfo(Config.target);

                    if (isNotConnected(player, targetServer)) {
                        Callback<Boolean> cb = (result, error) -> {
                            if (result) {
                                Main.log("flushQueue", "§3§b" + finalPlayer + "§3 connected to §b" + Config.target + "§3. Queue count is " + playersQueue.size() + ". Main count is " + (mainServerInfo.playerCount + 1) + " of " + Config.maxPlayers + ".");
                            } else {
                                Main.log("flushQueue", "§c§b" + finalPlayer + "s§c connection to §b" + Config.target + "§c failed: " + error.getMessage());
                                finalPlayer.sendMessage(TextComponent.fromLegacyText("§cConnection to " + Config.serverName + " failed!§r"));
                                playersQueue.add(finalPlayer);
                            }
                        };

                        player.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', Config.messageConnecting) + "§r"));
                        player.connect(targetServer, cb);
                    } else {
                        player.disconnect(TextComponent.fromLegacyText("§cYou are already connected to " + Config.serverName + "!"));
                        Main.log("flushQueue", "§c§b" + player + "§c was disconnected because there was already a connection for this account to the server.");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    /**
     * Tells players their queue position
     */
    public void sendUpdate() {
        int i = 1;

        Deque<ProxiedPlayer> removePlayers = new LinkedList<>();

        for (ProxiedPlayer player : playersQueue) {
            Server playerServer = player.getServer();

            // Player is not connected OR Player Server is not the Queue Server
            if (!player.isConnected() || !playerServer.getInfo().getName().equals(Config.queue)) {
                removePlayers.add(player);
                continue;
            }

            player.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', Config.messagePosition.replaceAll("%position%", Integer.toString(i))) + "§r"));

            i++;
        }

        for (ProxiedPlayer player : removePlayers) {
            try {
                // Remove Player from the queue
                mutex.acquire();
                playersQueue.remove(player);

                Main.log("sendUpdate", "§3§b" + player.toString() + "§3 was removed from §b" + Config.queue + "§3 (wrong server or disconnected). Queue count is " + playersQueue.size() + ".");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    /**
     * This event is called once a connection to a server is fully operational.
     * Add a Player to the queue if they join the queue server.
     */
    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getServer().getInfo().getName().equals(Config.queue)) {
            if (!playersQueue.contains(event.getPlayer())) {
                // Add Player to queue
                try {
                    mutex.acquire();
                    playersQueue.add(event.getPlayer());
                    Main.log("onServerConnected", "§3§b" + event.getPlayer().toString() + "§3 was added to §b" + Config.queue + "§3. Queue count is " + playersQueue.size() + ".");
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                } finally {
                    mutex.release();
                }
            }
        }
    }

    /**
     * Removes a Player from the queue if they were in it.
     */
    @EventHandler
    public void onServerDisconnect(ServerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (playersQueue.contains(player)) {
            Server playerNewServer = player.getServer();

            if (playerNewServer == null || !playerNewServer.getInfo().getName().equals(Config.queue)) {
                // Remove Player from queue
                try {
                    mutex.acquire();
                    playersQueue.remove(player);
                    Main.log("onServerDisconnect", "§3§b" + player + "§3 was removed from §b" + Config.queue + "§3. Queue count is " + playersQueue.size() + ".");
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                } finally {
                    mutex.release();
                }
            }
        }
    }

    /**
     * Server went down or player got kicked
     */
    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        // check if it's a "fresh" connection
        if (event.getPlayer().getServer() == null)
            return;

        if (event.getKickedFrom().getName().equals(Config.target)) {
            // Move Player back to the queue
            try {
                mutex.acquire();
                String reason = BaseComponent.toLegacyText(event.getKickReasonComponent());
                ProxiedPlayer player = event.getPlayer();

                // lost connection -> disconnected
                if (reason.toLowerCase().contains("banned")) {
                    // if banned don't add back to the queue;
                    player.disconnect(event.getKickReasonComponent());
                } else if (!reason.toLowerCase().contains("lost connection")) {
                    // add to the queue again, since the player did not leave the queue and the right event was not triggered
                    if (event.getPlayer().getServer().getInfo() == ProxyServer.getInstance().getServerInfo(Config.queue)) {
                        playersQueue.add(player);
                        Main.log("onServerKick", "§3§b" + player.toString() + "§3 was added to the §b" + Config.queue + "§3. Queue count is " + playersQueue.size() + ". Kicked count is " + (kickedPlayers.size() + 1) + ".");
                    }

                    // if config is not set to kick, if the player was kicked while connecting to main or main is restarting
                    if (!Config.kick || event.getPlayer().getServer().getInfo() == ProxyServer.getInstance().getServerInfo(Config.queue)
                            || (!Config.kickOnRestart && reason.toLowerCase().contains("server is restarting"))
                            || (!Config.kickOnTooMany && reason.toLowerCase().contains("too many people logging in"))) {
                        // cancel kick and send back to the queue
                        event.setCancelled(true);
                        event.setCancelServer(ProxyServer.getInstance().getServerInfo(Config.queue));

                        player.sendMessage(TextComponent.fromLegacyText("§6You were sent back to the queue for: §c" + reason + "§r"));
                        Main.log("onServerKick", "§3§b" + player + "§3 was sent back to §b" + Config.queue + "§3 after a kick (" + reason + "§3). Kicked count is " + (kickedPlayers.size() + 1) + ".");

                        kickedPlayers.put(player, Instant.now().getEpochSecond());
                    } else {
                        // kick the player if the event was not cancelled
                        Main.log("onServerKick", "§3§b" + player.toString() + "§3 was kicked for " + reason + "§3.");
                    }
                }
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    /**
     * Called when deciding to connect to a server. At the time when this event is called, no connection has actually been made. Cancelling the event will ensure that the connection does not proceed and can be useful to prevent certain players from accessing certain servers.
     */
    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // check if it's a "fresh" connection
        if (player.getServer() != null)
            return;

        // Direct connect to main server
        if (playersQueue.size() == 0 && event.getTarget().equals(ProxyServer.getInstance().getServerInfo(Config.queue))) {
            // Get status of target server
            // TODO: make this check global with and time updates
            ServerInfoGetter mainServerInfo = ServerInfoGetter.awaitServerInfo(Config.target);

            // main server is online and player count is lower then max
            if (mainServerInfo.isOnline && mainServerInfo.playerCount < Config.maxPlayers) {
                ServerInfo targetServer = ProxyServer.getInstance().getServerInfo(Config.target);

                if (isNotConnected(player, targetServer)) {
                    // direct connection
                    event.setTarget(targetServer);
                    Main.log("onServerConnect", "§3§b" + event.getPlayer() + "§3 was directly connected to §b" + Config.target + "§3. Main count is " + (mainServerInfo.playerCount + 1) + " of " + Config.maxPlayers + ".");
                } else {
                    player.disconnect(TextComponent.fromLegacyText("§cYou are already connected to " + Config.serverName + "!"));
                    Main.log("onServerConnect", "§c§b" + player + "§c was disconnected because there was already a connection for this account to the server.");
                }
            } else {
                // Send full message
                event.getPlayer().sendMessage(TextComponent.fromLegacyText("§6" + Config.serverName + " is full or offline§r"));
            }
        }
    }

    /**
     * Called when a player has left the proxy, it is not safe to call any methods
     * that perform an action on the passed player instance.
     */
    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        if (playersQueue.contains(event.getPlayer())) {
            // Remove Player from queue
            try {
                mutex.acquire();
                Main.log("onPlayerDisconnect", "§3§b" + event.getPlayer() + "§3 was removed from §b" + Config.queue + "§3. Queue count is " + playersQueue.size() + ".");
                playersQueue.remove(event.getPlayer());
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    /**
     * Test a Server Connection
     */
    private static class ServerInfoGetter implements Callback<ServerPing> {
        public boolean done = false;
        public int playerCount = Integer.MAX_VALUE;
        public boolean isOnline = false;

        public static ServerInfoGetter awaitServerInfo(String name) {
            ServerInfoGetter sig = new ServerInfoGetter();
            ProxyServer.getInstance().getServerInfo(name).ping(sig);

            int slept = 0;

            // Timeout
            try {
                while (!sig.done && slept < 1000) {
                    //noinspection BusyWait
                    Thread.sleep(10);
                    slept += 10;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return sig;
        }

        @Override
        public void done(ServerPing serverPing, Throwable throwable) {
            if (throwable != null) {
                // we had an error, so we assume the server is offline
                return;
            }

            isOnline = true;
            playerCount = serverPing.getPlayers().getOnline();
            done = true;
        }
    }
}