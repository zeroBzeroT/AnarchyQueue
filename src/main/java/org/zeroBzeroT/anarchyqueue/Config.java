package org.zeroBzeroT.anarchyqueue;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

@SuppressWarnings("CanBeFinal")
public class Config {
    public static String target = null;
    public static String queue = null;
    public static int maxPlayers = 0;
    public static String messagePosition = null;
    public static String messageConnecting = null;
    public static String messageFullOrOffline= null;
    public static String serverName = null;
    public static int waitOnKick = 0;
    public static boolean kickPassthrough = true;
    public static boolean kickOnRestart = false;
    public static boolean kickOnBusy = false;


    /**
     * Loads a config file, and if it doesn't exist creates one
     *
     * @param plugin BungeeCord plugin
     */
    static void getConfig(Plugin plugin) throws Exception {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (configFile.exists()) {
            loadConfig(configFile);
        } else {
            try {
                InputStream in = plugin.getResourceAsStream("config.yml");
                Files.copy(in, configFile.toPath());
                loadConfig(configFile);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        messageConnecting = messageConnecting.replaceAll("%server%", serverName);
        messageFullOrOffline = messageFullOrOffline.replaceAll("%server%", serverName);
    }

    /**
     * Load the config from the plugin data folder
     *
     * @param configFile Path to the configuration file
     */
    static void loadConfig(File configFile) throws IOException {
        Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);

        Arrays.asList(Config.class.getDeclaredFields()).forEach(field -> {
            try {
                field.setAccessible(true);
                field.set(Config.class, config.get(field.getName()));
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException exception) {
                Main.log("config", "§fError while loading the config. Please remove the config file and let the plugin generate a new one:");
                exception.printStackTrace();
            }
        });
    }
}
