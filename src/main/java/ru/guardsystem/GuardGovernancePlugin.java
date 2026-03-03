package ru.guardsystem;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.guardsystem.commands.GuardCommand;
import ru.guardsystem.commands.VoteBanCommand;
import ru.guardsystem.commands.VoteCommand;
import ru.guardsystem.listener.GuardGuiListener;
import ru.guardsystem.listener.GuardPermissionListener;
import ru.guardsystem.service.AuditLogger;
import ru.guardsystem.service.CoreProtectService;
import ru.guardsystem.service.GuardGuiService;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.GuardPermissionService;
import ru.guardsystem.service.PersistenceLayer;
import ru.guardsystem.service.SessionManager;
import ru.guardsystem.service.VoteManager;

public final class GuardGovernancePlugin extends JavaPlugin {

    private PersistenceLayer persistenceLayer;
    private AuditLogger auditLogger;
    private GuardManager guardManager;
    private VoteManager voteManager;
    private SessionManager sessionManager;
    private CoreProtectService coreProtectService;
    private GuardGuiService guardGuiService;
    private GuardPermissionService guardPermissionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.persistenceLayer = new PersistenceLayer(this);
        this.persistenceLayer.initializeStorage();

        this.auditLogger = new AuditLogger(getDataFolder().toPath().resolve("logs"));
        this.guardManager = new GuardManager(persistenceLayer, auditLogger);
        this.voteManager = new VoteManager(this, persistenceLayer, auditLogger, guardManager);
        this.sessionManager = new SessionManager(auditLogger);
        this.coreProtectService = new CoreProtectService(this, auditLogger);
        this.guardGuiService = new GuardGuiService(guardManager);
        this.guardPermissionService = new GuardPermissionService(this, guardManager);

        this.guardManager.load();
        this.voteManager.load();
        this.sessionManager.load();
        this.coreProtectService.initialize();

        registerCommands();
        registerListeners();
        guardPermissionService.syncOnlinePlayers();
        getLogger().info("GuardGovernance enabled.");
    }

    @Override
    public void onDisable() {
        if (guardPermissionService != null) {
            guardPermissionService.clear();
        }
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
        VoteBanCommand voteBanCommand = new VoteBanCommand(voteManager);
        VoteCommand voteCommand = new VoteCommand(sessionManager, guardManager);
        GuardCommand guardCommand = new GuardCommand(guardManager, sessionManager, coreProtectService, guardGuiService, guardPermissionService);

        bindCommand("voteban", voteBanCommand);
        bindCommand("vote", voteCommand);
        bindCommand("guard", guardCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GuardGuiListener(guardManager, guardGuiService, guardPermissionService), this);
        getServer().getPluginManager().registerEvents(new GuardPermissionListener(guardPermissionService), this);
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
