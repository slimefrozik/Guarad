package ru.guardsystem.service;

import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class GuardManager {

    private static final int MAX_GUARDS = 3;
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofDays(30);

    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private YamlConfiguration guardsConfiguration;
    private final Map<UUID, GuardRecord> guards = new LinkedHashMap<>();

    public GuardManager(PersistenceLayer persistenceLayer, AuditLogger auditLogger) {
        this.persistenceLayer = persistenceLayer;
        this.auditLogger = auditLogger;
    }

    public void load() {
        this.guardsConfiguration = persistenceLayer.loadGuardsYaml();
        loadGuardsFromConfig();
        enforceInactivityRule();
        auditLogger.log("GuardManager loaded guards.yml");
    }

    public void save() {
        if (guardsConfiguration == null) {
            return;
        }
        guardsConfiguration.set("guards", serializeGuards());
        persistenceLayer.saveGuardsYaml(guardsConfiguration);
        auditLogger.log("GuardManager saved guards.yml");
    }

    public synchronized boolean registerGuard(UUID guardId) {
        Objects.requireNonNull(guardId, "guardId");
        enforceInactivityRule();
        GuardRecord existing = guards.get(guardId);
        if (existing != null) {
            existing.status = GuardStatus.ACTIVE;
            existing.lastActivity = Instant.now();
            return true;
        }

        if (guards.size() >= MAX_GUARDS) {
            return false;
        }

        guards.put(guardId, new GuardRecord(guardId, GuardStatus.ACTIVE, Instant.now()));
        return true;
    }

    public synchronized boolean removeGuard(UUID guardId) {
        return guards.remove(guardId) != null;
    }

    public synchronized boolean markActive(UUID guardId) {
        GuardRecord record = guards.get(guardId);
        if (record == null) {
            return false;
        }
        record.status = GuardStatus.ACTIVE;
        record.lastActivity = Instant.now();
        return true;
    }

    public synchronized boolean hasGuardPermission(UUID guardId) {
        enforceInactivityRule();
        GuardRecord record = guards.get(guardId);
        return record != null && record.status == GuardStatus.ACTIVE;
    }

    public synchronized void enforceInactivityRule() {
        Instant now = Instant.now();
        for (GuardRecord guardRecord : guards.values()) {
            if (guardRecord.status == GuardStatus.ACTIVE
                    && guardRecord.lastActivity.plus(INACTIVITY_TIMEOUT).isBefore(now)) {
                guardRecord.status = GuardStatus.INACTIVE;
            }
        }
    }

    public synchronized int getActiveGuardCount() {
        enforceInactivityRule();
        return (int) guards.values().stream().filter(guard -> guard.status == GuardStatus.ACTIVE).count();
    }

    public synchronized List<UUID> getActiveGuardIds() {
        enforceInactivityRule();
        return guards.values().stream()
                .filter(guard -> guard.status == GuardStatus.ACTIVE)
                .map(guard -> guard.id)
                .toList();
    }

    public synchronized List<UUID> getAllGuardIds() {
        return new ArrayList<>(guards.keySet());
    }

    public synchronized Optional<GuardStatus> getGuardStatus(UUID guardId) {
        GuardRecord record = guards.get(guardId);
        return record == null ? Optional.empty() : Optional.of(record.status);
    }

    public synchronized int getRequiredUnanimousVotes() {
        return Math.max(1, getActiveGuardCount());
    }

    public synchronized int getRequiredImpeachmentVotes() {
        int active = Math.max(1, getActiveGuardCount());
        return (int) Math.ceil((active * 2) / 3.0d);
    }

    public void touch() {
        auditLogger.log("GuardManager touch called");
    }

    private void loadGuardsFromConfig() {
        guards.clear();
        if (guardsConfiguration == null) {
            return;
        }

        List<Map<?, ?>> serialized = guardsConfiguration.getMapList("guards");
        for (Map<?, ?> guardNode : serialized) {
            try {
                UUID id = UUID.fromString(String.valueOf(guardNode.get("id")));
                GuardStatus status = GuardStatus.valueOf(String.valueOf(guardNode.getOrDefault("status", "ACTIVE")));
                Instant lastActivity = Instant.parse(String.valueOf(guardNode.getOrDefault("lastActivity", Instant.now().toString())));
                guards.put(id, new GuardRecord(id, status, lastActivity));
            } catch (Exception ignored) {
                // Skip malformed entries.
            }
        }

        if (!guards.isEmpty()) {
            trimToMaxGuards();
            return;
        }

        List<String> legacyGuards = guardsConfiguration.getStringList("guards");
        for (String guard : legacyGuards) {
            try {
                UUID id = UUID.fromString(guard);
                guards.put(id, new GuardRecord(id, GuardStatus.ACTIVE, Instant.now()));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries.
            }
        }

        trimToMaxGuards();
    }

    private void trimToMaxGuards() {
        if (guards.size() <= MAX_GUARDS) {
            return;
        }

        List<GuardRecord> byActivity = guards.values().stream()
                .sorted(Comparator.comparing(record -> record.lastActivity))
                .toList();

        int toRemove = guards.size() - MAX_GUARDS;
        for (int i = 0; i < toRemove; i++) {
            guards.remove(byActivity.get(i).id);
        }
    }

    private List<Map<String, Object>> serializeGuards() {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (GuardRecord guardRecord : guards.values()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", guardRecord.id.toString());
            node.put("status", guardRecord.status.name());
            node.put("lastActivity", guardRecord.lastActivity.toString());
            serialized.add(node);
        }
        return serialized;
    }

    public enum GuardStatus {
        ACTIVE,
        INACTIVE
    }

    private static final class GuardRecord {
        private final UUID id;
        private GuardStatus status;
        private Instant lastActivity;

        private GuardRecord(UUID id, GuardStatus status, Instant lastActivity) {
            this.id = id;
            this.status = status;
            this.lastActivity = lastActivity;
        }
    }
}
