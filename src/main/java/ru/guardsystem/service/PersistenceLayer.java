package ru.guardsystem.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

public class PersistenceLayer {

    private static final String DEFAULT_VOTES_JSON = "{\n  \"votes\": []\n}\n";
    private static final String DEFAULT_GUARDS_YAML = "guards: []\n";

    private final JavaPlugin plugin;
    private final Path dataFolder;
    private final ObjectMapper objectMapper;

    public PersistenceLayer(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.objectMapper = new ObjectMapper();
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
        Path guardsFile = guardsFile();
        try {
            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(guardsFile.toFile());
            validateGuardsSchema(yamlConfiguration);
            return yamlConfiguration;
        } catch (Exception ex) {
            plugin.getLogger().warning("guards.yml is invalid, restoring defaults: " + ex.getMessage());
            return recoverGuards(guardsFile, ex);
        }
    }

    public String loadVotesJson() {
        Path votesFile = votesFile();
        try {
            String content = Files.readString(votesFile);
            validateVotesSchema(content);
            return content;
        } catch (Exception ex) {
            plugin.getLogger().warning("votes.json is invalid, restoring defaults: " + ex.getMessage());
            return recoverVotes(votesFile, ex);
        }
    }

    public void saveGuardsYaml(YamlConfiguration yamlConfiguration) {
        try {
            validateGuardsSchema(yamlConfiguration);
            atomicWrite(guardsFile(), yamlConfiguration.saveToString());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save guards.yml", ex);
        }
    }

    public void saveVotesJson(String content) {
        try {
            validateVotesSchema(content);
            atomicWrite(votesFile(), content);
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
        yamlConfiguration.set("guards", List.of());
        atomicWrite(guardsFile, yamlConfiguration.saveToString());
    }

    private void createVotesIfMissing() throws IOException {
        Path votesFile = votesFile();
        if (Files.exists(votesFile)) {
            return;
        }
        atomicWrite(votesFile, DEFAULT_VOTES_JSON);
    }

    private void validateGuardsSchema(YamlConfiguration yamlConfiguration) {
        if (!yamlConfiguration.contains("guards") || !yamlConfiguration.isList("guards")) {
            throw new IllegalStateException("guards.yml schema violation: expected list at 'guards'");
        }
    }

    private void validateVotesSchema(String content) throws IOException {
        JsonNode root = objectMapper.readTree(content);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("votes.json schema violation: expected object root");
        }
        JsonNode votes = root.get("votes");
        if (votes == null || !votes.isArray()) {
            throw new IllegalStateException("votes.json schema violation: expected array at 'votes'");
        }
    }

    private YamlConfiguration recoverGuards(Path file, Exception originalException) {
        backupCorruptedFile(file);
        try {
            atomicWrite(file, DEFAULT_GUARDS_YAML);
            return YamlConfiguration.loadConfiguration(file.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to recover guards.yml", combine(originalException, ex));
        }
    }

    private String recoverVotes(Path file, Exception originalException) {
        backupCorruptedFile(file);
        try {
            atomicWrite(file, DEFAULT_VOTES_JSON);
            return DEFAULT_VOTES_JSON;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to recover votes.json", combine(originalException, ex));
        }
    }

    private RuntimeException combine(Exception primary, Exception secondary) {
        primary.addSuppressed(secondary);
        return new IllegalStateException(primary.getMessage(), primary);
    }

    private void backupCorruptedFile(Path path) {
        if (!Files.exists(path)) {
            return;
        }

        String backupName = path.getFileName() + ".corrupt-" + Instant.now().toEpochMilli();
        Path backupPath = path.resolveSibling(backupName);
        try {
            Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to backup corrupted file " + path.getFileName(), ex);
        }
    }

    private void atomicWrite(Path targetFile, String content) throws IOException {
        Path tempFile = Files.createTempFile(targetFile.getParent(), targetFile.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            try {
                Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
