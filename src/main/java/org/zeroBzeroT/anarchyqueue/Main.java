package org.zeroBzeroT.anarchyqueue;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

@Plugin(
    id = "anarchyqueue",
    name = "AnarchyQueue",
    version = "2.0.0-SNAPSHOT",
    description = "velocity queue system for anarchy servers",
    url = "https://github.com/zeroBzeroT/AnarchyQueue",
    authors = {"bierdosenhalter", "nothub"}
)
public class Main {

    private static Main instance;
    public final ProxyServer server;
    public final Logger log;
    private final Path dataDir;

    @Inject
    public Main(ProxyServer server, CommandManager commandManager, Logger logger, @DataDirectory final Path dataDir) {
        this.server = server;
        this.log = logger;
        this.dataDir = dataDir;
        try {
            Config.loadConfig(dataDir);
        } catch (IOException e) {
            logger.warn(e.getMessage());
        }
        commandManager.register("maxplayers", new SlotsCommand(), "maxslots");
        instance = this;
    }

    public static Main getInstance() {
        if (instance == null) throw new IllegalStateException("instance was null!");
        return instance;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {

        try {
            Config.loadConfig(dataDir);
        } catch (Exception e) {
            log.error(e.getMessage());
            server.shutdown();
            return;
        }

        Queue queue = new Queue(server);

        server.getEventManager().register(this, queue);

        // Run queue flusher
        server.getScheduler()
            .buildTask(this, queue::flushQueue)
            .delay(Duration.ofSeconds(1))
            .repeat(Duration.ofSeconds(2))
            .schedule();

        // Run player notification
        server.getScheduler()
            .buildTask(this, queue::sendUpdate)
            .delay(Duration.ofSeconds(1))
            .repeat(Duration.ofSeconds(10))
            .schedule();

        log.info("Position Message: " + Config.messagePosition);
        log.info("Connecting Message: " + Config.messageConnecting);

    }

}
