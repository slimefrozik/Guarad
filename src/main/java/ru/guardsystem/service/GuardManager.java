package ru.guardsystem.service;

import org.bukkit.configuration.file.YamlConfiguration;

public class GuardManager {

    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private YamlConfiguration guardsConfiguration;

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
}
