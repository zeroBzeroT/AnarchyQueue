package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;

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
        getProxy().getPluginManager().registerCommand(this, new QueueCommand(queue));

        // Listener
        getProxy().getPluginManager().registerListener(this, queue);

        // Run queue flusher
        getProxy().getScheduler().schedule(this, queue::flushQueue, 1, 2, TimeUnit.SECONDS);

        // Run player notification
        getProxy().getScheduler().schedule(this, queue::sendUpdate, 1, 10, TimeUnit.SECONDS);

        // Print config settings
        log("config", "§3Queue message example: §r" + ChatColor.translateAlternateColorCodes('&', Config.messagePosition.replaceAll("%position%", "42")));
        log("config", "§3Connecting message: §r" + ChatColor.translateAlternateColorCodes('&', Config.messageConnecting));
        log("config", "§3Full or offline message: §r" + ChatColor.translateAlternateColorCodes('&', Config.messageFullOrOffline));
        log("config", "§3Max players on main server: §r" + Config.maxPlayers);
        log("config", "§3Pass main server kicks to client: §r" + Config.kickPassthrough);
        log("config", "§3Kick on restart of the main server: §r" + Config.kickOnRestart);
        log("config", "§3Kick when the main server is busy: §r" + Config.kickOnBusy);
        log("config", "§3Send status as title: §r" + Config.sendTitle);

        // Load Plugin Metrics
        if (Config.bStats) {
            new Metrics(this, 16228);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();

        getProxy().getPluginManager().unregisterListeners(this);
        getProxy().getPluginManager().unregisterCommands(this);
    }
}
