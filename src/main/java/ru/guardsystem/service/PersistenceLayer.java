package ru.guardsystem.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersistenceLayer {

    private static final String DEFAULT_VOTES_JSON = "{\n  \"votes\": []\n}\n";

    private final JavaPlugin plugin;
    private final Path dataFolder;

    public PersistenceLayer(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
    }

    public void initializeStorage() {
        try {
            Files.createDirectories(dataFolder);
            Files.createDirectories(logsDirectory());
            createGuardsIfMissing();
            createVotesIfMissing();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize storage structure", ex);
        }
    }

    public YamlConfiguration loadGuardsYaml() {
        return YamlConfiguration.loadConfiguration(guardsFile().toFile());
    }

    public String loadVotesJson() {
        try {
            return Files.readString(votesFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read votes.json", ex);
        }
    }

    public void saveGuardsYaml(YamlConfiguration yamlConfiguration) {
        try {
            yamlConfiguration.save(guardsFile().toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save guards.yml", ex);
        }
    }

    public void saveVotesJson(String content) {
        try {
            Files.writeString(votesFile(), content);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save votes.json", ex);
        }
    }

    public Path logsDirectory() {
        return dataFolder.resolve("logs");
    }

    private Path guardsFile() {
        return dataFolder.resolve("guards.yml");
    }

    private Path votesFile() {
        return dataFolder.resolve("votes.json");
    }

    private void createGuardsIfMissing() throws IOException {
        Path guardsFile = guardsFile();
        if (Files.exists(guardsFile)) {
            return;
        }

        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.set("guards", java.util.List.of());
        yamlConfiguration.save(guardsFile.toFile());
    }

    private void createVotesIfMissing() throws IOException {
        Path votesFile = votesFile();
        if (Files.exists(votesFile)) {
            return;
        }
        Files.writeString(votesFile, DEFAULT_VOTES_JSON);
    }
}
