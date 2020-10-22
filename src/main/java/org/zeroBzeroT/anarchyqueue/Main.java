package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class Main extends Plugin {
    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    public static void log(String module, String message) {
        Main.getInstance().getLogger().info("§a[" + module + "] §e" + message + "§r");
    }

    @Override
    public void onEnable() {
        super.onEnable();

        instance = this;

        String folder = getDataFolder().getPath();

        // Create path directories if not existent
        //noinspection ResultOfMethodCallIgnored
        new File(folder).mkdirs();

        // Config
        try {
            Config.getConfig(this);
        } catch (Exception e) {
            e.printStackTrace();
            onDisable();
            return;
        }

        Queue queue = new Queue();

        // Commands
        getProxy().getPluginManager().registerCommand(this, new SlotsCommand());

        // Listener
        getProxy().getPluginManager().registerListener(this, queue);

        // Run queue flusher
        getProxy().getScheduler().schedule(this, queue::flushQueue, 1, 2, TimeUnit.SECONDS);

        // Run player notification
        getProxy().getScheduler().schedule(this, queue::sendUpdate, 1, 10, TimeUnit.SECONDS);

        log("onEnable", "§3Queue Message: §r" + ChatColor.translateAlternateColorCodes('&', Config.messagePosition));
        log("onEnable", "§3Connecting Message: §r" + ChatColor.translateAlternateColorCodes('&', Config.messageConnecting));
    }

    @Override
    public void onDisable() {
        super.onDisable();

        getProxy().getPluginManager().unregisterListeners(this);
        getProxy().getPluginManager().unregisterCommands(this);
    }
}
