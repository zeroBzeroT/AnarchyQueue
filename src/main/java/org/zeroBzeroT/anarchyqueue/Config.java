package org.zeroBzeroT.anarchyqueue;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {

    public static String target = null;
    public static String queue = null;
    public static int maxPlayers = 0;
    public static String messagePosition = null;
    public static String messageConnecting = null;
    public static String serverName = null;
    public static int waitOnKick = 0;
    public static boolean kick = true;

    /**
     * Load the config from the plugin data folder
     *
     * @param path Path to the plugin data folder
     */
    static void loadConfig(Path path) throws IOException {

        File file = new File(path.toFile(), "config.toml");

        if (!file.getParentFile().exists()) {
            if (!file.getParentFile().mkdirs()) throw new IllegalStateException("unable to create data dir!");
        }

        if (!file.exists()) {
            try (InputStream input = Config.class.getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    if (!file.createNewFile()) throw new IllegalStateException("unable to load default config!");
                }
            } catch (IOException exception) {
                Main.getInstance().log.warn(exception.getMessage());
                return;
            }
        }

        Toml toml = new Toml().read(file);
        target = toml.getString("target", "main");
        queue = toml.getString("queue", "queue");
        maxPlayers = toml.getLong("maxPlayers", 420L).intValue();
        waitOnKick = toml.getLong("waitOnKick", 16L).intValue();
        messagePosition = toml.getString("messagePosition", "&6Position in queue: &l%position%");
        messageConnecting = toml.getString("messageConnecting", "&6Connecting to the server...");
        serverName = toml.getString("serverName", "0b0t");
        kick = toml.getBoolean("kick", true);

    }

}
