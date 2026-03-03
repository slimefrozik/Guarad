package ru.guardsystem.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GuardGuiService {

    private final GuardManager guardManager;

    public GuardGuiService(GuardManager guardManager) {
        this.guardManager = guardManager;
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, "Guard меню");

        ItemStack listItem = new ItemStack(Material.BOOK);
        ItemMeta listMeta = listItem.getItemMeta();
        listMeta.setDisplayName("§eСостав Guard");
        List<String> listLore = new ArrayList<>();
        listLore.add("§7Guard всего: §f" + guardManager.guardCount());
        listLore.add("§7Нажмите, чтобы вывести список в чат");
        listMeta.setLore(listLore);
        listItem.setItemMeta(listMeta);

        ItemStack transferItem = new ItemStack(Material.NAME_TAG);
        ItemMeta transferMeta = transferItem.getItemMeta();
        transferMeta.setDisplayName("§bПередать роль Guard");
        List<String> transferLore = new ArrayList<>();
        transferLore.add("§7Только для текущих Guard");
        transferLore.add("§7Открывает выбор игрока");
        transferMeta.setLore(transferLore);
        transferItem.setItemMeta(transferMeta);

        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§cЗакрыть");
        closeItem.setItemMeta(closeMeta);

        inventory.setItem(11, listItem);
        inventory.setItem(13, transferItem);
        inventory.setItem(15, closeItem);

        player.openInventory(inventory);
    }

    public void openTransferMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, "Передача Guard");
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (guardManager.isGuard(online.getName())) {
                continue;
            }
            if (slot >= inventory.getSize()) {
                break;
            }

            ItemStack candidate = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = candidate.getItemMeta();
            meta.setDisplayName("§a" + online.getName());
            meta.setLore(List.of("§7Нажмите, чтобы передать роль этому игроку"));
            candidate.setItemMeta(meta);
            inventory.setItem(slot, candidate);
            slot++;
        }
        player.openInventory(inventory);
    }
}
