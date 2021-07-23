package org.zeroBzeroT.anarchyqueue;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {

    public static String serverMain = null;
    public static String serverQueue = null;
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
        serverMain = toml.getString("server-main", "main");
        serverQueue = toml.getString("server-queue", "queue");
        maxPlayers = toml.getLong("max-players", 420L).intValue();
        waitOnKick = toml.getLong("wait-on-kick", 16L).intValue();
        messagePosition = toml.getString("message-position", "&6Position in queue: &l%position%");
        messageConnecting = toml.getString("message-connecting", "&6Connecting to the server...");
        serverName = toml.getString("server-name", "0b0t");
        kick = toml.getBoolean("kick", true);

    }

}
