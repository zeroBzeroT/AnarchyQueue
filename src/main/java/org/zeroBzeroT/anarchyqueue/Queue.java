package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

public class Queue implements Listener {
    private final Config config;
    private final Deque<ProxiedPlayer> players;
    private final Semaphore mutex = new Semaphore(1);

    /**
     * Initializes a queue
     *
     * @param config config instance for the plugin
     */
    public Queue(Config config) {
        this.config = config;

        players = new LinkedList<>();
    }

    /**
     * Lets as many people as possible into the server
     */
    public void flushQueue() {
        // Ignore if queue is empty
        if (players.isEmpty())
            return;

        // Get status of target server
        ServerInfo targetServer = ProxyServer.getInstance().getServerInfo(config.target);

        ServerInfoGetter sig = new ServerInfoGetter();
        ProxyServer.getInstance().getServerInfo(config.target).ping(sig);

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

        if (sig.players < config.maxPlayers) {
            // Allow players onto the server
            int allowance = Math.min(config.maxPlayers - sig.players, players.size());

            for (int i = 0; i < allowance; i++) {
                try {
                    mutex.acquire();
                    ProxiedPlayer p = players.getFirst();

                    Callback<Boolean> cb = (result, error) -> {
                        if (result) {
                            Main.log("flushQueue", "§3Connected to target: " + p.toString() + " Waiting: "
                                    + players.size() + " Server: " + sig.players);
                            players.remove(p);
                        } else {
                            Main.log("flushQueue", "§3Connection to target failed (ban?): " + p.toString() + " "
                                    + players.size() + " " + error.getMessage());
                            p.sendMessage(TextComponent.fromLegacyText(
                                    "§cConnection to " + config.target + " failed! Are you banned?"));
                            players.remove(p);
                            players.add(p);
                        }
                    };

                    p.connect(targetServer, cb);
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

        for (ProxiedPlayer player : players) {
            Server playerServer = player.getServer();

            // Player is not connected OR Player Server is not connected OR Player Server is
            // not the Queue Server
            if (!player.isConnected() || !playerServer.getInfo().getName().equals(config.queue)) {
                removePlayers.add(player);
                continue;
            }

            player.sendMessage(
                    TextComponent.fromLegacyText(config.message.replaceAll("%position%", Integer.toString(i))));

            i++;
        }

        for (ProxiedPlayer p : removePlayers) {
            try {
                mutex.acquire();
                players.remove(p);

                Main.log("sendUpdate", "§3Removed from queue since on the wrong server or disconnected: " + p.toString()
                        + " " + players.size());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mutex.release();
            }
        }
    }

    /**
     * Add a ProxiedPlayer to the queue if they join the queue server
     */
    @EventHandler
    public void onServerConnect(ServerConnectEvent e) {
        if (e.getTarget().getName().equals(config.queue)) {
            // Add ProxiedPlayer to queue
            if (!players.contains(e.getPlayer())) {
                try {
                    mutex.acquire();
                    players.add(e.getPlayer());
                    Main.log("onServerConnect",
                            "§3Added to queue: " + e.getPlayer().toString() + " Waiting: " + players.size());

                    e.getPlayer().sendMessage(TextComponent.fromLegacyText(
                            "§6" + config.message.replaceAll("%position%", Integer.toString(players.size()))));
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                } finally {
                    mutex.release();
                }
            }
        }
    }

//	/**
//	 * When a player is kicked from the main server
//	 */
//	@EventHandler(priority = EventPriority.LOWEST)
//	public void onKickedFromServer(ServerKickEvent e) {
//		if (e.getKickedFrom().getName().equals(config.target)) {
//			// Add ProxiedPlayer to queue
//			if (!players.contains(e.getPlayer())) {
//				try {
//					mutex.acquire();
//					players.add(e.getPlayer());
//					Main.log("onKickedFromServer",
//							"§3Added to queue after kick: " + e.getPlayer().toString() + " Waiting: " + players.size());
//				} catch (InterruptedException e1) {
//					e1.printStackTrace();
//				} finally {
//					mutex.release();
//				}
//			}
//		}
//	}

    /**
     * Removes a ProxiedPlayer from the queue if they were in it
     */
    @EventHandler
    public void onLeave(ServerDisconnectEvent e) {
        ProxiedPlayer player = e.getPlayer();
        Server playerNewServer = player.getServer();

        if (!playerNewServer.getInfo().getName().equals(config.queue)) {
            // Remove ProxiedPlayer from queue
            try {
                mutex.acquire();
                players.remove(player);
                Main.log("onLeave", "§3Removed from queue: " + player.toString() + " Waiting: " + players.size());
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

        public void done(ServerPing serverPing, Throwable throwable) {
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