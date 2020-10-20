package org.zeroBzeroT.anarchyqueue;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Config {
    public final String target = "main";
    public final String queue = "queue";
    public final int maxPlayers = 10;
    public final String message = "Position in queue: %position%";

    /**
     * Loads a config file, and if it doesn't exist creates one
     *
     * @param filepath filepath of the config
     */
    static Config getConfig(String filepath) {
        Gson gson = new Gson();

        try {
            FileReader fr = new FileReader(filepath);
            return gson.fromJson(fr, Config.class);
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            genConfig(filepath);
        }

        return new Config();
    }

    /**
     * Create a new config
     *
     * @param filepath filepath of the config
     */
    static void genConfig(String filepath) {
        File config = new File(filepath);
        try {
            //noinspection ResultOfMethodCallIgnored
            config.createNewFile();
            FileWriter fw = new FileWriter(config);
            fw.write(
                    "{\n\"target\": \"main\",\n\"queue\": \"queue\",\n\"maxPlayers\": 80,\n\"message\": \"Position in queue: %position%\"\n}");
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
