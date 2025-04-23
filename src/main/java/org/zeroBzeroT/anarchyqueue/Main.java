package org.zeroBzeroT.anarchyqueue;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bstats.velocity.Metrics;

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

    public final ComponentLogger log;

    private final ProxyServer server;

    private final Path dataDir;

    private final Metrics.Factory metricsFactory;

    @Inject
    public Main(ProxyServer server, CommandManager commandManager, ComponentLogger logger, @DataDirectory final Path dataDir, Metrics.Factory metricsFactory) {
        this.server = server;
        this.log = logger;
        this.dataDir = dataDir;
        instance = this;
        this.metricsFactory = metricsFactory;
    }

    public static Main getInstance() {
        if (instance == null)
            throw new IllegalStateException("instance was null!");

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

        // Load Plugin Metrics
        if (Config.bStats) {
            metricsFactory.make(this, 16228);
        }
    }
}
