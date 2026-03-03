package ru.guardsystem.service;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuardPermissionService {

    private final JavaPlugin plugin;
    private final GuardManager guardManager;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public GuardPermissionService(JavaPlugin plugin, GuardManager guardManager) {
        this.plugin = plugin;
        this.guardManager = guardManager;
    }

    public void syncPlayer(Player player) {
        if (guardManager.isGuard(player.getName())) {
            PermissionAttachment attachment = attachments.computeIfAbsent(player.getUniqueId(), ignored -> player.addAttachment(plugin));
            attachment.setPermission("coreprotect.inspect", true);
            attachment.setPermission("coreprotect.lookup", true);
            return;
        }

        PermissionAttachment removed = attachments.remove(player.getUniqueId());
        if (removed != null) {
            player.removeAttachment(removed);
        }
    }

    public void syncOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            syncPlayer(player);
        }
    }

    public void clear() {
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                player.removeAttachment(entry.getValue());
            }
        }
        attachments.clear();
    }
}
