package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

public class Queue {

    private final Logger log;
    private final ProxyServer proxyServer;

    /**
     * We dont use ConcurrentLinkedQueue for this because we want index based access to players.
     */
    private final List<Player> queuedPlayers = new CopyOnWriteArrayList<>();

    public Queue(ProxyServer proxyServer) {
        this.log = Main.getInstance().log;
        this.proxyServer = proxyServer;
        // process queue
        proxyServer.getScheduler()
            .buildTask(Main.getInstance(), this::process)
            .delay(Duration.ofSeconds(1))
            .repeat(Duration.ofSeconds(2))
            .schedule();
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent e) {
        queuedPlayers.add(e.getPlayer());
    }

    public void process() {
        final RegisteredServer serverMain;
        final RegisteredServer serverQueue;
        try {
            serverMain = getServer(Config.serverMain);
            serverQueue = getServer(Config.serverQueue);
        } catch (ServerNotReachableException e) {
            log.warn(e.getMessage());
            return;
        }
        // check current player counts
        int onlinePlayersMain = serverMain.getPlayersConnected().size();
        int onlinePlayersQueue = serverQueue.getPlayersConnected().size();
        log.info("Online players: main " + onlinePlayersMain + " / queue " + onlinePlayersQueue);
        // TODO: send notifications to players every seconds % 10 == 0
        // TODO: check for maximum connected players
        // get next player to move to main server
        if (queuedPlayers.size() == 0) return;
        Player player = queuedPlayers.get(0);
        player.createConnectionRequest(serverMain);
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

}
