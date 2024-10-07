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

    public static String name = null; // TODO: not in use, implement or remove this

    public static int maxPlayers = 0;

    public static String messagePosition = null;

    public static String messageConnecting = null;

    public static String messageFull = null; // TODO: not in use, implement or remove this

    public static String messageOffline = null; // TODO: not in use, implement or remove this

    public static boolean kick = true;

    public static int waitOnKick = 16;

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
        name = toml.getString("name", "0b0t");
        maxPlayers = toml.getLong("max-players", 420L).intValue();
        messagePosition = toml.getString("message-position", "Position in queue: ");
        messageConnecting = toml.getString("message-connecting", "Connecting to the server...");
        messageFull = toml.getString("message-full", "Server is currently full!");
        messageOffline = toml.getString("message-offline", "Server is currently down!");
        kick = toml.getBoolean("kick", true);
        waitOnKick = toml.getLong("wait-on-kick", 16L).intValue();
    }
}
