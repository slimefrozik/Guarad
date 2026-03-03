package ru.guardsystem.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class GuardManager {

    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private YamlConfiguration guardsConfiguration;
    private Set<String> guards = Set.of();

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
            .collect(Collectors.toCollection(LinkedHashSet::new));
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


    public boolean transferGuardRole(String fromNickname, String toNickname) {
        String from = normalize(fromNickname);
        String to = normalize(toNickname);
        if (from.equals(to) || !guards.contains(from) || guards.contains(to)) {
            return false;
        }

        guards.remove(from);
        guards.add(to);
        auditLogger.log("Guard role transferred: " + fromNickname + " -> " + toNickname);
        return true;
    }

    public Collection<String> listGuards() {
        return Set.copyOf(guards);
    }

    private String normalize(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
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

    public int getConfiguredGuardCount() {
        return guards.size();
    }
}
