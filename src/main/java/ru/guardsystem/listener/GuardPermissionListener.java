package ru.guardsystem.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.guardsystem.service.GuardPermissionService;

public class GuardPermissionListener implements Listener {

    private final GuardPermissionService guardPermissionService;

    public GuardPermissionListener(GuardPermissionService guardPermissionService) {
        this.guardPermissionService = guardPermissionService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        guardPermissionService.syncPlayer(event.getPlayer());
    }
}
