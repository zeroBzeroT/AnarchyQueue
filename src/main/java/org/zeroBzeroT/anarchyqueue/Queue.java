package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ListenerBoundEvent;
import com.velocitypowered.api.event.proxy.ListenerCloseEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Optional;

public class Queue {

    private final ProxyServer proxyServer;

    public Queue(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Subscribe
    public void onListenerBound(ListenerBoundEvent e) {
        Main.getInstance().log.info("ListenerBoundEvent: " + e.getAddress().toString() + " " + e.getListenerType().toString());
    }

    @Subscribe
    public void onListenerClose(ListenerCloseEvent e) {
        Main.getInstance().log.info("ListenerCloseEvent: " + e.getAddress().toString() + " " + e.getListenerType().toString());
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent e) {
        Main.getInstance().log.info("KickedFromServerEvent: " + e.getPlayer().getUsername() + " " + e.getServer().getServerInfo().getName() + " " + e.getServer().getServerInfo().getAddress().toString());
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent e) {
        Optional<RegisteredServer> serverOpt = e.getInitialServer();
        if (serverOpt.isPresent()) {
            RegisteredServer server = serverOpt.get();
            Main.getInstance().log.info("PlayerChooseInitialServerEvent: " + e.getPlayer().getUsername() + " " + server.getServerInfo().getName() + " " + server.getServerInfo().getAddress().toString());
        } else {
            Main.getInstance().log.info("PlayerChooseInitialServerEvent: " + e.getPlayer().getUsername() + " no initial server");
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent e) {
        Main.getInstance().log.info("ServerConnectedEvent: " + e.getPlayer().getUsername() + " " + e.getServer().getServerInfo().getName() + " " + e.getServer().getServerInfo().getAddress().toString());
    }

    public void flushQueue() {
        Optional<RegisteredServer> serverMain = proxyServer.getServer(Config.serverMain);
        Optional<RegisteredServer> serverQueue = proxyServer.getServer(Config.serverQueue);
        if (!serverMain.isPresent()) {
            Main.getInstance().log.warn("Main server " + Config.serverMain + " is not connected!");
            return;
        }
        if (!serverQueue.isPresent()) {
            Main.getInstance().log.warn("Queue server " + Config.serverQueue + " is not connected!");
            return;
        }
        int onlinePlayersMain = serverMain.get().getPlayersConnected().size();
        int onlinePlayersQueue = serverQueue.get().getPlayersConnected().size();
        Main.getInstance().log.info("Online player: main " + onlinePlayersMain + " / queue" + onlinePlayersQueue);
    }

    public void sendUpdate() {
    }

}
