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

    public static final String MAIN_TITLE = "§0Меню управления";
    public static final String TRANSFER_TITLE = "§0Передача роли Guard";
    public static final String VOTEBAN_TITLE = "§0VoteBan: выбор цели";
    public static final String LOOT_LIST_TITLE = "§0Конфискат игроков";

    private final GuardManager guardManager;
    private final VoteManager voteManager;
    private final BanInventoryService banInventoryService;

    public GuardGuiService(GuardManager guardManager, VoteManager voteManager, BanInventoryService banInventoryService) {
        this.guardManager = guardManager;
        this.voteManager = voteManager;
        this.banInventoryService = banInventoryService;
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN_TITLE);

        inventory.setItem(10, menuItem(Material.BOOK, "§eСостав Guard",
                "§7Guard всего: §f" + guardManager.guardCount(),
                "§7Показать список в чате"));

        inventory.setItem(11, menuItem(Material.NAME_TAG, "§bПередать роль Guard",
                "§7Только для текущих Guard",
                "§7Открыть список игроков"));

        inventory.setItem(12, menuItem(Material.PLAYER_HEAD, "§dНачать VoteBan",
                "§7Открыть список игроков",
                "§7Причина: §fЧерез GUI"));

        voteManager.activeSnapshot().ifPresent(snapshot -> inventory.setItem(14, menuItem(Material.CLOCK, "§6Активное голосование",
                "§7Тип: §f" + voteManager.label(snapshot.type()),
                "§7Цель: §f" + snapshot.target(),
                "§7Причина: §f" + snapshot.reason(),
                "§7Да: §a" + snapshot.yes() + "  §7Нет: §c" + snapshot.no(),
                "§7Осталось: §f" + snapshot.secondsLeft() + "с")));

        inventory.setItem(15, menuItem(Material.LIME_WOOL, "§aГолосовать ЗА", "§7Нажмите для /vote yes"));
        inventory.setItem(16, menuItem(Material.RED_WOOL, "§cГолосовать ПРОТИВ", "§7Нажмите для /vote no"));

        inventory.setItem(18, menuItem(Material.CHEST, "§9Конфискат офлайн-банов",
                "§7Доступно только OP",
                "§7Открыть сохранённые вещи"));

        inventory.setItem(26, menuItem(Material.BARRIER, "§cЗакрыть"));

        player.openInventory(inventory);
    }

    public void openTransferMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, TRANSFER_TITLE);
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId()) || guardManager.isGuard(online.getName())) {
                continue;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, playerHead("§a" + online.getName(), "§7Передать роль этому игроку"));
            slot++;
        }
        player.openInventory(inventory);
    }

    public void openVoteBanMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, VOTEBAN_TITLE);
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, playerHead("§d" + online.getName(), "§7Начать голосование за бан"));
            slot++;
        }
        player.openInventory(inventory);
    }

    public void openLootListMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, LOOT_LIST_TITLE);
        int slot = 0;
        for (BanInventoryService.ConfiscatedInventory confiscated : banInventoryService.listConfiscatedInventories()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, playerHead("§6" + confiscated.playerName(),
                    "§7Причина: §f" + confiscated.reason(),
                    "§7Вещей: §f" + confiscated.items().size(),
                    confiscated.returnedToWorld()
                            ? "§aСтатус: ✔ Возвращено в мир"
                            : "§eСтатус: Не возвращено",
                    confiscated.returnedBy() == null
                            ? "§7Отметка: §f-"
                            : "§7Отметил: §f" + confiscated.returnedBy(),
                    "§7Нажмите, чтобы открыть"));
            slot++;
        }
        player.openInventory(inventory);
    }

    private ItemStack playerHead(String name, String... loreLines) {
        return menuItem(Material.PLAYER_HEAD, name, loreLines);
    }

    private ItemStack menuItem(Material material, String displayName, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(displayName);
        List<String> lore = new ArrayList<>();
        lore.addAll(List.of(loreLines));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
