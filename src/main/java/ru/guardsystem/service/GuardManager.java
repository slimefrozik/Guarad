package ru.guardsystem.service;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class GuardManager {

    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private final Set<String> guards = new LinkedHashSet<>();

    public GuardManager(PersistenceLayer persistenceLayer, AuditLogger auditLogger) {
        this.persistenceLayer = persistenceLayer;
        this.auditLogger = auditLogger;
    }

    public void load() {
        guards.clear();
        YamlConfiguration guardsConfiguration = persistenceLayer.loadGuardsYaml();
        guardsConfiguration.getStringList("guards").stream()
                .map(this::normalize)
                .forEach(guards::add);
        auditLogger.log("GuardManager loaded guards.yml");
    }

    public void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("guards", guards.stream().toList());
        persistenceLayer.saveGuardsYaml(configuration);
        auditLogger.log("GuardManager saved guards.yml");
    }

    public boolean isGuard(String nickname) {
        return guards.contains(normalize(nickname));
    }

    public int guardCount() {
        return guards.size();
    }

    public boolean addGuard(String nickname) {
        boolean changed = guards.add(normalize(nickname));
        if (changed) {
            auditLogger.log("Guard added: " + nickname);
        }
        return changed;
    }

    public boolean removeGuard(String nickname) {
        boolean changed = guards.remove(normalize(nickname));
        if (changed) {
            auditLogger.log("Guard removed: " + nickname);
        }
        return changed;
    }

    public Collection<String> listGuards() {
        return Set.copyOf(guards);
    }

    private String normalize(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }
}
