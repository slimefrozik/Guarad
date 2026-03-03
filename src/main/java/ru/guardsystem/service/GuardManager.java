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
        auditLogger.logEvent("vote_result", "system-load", java.util.Map.of("message", "GuardManager loaded guards.yml"));
    }

    public void save() {
        if (guardsConfiguration == null) {
            return;
        }
        guardsConfiguration.set("guards", serializeGuards());
        persistenceLayer.saveGuardsYaml(guardsConfiguration);
        auditLogger.logEvent("vote_result", "system-save", java.util.Map.of("message", "GuardManager saved guards.yml"));
    }

    public void requestRollback(String sessionId, String actor, String target) {
        auditLogger.logEvent("rollback_requested", sessionId, java.util.Map.of("actor", actor, "target", target));
    }

    public void approveRollback(String sessionId, String actor, String target) {
        auditLogger.logEvent("rollback_approved", sessionId, java.util.Map.of("actor", actor, "target", target));
    }

    public void transferGuard(String sessionId, String actor, String fromGuard, String toGuard) {
        auditLogger.logEvent("guard_transfer", sessionId, java.util.Map.of(
                "actor", actor,
                "from", fromGuard,
                "to", toGuard
        ));
    }

    public void startImpeach(String sessionId, String actor, String target) {
        auditLogger.logEvent("impeach_started", sessionId, java.util.Map.of("actor", actor, "target", target));
    }

    public void registerImpeachResult(String sessionId, String actor, String target, String result) {
        auditLogger.logEvent("impeach_result", sessionId, java.util.Map.of(
                "actor", actor,
                "target", target,
                "result", result
        ));
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
