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
    private final Deque<ProxiedPlayer> playerQueue;
    private final HashMap<ProxiedPlayer, Long> kickedPlayers = new HashMap<>();
    private final Semaphore mutex = new Semaphore(1);

    /**
     * Initializes a queue
     */
    public Queue() {
        playerQueue = new LinkedList<>();

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
        if (playerQueue.isEmpty())
            return;

        // Get status of target server
        ServerInfoGetter mainServerInfo = ServerInfoGetter.awaitServerInfo(Config.target);

        // Allow player to join the main server - 1s (ping timeout) + ~500ms (connection time) < 2s (interval)
        if (mainServerInfo.isOnline && mainServerInfo.playerCount < Config.maxPlayers && Math.min(Config.maxPlayers - mainServerInfo.playerCount, playerQueue.size()) > 0) {
            try {
                mutex.acquire();

                ProxiedPlayer player = null;

                // try to find the first player that got not kicked recently
                for (ProxiedPlayer testPlayer : playerQueue) {
                    if (kickedPlayers.containsKey(testPlayer))
                        continue;

                    player = testPlayer;
                    break;
                }

                // if no player was found return
                if (player == null) return;

                playerQueue.remove(player);

                if (player.isConnected()) {
                    ProxiedPlayer finalPlayer = player;
                    ServerInfo targetServer = ProxyServer.getInstance().getServerInfo(Config.target);

                    if (isNotConnected(player, targetServer)) {
                        Callback<Boolean> cb = (result, error) -> {
                            if (result) {
                                Main.log("queue", "§3§f" + finalPlayer.getName() + "§3 connected to server §b" + Config.target + "§3. Queue count is " + playerQueue.size() + ". Main count is " + (mainServerInfo.playerCount + 1) + " of " + Config.maxPlayers + ".");
                            } else {
                                Main.log("queue", "§c§f" + finalPlayer.getName() + "s§c connection to server §b" + Config.target + "§c failed: " + error.getMessage());
                                finalPlayer.sendMessage(TextComponent.fromLegacyText("§cConnection to " + Config.serverName + " failed!§r"));
                                playerQueue.add(finalPlayer);
                            }
                        };

                        player.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', Config.messageConnecting) + "§r"));
                        player.connect(targetServer, cb);
                    } else {
                        player.disconnect(TextComponent.fromLegacyText("§cYou are already connected to " + Config.serverName + "!"));
                        Main.log("queue", "§c§f" + player.getName() + "§c was disconnected because there was already a connection for this account to the server.");
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

        for (ProxiedPlayer player : playerQueue) {
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
                playerQueue.remove(player);

                Main.log("update", "§3§f" + player.getName() + "§3 was removed from the §dplayer queue§3 (wrong server or disconnected). Queue count is " + playerQueue.size() + ".");
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
            ProxiedPlayer player = event.getPlayer();

            if (!playerQueue.contains(player)) {
                // Add Player to queue
                try {
                    mutex.acquire();
                    playerQueue.add(player);
                    Main.log("connect", "§3§f" + player.getName() + "§3 was added to the §dplayer queue§3. Queue count is " + playerQueue.size() + ".");
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

        if (playerQueue.contains(player)) {
            Server playerNewServer = player.getServer();

            if (playerNewServer == null || !playerNewServer.getInfo().getName().equals(Config.queue)) {
                // Remove Player from queue
                try {
                    mutex.acquire();
                    playerQueue.remove(player);
                    Main.log("disconnect", "§3§f" + player.getName() + "§3 was removed from the §dplayer queue§3. Queue count is " + playerQueue.size() + ".");
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                } finally {
                    mutex.release();
                }
            }
        }
    }

    /**
     * Server went down or player got kicked or just disconnected
     */
    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        if (event.getKickedFrom().getName().equals(Config.target)) {
            // kick from target server
            try {
                mutex.acquire();
                String reason = BaseComponent.toLegacyText(event.getKickReasonComponent());
                ProxiedPlayer player = event.getPlayer();

                if (!Config.kickPassthrough
                        || (!Config.kickOnRestart && reason.toLowerCase().contains("server is restarting"))
                        || (!Config.kickOnRestart && reason.toLowerCase().contains("went down"))
                        || (!Config.kickOnBusy && reason.toLowerCase().contains("too many people logging in"))
                        || (!Config.kickOnBusy && reason.toLowerCase().contains("too fast re-login"))) {
                    // cancel the event, if one of the following:
                    // - kicking is not enabled
                    // - target is restarting (and restart kicks are disabled)
                    // - target is busy with connecting players (and busy kicks are disabled)

                    // save the disconnection time
                    kickedPlayers.put(player, Instant.now().getEpochSecond());

                    // cancel kick and send back to the queue
                    event.setCancelled(true);
                    event.setCancelServer(ProxyServer.getInstance().getServerInfo(Config.queue));

                    // send message
                    player.sendMessage(TextComponent.fromLegacyText("§6You were sent back to the queue for: §c" + reason.replaceAll("\n", " ") + "§r"));
                    Main.log("kick", "§3§f" + player.getName() + "§3 was sent back to server §e" + Config.queue + "§3 after a disconnection (\"" + reason.replaceAll("\n", " ") + "§3\"). Kicked count is " + kickedPlayers.size() + ".");

                    // the player did not leave the queue, therefore the connect event is not triggered and
                    // a manual (re)adding to the player queue is required
                    if (player.getServer() != null && player.getServer().getInfo() == ProxyServer.getInstance().getServerInfo(Config.queue)) {
                        playerQueue.add(player);
                        Main.log("kick", "§3§f" + player.getName() + "§3 was added to the §dplayer queue§3. Queue count is " + playerQueue.size() + ".");
                    }
                } else {
                    // set the disconnect reason from the target server (not the bungee message)
                    player.disconnect(event.getKickReasonComponent());
                }
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    /**
     * Called when deciding to connect to a server. At the time when this event is called, no connection has actually been made.
     * Cancelling the event will ensure that the connection does not proceed and can be useful to prevent certain players from accessing certain servers.
     */
    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        // check if it's a "fresh" connection
        if (player.getServer() != null) {
            if (player.getServer().getInfo().equals(event.getTarget()))
                event.setCancelled(true);

            return;
        }

        // Direct connect to main server
        if (playerQueue.size() == 0 && event.getTarget().equals(ProxyServer.getInstance().getServerInfo(Config.queue))) {
            // Get status of target server
            // TODO: make this check global with timed updates
            ServerInfoGetter mainServerInfo = ServerInfoGetter.awaitServerInfo(Config.target);

            if (kickedPlayers.containsKey(player)) {
                // player was sent back to the queue after a kick on a fresh connection
                Main.log("connect", "§3§f" + player.getName() + "§3 was sent to server §e" + Config.queue + "§3 after a kick from server §b" + Config.target + "§3.");
            } else if (mainServerInfo.isOnline && mainServerInfo.playerCount < Config.maxPlayers) {
                // main server is online and player count is lower then max
                ServerInfo targetServer = ProxyServer.getInstance().getServerInfo(Config.target);

                if (isNotConnected(player, targetServer)) {
                    // direct connection
                    event.setTarget(targetServer);
                    Main.log("connect", "§3§f" + player.getName() + "§3 was directly connected to server §b" + Config.target + "§3. Main count is " + (mainServerInfo.playerCount + 1) + " of " + Config.maxPlayers + ".");
                } else {
                    player.disconnect(TextComponent.fromLegacyText("§cYou are already connected to " + Config.serverName + "!"));
                    Main.log("connect", "§c§f" + player.getName() + "§c was disconnected because there was already a connection for this account to the server.");
                }
            } else {
                // Send full message
                player.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', Config.messageFullOrOffline)));
            }
        }
    }

    /**
     * Called when a player has left the proxy, it is not safe to call any methods
     * that perform an action on the passed player instance.
     */
    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        if (playerQueue.contains(player)) {
            // Remove Player from queue
            try {
                mutex.acquire();
                Main.log("disconnect", "§3§f" + player.getName() + "§3 was removed from the §dplayer queue§3. Queue count is " + playerQueue.size() + ".");
                playerQueue.remove(player);
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