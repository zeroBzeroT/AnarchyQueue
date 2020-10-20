package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Collection;
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

        // Get players of target server
        Collection<ProxiedPlayer> playersMain = ProxyServer.getInstance().getServerInfo(config.target).getPlayers();

        ServerInfoGetter sig = new ServerInfoGetter();
        //ProxyServer.getInstance().getServerInfo(config.target).ping(sig);
        ProxyServer.getInstance().getServers().get(config.target).ping(sig);

        int slept = 0;

        try {
            while (!sig.done && slept < 10000) {
                //noinspection BusyWait
                Thread.sleep(100);
                slept += 100;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        Main.log("flushQueue", "Players waiting: " + playersQueue.size() + " Player on the server: " + playersMain.size() + " " + sig.players + " " + sig.online);

        if (sig.online && playersMain.size() < config.maxPlayers) {
            // Allow players onto the server
            int allowance = Math.min(config.maxPlayers - playersMain.size(), playersQueue.size());

            for (int i = 0; i < allowance; i++) {
                try {
                    mutex.acquire();
                    ProxiedPlayer player = playersQueue.getFirst();

                    Callback<Boolean> cb = (result, error) -> {
                        if (result) {
                            Main.log("flushQueue", "§3Connected to target: " + player.toString() + " Waiting: " + playersQueue.size() + " Server: " + playersMain.size());
                            playersQueue.remove(player);
                        } else {
                            Main.log("flushQueue", "§3Connection to target failed (ban?): " + player.toString() + " " + playersQueue.size() + " " + error.getMessage());
                            player.sendMessage(TextComponent.fromLegacyText("§cConnection to " + config.target + " failed!"));
                            playersQueue.remove(player);
                            playersQueue.add(player);
                        }
                    };

                    player.connect(ProxyServer.getInstance().getServerInfo(config.target), cb);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    mutex.release();
                }
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

            // Player is not connected OR Player Server is not connected OR Player Server is
            // not the Queue Server
            if (!player.isConnected() || !playerServer.getInfo().getName().equals(config.queue)) {
                removePlayers.add(player);
                continue;
            }

            player.sendMessage(
                    TextComponent.fromLegacyText("§6" + config.message.replaceAll("%position%", Integer.toString(i)) + "§r"));

            i++;
        }

        for (ProxiedPlayer player : removePlayers) {
            try {
                mutex.acquire();
                playersQueue.remove(player);

                Main.log("sendUpdate", "§3Removed from queue since on the wrong server or disconnected: " + player.toString() + " " + playersQueue.size());
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
    public void onServerConnected(ServerConnectedEvent e) {
        if (e.getServer().getInfo().getName().equals(config.queue)) {
            // Add Player to queue
            if (!playersQueue.contains(e.getPlayer())) {
                try {
                    mutex.acquire();
                    playersQueue.add(e.getPlayer());
                    Main.log("onServerConnect", "§3Added to queue: " + e.getPlayer().toString() + " Waiting: " + playersQueue.size());

                    e.getPlayer().sendMessage(TextComponent.fromLegacyText("§6" + config.message.replaceAll("%position%", Integer.toString(playersQueue.size())) + "§r"));
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
    public void onServerDisconnect(ServerDisconnectEvent e) {
        ProxiedPlayer player = e.getPlayer();
        Server playerNewServer = player.getServer();

        if (playerNewServer != null && !playerNewServer.getInfo().getName().equals(config.queue)) {
            // Remove Player from queue
            try {
                mutex.acquire();
                playersQueue.remove(player);
                Main.log("onLeave", "§3Removed from queue: " + player.toString() + " Waiting: " + playersQueue.size());
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    /**
     * Called when deciding to connect to a server.
     */
    @EventHandler
    public void onConnectEvent(ServerConnectEvent e) {
        Main.log("onConnectEvent", e.getPlayer().getName() + " deciding to connect to " + e.getTarget().getName() + ".");
    }

    /**
     * Called when a player has changed servers.
     */
    @EventHandler
    public void onServerSwitch(ServerSwitchEvent e) {
        Main.log("onServerSwitch", e.toString());

        //Main.log("onServerSwitch", e.getPlayer().getName() + " switched from " + e.getFrom() == null ? "null" : e.getFrom().getName() + ".");
    }

    /**
     * Server went down or player got kicked
     */
    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        Main.log("onKickedFromServer", event.getPlayer().getName() + " kicked from " + event.getKickedFrom().getName() + ".");

        if (event.getKickedFrom().getName().equals(config.target)) {
            // Move Player back to the queue
            try {
                mutex.acquire();

                ProxiedPlayer player = event.getPlayer();

                event.setCancelled(true);
                event.setCancelServer(ProxyServer.getInstance().getServerInfo(config.queue));

                if (!playersQueue.contains(player)) {
                    playersQueue.add(player);
                }

                player.sendMessage(TextComponent.fromLegacyText("§6You have been send back to the queue."));
                Main.log("onKickedFromServer", "§3Added to queue after kick: " + player.toString() + " Waiting: " + playersQueue.size());
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    private static class ServerInfoGetter implements Callback<ServerPing> {
        public boolean done = false;
        public int players = Integer.MAX_VALUE;
        public boolean online = false;

        @Override
        public void done(ServerPing serverPing, Throwable throwable) {
            if (throwable != null) {
                // we had an error so we assume the server is offline
                return;
            }

            online = true;
            players = serverPing.getPlayers().getOnline();
            done = true;
        }
    }

//	/**
//	 * Test a Server Connection
//	 */
//	private boolean isReachable(InetSocketAddress address) {
//		Socket socket = new Socket();
//		try {
//			socket.connect(address, 500);
//			socket.close();
//			return true;
//		} catch (IOException localIOException) {
//		}
//		return false;
//	}
}