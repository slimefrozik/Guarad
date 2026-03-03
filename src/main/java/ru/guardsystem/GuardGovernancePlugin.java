package ru.guardsystem;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.guardsystem.commands.GuardCommand;
import ru.guardsystem.commands.VoteBanCommand;
import ru.guardsystem.commands.VoteCommand;
import ru.guardsystem.service.AuditLogger;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.PersistenceLayer;
import ru.guardsystem.service.SessionManager;
import ru.guardsystem.service.VoteManager;

public final class GuardGovernancePlugin extends JavaPlugin {

    private PersistenceLayer persistenceLayer;
    private AuditLogger auditLogger;
    private GuardManager guardManager;
    private VoteManager voteManager;
    private SessionManager sessionManager;

    @Override
    public void onEnable() {
        this.persistenceLayer = new PersistenceLayer(this);
        this.persistenceLayer.initializeStorage();

        this.auditLogger = new AuditLogger(getDataFolder().toPath().resolve("logs"));
        this.guardManager = new GuardManager(persistenceLayer, auditLogger);
        this.voteManager = new VoteManager(persistenceLayer, auditLogger, guardManager);
        this.sessionManager = new SessionManager(auditLogger);

        this.guardManager.load();
        this.voteManager.load();
        this.sessionManager.load();

        registerCommands();
        getLogger().info("GuardGovernance enabled.");
    }

    @Override
    public void onDisable() {
        if (guardManager != null) {
            guardManager.save();
        }
        if (voteManager != null) {
            voteManager.save();
        }
        if (sessionManager != null) {
            sessionManager.save();
        }
    }

    private void registerCommands() {
        VoteBanCommand voteBanCommand = new VoteBanCommand(voteManager, sessionManager);
        VoteCommand voteCommand = new VoteCommand(voteManager);
        GuardCommand guardCommand = new GuardCommand(guardManager);

        bindCommand("voteban", voteBanCommand);
        bindCommand("vote", voteCommand);
        bindCommand("guard", guardCommand);
    }

    private void bindCommand(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command " + name + " is not declared in plugin.yml");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
