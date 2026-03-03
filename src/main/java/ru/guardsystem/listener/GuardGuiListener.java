package ru.guardsystem.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.guardsystem.service.BanInventoryService;
import ru.guardsystem.service.GuardGuiService;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.GuardPermissionService;
import ru.guardsystem.service.VoteManager;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class GuardGuiListener implements Listener {

    private final GuardManager guardManager;
    private final GuardGuiService guardGuiService;
    private final GuardPermissionService guardPermissionService;
    private final VoteManager voteManager;
    private final BanInventoryService banInventoryService;

    public GuardGuiListener(GuardManager guardManager,
                            GuardGuiService guardGuiService,
                            GuardPermissionService guardPermissionService,
                            VoteManager voteManager,
                            BanInventoryService banInventoryService) {
        this.guardManager = guardManager;
        this.guardGuiService = guardGuiService;
        this.guardPermissionService = guardPermissionService;
        this.voteManager = voteManager;
        this.banInventoryService = banInventoryService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        boolean confiscatedView = title.startsWith("§0Конфискат: ");
        boolean managedTitle = GuardGuiService.MAIN_TITLE.equals(title)
                || GuardGuiService.TRANSFER_TITLE.equals(title)
                || GuardGuiService.VOTEBAN_TITLE.equals(title)
                || GuardGuiService.LOOT_LIST_TITLE.equals(title)
                || confiscatedView;

        if (!managedTitle) {
            return;
        }

        if (confiscatedView) {
            if (!player.isOp()) {
                event.setCancelled(true);
                player.sendMessage("Доступно только OP-админам.");
                return;
            }

            String playerName = title.substring("§0Конфискат: ".length());
            Optional<UUID> targetId = banInventoryService.getConfiscatedPlayerIdByName(playerName);
            if (targetId.isEmpty()) {
                event.setCancelled(true);
                player.sendMessage("Не удалось определить игрока конфиската.");
                return;
            }

            Optional<BanInventoryService.ConfiscatedInventory> confiscated = banInventoryService.getConfiscatedInventory(targetId.get());
            if (confiscated.isEmpty()) {
                event.setCancelled(true);
                player.sendMessage("Конфискат не найден.");
                return;
            }

            if (event.getRawSlot() == 53) {
                event.setCancelled(true);
                if (banInventoryService.markLootReturned(targetId.get(), player.getName())) {
                    player.sendMessage("Конфискат отмечен как возвращённый в мир.");
                    banInventoryService.getConfiscatedInventory(targetId.get())
                            .ifPresent(updated -> player.openInventory(banInventoryService.buildConfiscatedInventoryView(updated)));
                } else {
                    player.sendMessage("Этот конфискат уже отмечен как возвращённый.");
                }
                return;
            }

            if (confiscated.get().returnedToWorld()) {
                event.setCancelled(true);
                player.sendMessage("Вещи уже отмечены как возвращённые в мир. Выдача заблокирована.");
            }
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

        String displayName = stripColor(meta.getDisplayName());

        if (GuardGuiService.MAIN_TITLE.equals(title)) {
            handleMainMenu(player, displayName);
            return;
        }

        if (GuardGuiService.TRANSFER_TITLE.equals(title)) {
            handleTransferMenu(player, displayName);
            return;
        }

        if (GuardGuiService.VOTEBAN_TITLE.equals(title)) {
            handleVoteBanMenu(player, displayName);
            return;
        }

        if (GuardGuiService.LOOT_LIST_TITLE.equals(title)) {
            handleLootListMenu(player, displayName);
        }
    }

    private void handleMainMenu(Player player, String displayName) {
        switch (displayName) {
            case "Состав Guard" -> {
                String guards = guardManager.listGuards().stream().sorted().collect(Collectors.joining(", "));
                player.sendMessage(guards.isEmpty() ? "Список Guard пуст." : "Guard: " + guards);
            }
            case "Передать роль Guard" -> {
                if (!guardManager.isGuard(player.getName())) {
                    player.sendMessage("Только Guard может передавать роль.");
                    return;
                }
                guardGuiService.openTransferMenu(player);
            }
            case "Начать VoteBan" -> {
                if (!guardManager.isGuard(player.getName()) && !player.isOp()) {
                    player.sendMessage("Только Guard или OP может начать VoteBan.");
                    return;
                }
                guardGuiService.openVoteBanMenu(player);
            }
            case "Голосовать ЗА" -> handleVoteClick(player, VoteManager.VoteChoice.YES);
            case "Голосовать ПРОТИВ" -> handleVoteClick(player, VoteManager.VoteChoice.NO);
            case "Конфискат офлайн-банов" -> {
                if (!player.isOp()) {
                    player.sendMessage("Доступно только OP-админам.");
                    return;
                }
                guardGuiService.openLootListMenu(player);
            }
            case "Закрыть" -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleTransferMenu(Player player, String target) {
        if (!guardManager.isGuard(player.getName())) {
            player.sendMessage("Только Guard может передавать роль.");
            return;
        }

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

    private void handleVoteBanMenu(Player player, String target) {
        Optional<String> error = voteManager.startVote(
                VoteManager.VoteType.VOTEBAN,
                target,
                "Через GUI (инициатор: " + player.getName() + ")",
                player,
                onlinePlayer -> true
        );

        if (error.isPresent()) {
            player.sendMessage(error.get());
            return;
        }

        voteManager.activeSnapshot().ifPresent(snapshot ->
                Bukkit.broadcastMessage("[VoteBan] Запуск: цель=" + snapshot.target() + " | причина=" + snapshot.reason()
                        + " | за=0 против=0 | таймер=" + snapshot.secondsLeft() + "с"));

        guardGuiService.openMainMenu(player);
    }

    private void handleVoteClick(Player player, VoteManager.VoteChoice choice) {
        VoteManager.VoteResult result = voteManager.castVote(player, choice);
        player.sendMessage(result.message());
        guardGuiService.openMainMenu(player);
    }

    private void handleLootListMenu(Player player, String targetName) {
        if (!player.isOp()) {
            player.sendMessage("Доступно только OP-админам.");
            return;
        }

        UUID targetId = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        if (targetId == null) {
            player.sendMessage("Не удалось открыть конфискат.");
            return;
        }

        Optional<BanInventoryService.ConfiscatedInventory> inventory = banInventoryService.getConfiscatedInventory(targetId);
        if (inventory.isEmpty()) {
            player.sendMessage("Конфискат для игрока не найден.");
            return;
        }

        player.openInventory(banInventoryService.buildConfiscatedInventoryView(inventory.get()));
    }

    private String stripColor(String value) {
        return value.replaceAll("§.", "");
    }
}
