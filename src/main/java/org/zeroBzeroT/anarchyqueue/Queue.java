package org.zeroBzeroT.anarchyqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ListenerBoundEvent;
import com.velocitypowered.api.event.proxy.ListenerCloseEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import jdk.jpackage.internal.Log;

import java.util.Optional;

public class Queue {

    private final ProxyServer proxyServer;

    public Queue(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Subscribe
    public void onListenerBound(ListenerBoundEvent e) {
        Log.info("ListenerBoundEvent: " + e.getAddress().toString() + " " + e.getListenerType().toString());
    }

    @Subscribe
    public void onListenerClose(ListenerCloseEvent e) {
        Log.info("ListenerCloseEvent: " + e.getAddress().toString() + " " + e.getListenerType().toString());
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent e) {
        Log.info("KickedFromServerEvent: " + e.getPlayer().getUsername() + " " + e.getServer().getServerInfo().getName() + " " + e.getServer().getServerInfo().getAddress().toString());
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent e) {
        Optional<RegisteredServer> serverOpt = e.getInitialServer();
        if (serverOpt.isPresent()) {
            RegisteredServer server = serverOpt.get();
            Log.info("PlayerChooseInitialServerEvent: " + e.getPlayer().getUsername() + " " + server.getServerInfo().getName() + " " + server.getServerInfo().getAddress().toString());
        } else {
            Log.info("PlayerChooseInitialServerEvent: " + e.getPlayer().getUsername() + " no initial server");
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent e) {
        Log.info("ServerConnectedEvent: " + e.getPlayer().getUsername() + " " + e.getServer().getServerInfo().getName() + " " + e.getServer().getServerInfo().getAddress().toString());
    }

    public void flushQueue() {
    }

    public void sendUpdate() {
    }

}
