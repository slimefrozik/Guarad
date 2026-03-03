package ru.guardsystem.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.guardsystem.service.GuardGuiService;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.GuardPermissionService;

import java.util.stream.Collectors;

public class GuardGuiListener implements Listener {

    private final GuardManager guardManager;
    private final GuardGuiService guardGuiService;
    private final GuardPermissionService guardPermissionService;

    public GuardGuiListener(GuardManager guardManager, GuardGuiService guardGuiService, GuardPermissionService guardPermissionService) {
        this.guardManager = guardManager;
        this.guardGuiService = guardGuiService;
        this.guardPermissionService = guardPermissionService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!"Guard меню".equals(title) && !"Передача Guard".equals(title)) {
            return;
        }

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return;
        }

        String displayName = meta.getDisplayName().replace("§a", "").replace("§b", "").replace("§c", "").replace("§e", "");

        if ("Guard меню".equals(title)) {
            if (displayName.equals("Состав Guard")) {
                String guards = guardManager.listGuards().stream().sorted().collect(Collectors.joining(", "));
                player.sendMessage(guards.isEmpty() ? "Список Guard пуст." : "Guard: " + guards);
                return;
            }
            if (displayName.equals("Передать роль Guard")) {
                if (!guardManager.isGuard(player.getName())) {
                    player.sendMessage("Только Guard может передавать роль.");
                    return;
                }
                guardGuiService.openTransferMenu(player);
                return;
            }
            if (displayName.equals("Закрыть")) {
                player.closeInventory();
            }
            return;
        }

        if (!guardManager.isGuard(player.getName())) {
            player.sendMessage("Только Guard может передавать роль.");
            return;
        }

        String target = displayName;
        if (guardManager.isGuard(target)) {
            player.sendMessage("Игрок уже состоит в Guard.");
            return;
        }

        if (!guardManager.transferGuardRole(player.getName(), target)) {
            player.sendMessage("Не удалось передать роль Guard.");
            return;
        }

        guardManager.save();
        guardPermissionService.syncPlayer(player);
        Player targetOnline = Bukkit.getPlayerExact(target);
        if (targetOnline != null) {
            guardPermissionService.syncPlayer(targetOnline);
            targetOnline.sendMessage("Вы получили роль Guard от " + player.getName() + ".");
        }
        player.sendMessage("Роль Guard передана игроку " + target + ".");
        player.closeInventory();
    }
}
