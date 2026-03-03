package ru.guardsystem.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BanInventoryService implements Listener {

    private final Map<UUID, List<ItemStack>> cachedInventories = new HashMap<>();
    private final Map<UUID, ConfiscatedInventory> confiscatedInventories = new HashMap<>();

    public void cacheOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            cachePlayerInventory(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cachePlayerInventory(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cachePlayerInventory(event.getPlayer());
    }

    public void handleOnlineBan(Player target) {
        List<ItemStack> loot = collectAllItems(target);
        for (ItemStack item : loot) {
            Item dropped = target.getWorld().dropItemNaturally(target.getLocation(), item);
            dropped.setOwner(null);
        }

        target.getInventory().clear();
        target.getInventory().setArmorContents(new ItemStack[4]);
        target.getInventory().setExtraContents(new ItemStack[1]);
        target.setHealth(0.0);
    }

    public boolean handleOfflineBan(String playerName, String reason) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.getUniqueId() == null) {
            return false;
        }

        List<ItemStack> cached = cachedInventories.getOrDefault(offlinePlayer.getUniqueId(), List.of());
        confiscatedInventories.put(offlinePlayer.getUniqueId(), new ConfiscatedInventory(playerName, reason, copyItems(cached)));
        return true;
    }

    public List<ConfiscatedInventory> listConfiscatedInventories() {
        return confiscatedInventories.values().stream()
                .sorted(Comparator.comparing(ConfiscatedInventory::playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<ConfiscatedInventory> getConfiscatedInventory(UUID playerId) {
        return Optional.ofNullable(confiscatedInventories.get(playerId));
    }

    public Inventory buildConfiscatedInventoryView(ConfiscatedInventory confiscatedInventory) {
        Inventory inventory = Bukkit.createInventory(null, 54, "§0Конфискат: " + confiscatedInventory.playerName());
        int slot = 0;
        for (ItemStack stack : confiscatedInventory.items()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, stack);
            slot++;
        }
        return inventory;
    }

    private void cachePlayerInventory(Player player) {
        cachedInventories.put(player.getUniqueId(), collectAllItems(player));
    }

    private List<ItemStack> collectAllItems(Player player) {
        List<ItemStack> items = new ArrayList<>();
        addNotEmpty(items, player.getInventory().getContents());
        addNotEmpty(items, player.getInventory().getArmorContents());
        addNotEmpty(items, player.getInventory().getExtraContents());
        return copyItems(items);
    }

    private void addNotEmpty(List<ItemStack> list, ItemStack[] items) {
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            list.add(item.clone());
        }
    }

    private List<ItemStack> copyItems(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : source) {
            copy.add(item.clone());
        }
        return copy;
    }

    public record ConfiscatedInventory(String playerName, String reason, List<ItemStack> items) {
    }
}
