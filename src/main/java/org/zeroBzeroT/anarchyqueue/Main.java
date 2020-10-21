package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class Main extends Plugin {
    public static int GLOBAL_SLOTS;
    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    public static void log(String module, String message) {
        Main.getInstance().getLogger().info("§a[" + module + "] §e" + message + "§r");
    }

    @Override
    public void onEnable() {
        instance = this;

        String folder = getDataFolder().getPath();

        // Create path directories if not existent
        //noinspection ResultOfMethodCallIgnored
        new File(folder).mkdirs();

        Config config = Config.getConfig(folder + "/config.json");

        Main.GLOBAL_SLOTS = config.maxPlayers;

        Queue queue = new Queue(config);

        // Commands
        getProxy().getPluginManager().registerCommand(this, new SlotsCommand());

        // Listener
        getProxy().getPluginManager().registerListener(this, queue);

        // Run queue flusher
        getProxy().getScheduler().schedule(this, queue::flushQueue, 1, 5, TimeUnit.SECONDS);

        // Run player notification
        getProxy().getScheduler().schedule(this, queue::sendUpdate, 1, 10, TimeUnit.SECONDS);

        log("onEnable", "§3Queue Message: " + config.messagePosition);
        log("onEnable", "§3Connecting Message: " + config.messageConnecting);
    }
}
