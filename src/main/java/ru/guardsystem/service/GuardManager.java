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
        auditLogger.log("GuardManager loaded guards.yml");
    }

    public void save() {
        if (guardsConfiguration == null) {
            return;
        }
        persistenceLayer.saveGuardsYaml(guardsConfiguration);
        auditLogger.log("GuardManager saved guards.yml");
    }

    public void touch() {
        auditLogger.log("GuardManager touch called");
    }
}
