package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
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
            .repeat(Duration.ofSeconds(1))
            .schedule();
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent e) {
        queuedPlayers.add(e.getPlayer());
    }

    public void process() {
        // check queue server reachability
        final RegisteredServer serverQueue;
        try {
            serverQueue = getServer(Config.serverQueue);
        } catch (ServerNotReachableException e) {
            log.warn(e.getMessage());
            return;
        }
        // check main server reachability
        final RegisteredServer serverMain;
        try {
            serverMain = getServer(Config.serverMain);
        } catch (ServerNotReachableException e) {
            sendNotification();
            return;
        }
        // check current player counts
        int onlinePlayersMain = serverMain.getPlayersConnected().size();
        int onlinePlayersQueue = serverQueue.getPlayersConnected().size();
        log.info("Online players: main " + onlinePlayersMain + " / queue " + onlinePlayersQueue);
        // TODO: send notifications to players every seconds % 10 == 0
        // TODO: check for maximum connected players
        // TODO: if too many players on main send infos and skip processing
        // get next player to move to main server
        if (queuedPlayers.size() == 0) return;
        sendNotification();
        // connect first player
        Player player = queuedPlayers.remove(0);
        player.createConnectionRequest(serverMain);
    }

    private void sendNotification() {
        // send every 10 seconds only
        if (Instant.now().getEpochSecond() % 10 != 0) return;
        for (int i = 0; i < queuedPlayers.size(); i++) {
            queuedPlayers
                .get(i)
                .sendMessage(TextComponent.of(
                    "Position in queue: " + i + "/" + queuedPlayers.size()
                ).color(TextColor.DARK_AQUA));
        }
    }

    private RegisteredServer getServer(String name) throws ServerNotReachableException {
        // get server configured in velocity.toml by name
        Optional<RegisteredServer> serverOpt = proxyServer.getServer(name);
        if (!serverOpt.isPresent()) {
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
