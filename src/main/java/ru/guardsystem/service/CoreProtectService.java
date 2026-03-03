package ru.guardsystem.service;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public class CoreProtectService {

    private final JavaPlugin plugin;
    private final AuditLogger auditLogger;

    private Object coreProtectApi;
    private Method performLookupMethod;
    private Method performRollbackMethod;
    private boolean available;

    public CoreProtectService(JavaPlugin plugin, AuditLogger auditLogger) {
        this.plugin = plugin;
        this.auditLogger = auditLogger;
    }

    public void initialize() {
        this.available = false;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        Plugin coreProtectPlugin = pluginManager.getPlugin("CoreProtect");
        if (coreProtectPlugin == null || !coreProtectPlugin.isEnabled()) {
            auditLogger.log("CoreProtect unavailable: plugin is missing or disabled. Running in graceful-degrade mode.");
            return;
        }

        try {
            Method getApiMethod = coreProtectPlugin.getClass().getMethod("getAPI");
            Object api = getApiMethod.invoke(coreProtectPlugin);
            Method isEnabledMethod = api.getClass().getMethod("isEnabled");
            boolean isEnabled = (boolean) isEnabledMethod.invoke(api);
            if (!isEnabled) {
                auditLogger.log("CoreProtect unavailable: API exists but disabled. Running in graceful-degrade mode.");
                return;
            }

            this.performLookupMethod = api.getClass().getMethod(
                "performLookup",
                int.class,
                List.class,
                List.class,
                List.class,
                List.class,
                int.class,
                Location.class
            );
            this.performRollbackMethod = api.getClass().getMethod(
                "performRollback",
                int.class,
                List.class,
                List.class,
                List.class,
                List.class,
                int.class,
                Location.class
            );

            this.coreProtectApi = api;
            this.available = true;
            auditLogger.log("CoreProtect API connected successfully.");
        } catch (Exception e) {
            auditLogger.log("CoreProtect unavailable: " + e.getMessage() + ". Running in graceful-degrade mode.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public List<?> inspect(CommandSender sender, String target, int radius, int timeSeconds, Location location) {
        if (!available) {
            sender.sendMessage("CoreProtect недоступен: inspect работает в режиме graceful-degrade.");
            auditLogger.log("Inspect skipped because CoreProtect is unavailable.");
            return List.of();
        }

        try {
            Object result = performLookupMethod.invoke(
                coreProtectApi,
                timeSeconds,
                List.of(target),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(0, 1, 2),
                radius,
                location
            );
            if (result instanceof List<?> list) {
                auditLogger.log("Inspect requested for target=" + target + ", radius=" + radius + ", seconds=" + timeSeconds + ", results=" + list.size());
                return list;
            }
            return List.of();
        } catch (Exception e) {
            auditLogger.log("Inspect failed: " + e.getMessage());
            sender.sendMessage("Ошибка inspect через CoreProtect API: " + e.getMessage());
            return List.of();
        }
    }

    public boolean rollback(CommandSender sender, String target, int radius, int timeSeconds, Location location) {
        if (!available) {
            sender.sendMessage("CoreProtect недоступен: rollback пропущен (graceful-degrade).");
            auditLogger.log("Rollback skipped because CoreProtect is unavailable.");
            return false;
        }

        try {
            Object result = performRollbackMethod.invoke(
                coreProtectApi,
                timeSeconds,
                List.of(target),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of(0, 1, 2),
                radius,
                location
            );
            boolean success = result instanceof Boolean b && b;
            auditLogger.log("Rollback executed for target=" + target + ", radius=" + radius + ", seconds=" + timeSeconds + ", success=" + success);
            return success;
        } catch (Exception e) {
            auditLogger.log("Rollback failed: " + e.getMessage());
            sender.sendMessage("Ошибка rollback через CoreProtect API: " + e.getMessage());
            return false;
        }
    }
}
