package org.zeroBzeroT.anarchyqueue;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "anarchyqueue",
    name = "AnarchyQueue",
    version = BuildConstants.VERSION,
    description = "velocity queue system for anarchy servers",
    url = "https://github.com/zeroBzeroT/AnarchyQueue",
    authors = {"bierdosenhalter", "nothub"}
)
public class Main {

    private static Main instance;

    public final Logger log;

    private final ProxyServer server;
    private final Path dataDir;

    @Inject
    public Main(ProxyServer server, CommandManager commandManager, Logger logger, @DataDirectory final Path dataDir) {
        this.server = server;
        this.log = logger;
        this.dataDir = dataDir;
        instance = this;
    }

    public static Main getInstance() {
        if (instance == null) throw new IllegalStateException("instance was null!");
        return instance;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Load config
        try {
            Config.loadConfig(dataDir);
        } catch (Exception e) {
            log.error(e.getMessage());
            server.shutdown();
            return;
        }
        // Register queue
        Queue queue = new Queue(server);
        server.getEventManager().register(this, queue);
    }

}
