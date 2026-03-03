package ru.guardsystem.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GuardManager {

    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private YamlConfiguration guardsConfiguration;
    private List<String> guards = List.of();

    public GuardManager(PersistenceLayer persistenceLayer, AuditLogger auditLogger) {
        this.persistenceLayer = persistenceLayer;
        this.auditLogger = auditLogger;
    }

    public void load() {
        this.guardsConfiguration = persistenceLayer.loadGuardsYaml();
        List<String> configuredGuards = guardsConfiguration.getStringList("guards");
        this.guards = configuredGuards.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        auditLogger.log("GuardManager loaded guards.yml with guards=" + this.guards.size());
    }

    public void save() {
        if (guardsConfiguration == null) {
            return;
        }
        guardsConfiguration.set("guards", new ArrayList<>(guards));
        persistenceLayer.saveGuardsYaml(guardsConfiguration);
        auditLogger.log("GuardManager saved guards.yml");
    }

    public void touch() {
        auditLogger.log("GuardManager touch called");
    }

    public int getActiveGuardCount() {
        int active = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isGuard(player.getName())) {
                active++;
            }
        }
        return active;
    }

    public boolean isGuard(String playerName) {
        return guards.contains(playerName.toLowerCase(Locale.ROOT));
    }

    public int getConfiguredGuardCount() {
        return guards.size();
    }
}
