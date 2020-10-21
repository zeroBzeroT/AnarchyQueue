package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

public class Queue implements Listener {
    private final Config config;
    private final Deque<ProxiedPlayer> playersQueue;
    private final Semaphore mutex = new Semaphore(1);

    /**
     * Initializes a queue
     *
     * @param config config instance for the plugin
     */
    public Queue(Config config) {
        this.config = config;

        playersQueue = new LinkedList<>();
    }

    /**
     * Lets as many people as possible into the server
     */
    public void flushQueue() {
        // Ignore if queue is empty
        if (playersQueue.isEmpty())
            return;

        // Get status of target server
        ServerInfoGetter mainServerInfo = ServerInfoGetter.awaitServerInfo(config.target);

        int counter = 0;

        // Allow players to join the main server
        while (counter < 5 && mainServerInfo.isOnline && mainServerInfo.playerCount < Main.GLOBAL_SLOTS && Math.min(config.maxPlayers - mainServerInfo.playerCount, playersQueue.size()) > 0) {
            try {
                mutex.acquire();

                ProxiedPlayer player = playersQueue.getFirst();
                playersQueue.remove(player);

                if (!player.isConnected()) {
                    continue;
                }

                Callback<Boolean> cb = (result, error) -> {
                    if (result) {
                        Main.log("flushQueue", "§3" + player.toString() + " connected to " + config.target + ". Queue count is " + playersQueue.size() + ". Main count is " + (ProxyServer.getInstance().getServerInfo(config.target).getPlayers().size() + 1) + " of " + Main.GLOBAL_SLOTS + ".");
                    } else {
                        Main.log("flushQueue", "§c" + player.toString() + "s connection to " + config.target + " failed: " + error.getMessage());
                        player.sendMessage(TextComponent.fromLegacyText("§cConnection to " + config.serverName + " failed!§r"));
                        playersQueue.add(player);
                    }
                };

                player.sendMessage(TextComponent.fromLegacyText("§6" + config.messageConnecting + "§r"));

                player.connect(ProxyServer.getInstance().getServerInfo(config.target), cb);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mutex.release();
            }

            // Sleep a bit to prevent "Too many players are trying to connect" bug
            try {
                //noinspection BusyWait
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Update status of target server
            mainServerInfo = ServerInfoGetter.awaitServerInfo(config.target);

            counter++;
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
            if (!player.isConnected() || !playerServer.getInfo().getName().equals(config.queue)) {
                removePlayers.add(player);
                continue;
            }

            player.sendMessage(TextComponent.fromLegacyText("§6" + config.messagePosition.replaceAll("%position%", Integer.toString(i)) + "§r"));

            i++;
        }

        for (ProxiedPlayer player : removePlayers) {
            try {
                // Remove Player from the queue
                mutex.acquire();
                playersQueue.remove(player);

                Main.log("sendUpdate", "§3" + player.toString() + " was removed from " + config.queue + " (wrong server or disconnected). Queue count is " + playersQueue.size() + ".");
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
        if (event.getServer().getInfo().getName().equals(config.queue)) {
            if (!playersQueue.contains(event.getPlayer())) {
                // Add Player to queue
                try {
                    mutex.acquire();
                    playersQueue.add(event.getPlayer());
                    Main.log("onServerConnected", "§3" + event.getPlayer().toString() + " was added to queue. Queue count is " + playersQueue.size() + ".");
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

            if (playerNewServer == null || !playerNewServer.getInfo().getName().equals(config.queue)) {
                // Remove Player from queue
                try {
                    mutex.acquire();
                    playersQueue.remove(player);
                    Main.log("onServerDisconnect", "§3" + player.toString() + " was removed from queue. Queue count is " + playersQueue.size() + ".");
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
        // check if its a "fresh" connection
        if (event.getPlayer().getServer() == null)
            return;

        if (event.getKickedFrom().getName().equals(config.target)) {
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
                    // add to the queue again since the player did not left the queue and the event is not triggered
                    if (event.getPlayer().getServer().getInfo() == ProxyServer.getInstance().getServerInfo(config.queue)) {
                        playersQueue.add(player);
                        Main.log("onServerKick", "§3" + player.toString() + " was added to the queue. Queue count is " + playersQueue.size() + ".");
                    }

                    // cancel kick and send back to the queue
                    event.setCancelled(true);
                    event.setCancelServer(ProxyServer.getInstance().getServerInfo(config.queue));

                    player.sendMessage(TextComponent.fromLegacyText("§6You were sent back to the queue for: §c" + reason + "§r"));
                    Main.log("onServerKick", "§3" + player.toString() + " was sent back to the queue after a kick (" + reason + "§3).");
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
        // check if its a "fresh" connection
        if (event.getPlayer().getServer() != null)
            return;

        // Direct connect to main server
        if (playersQueue.size() == 0 && event.getTarget().equals(ProxyServer.getInstance().getServerInfo(config.queue))) {
            // Get status of target server
            ServerInfoGetter mainServerInfo = ServerInfoGetter.awaitServerInfo(config.target);

            // main server is online and player count is lower then max
            if (mainServerInfo.isOnline && mainServerInfo.playerCount < Main.GLOBAL_SLOTS) {
                // direct connection
                event.setTarget(ProxyServer.getInstance().getServerInfo(config.target));

                Main.log("onServerConnect", "§3" + event.getPlayer() + " was directly connected to " + config.target + ".");
            } else {
                // Send full message
                event.getPlayer().sendMessage(TextComponent.fromLegacyText("§6" + config.serverName + " is full or offline§r"));
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
                Main.log("onPlayerDisconnect", "§3" + event.getPlayer() + " was removed from queue. Queue count is " + playersQueue.size() + ".");
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

            try {
                while (!sig.done && slept < 10000) {
                    //noinspection BusyWait
                    Thread.sleep(100);
                    slept += 100;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return sig;
        }

        @Override
        public void done(ServerPing serverPing, Throwable throwable) {
            if (throwable != null) {
                // we had an error so we assume the server is offline
                return;
            }

            isOnline = true;
            playerCount = serverPing.getPlayers().getOnline();
            done = true;
        }
    }
}