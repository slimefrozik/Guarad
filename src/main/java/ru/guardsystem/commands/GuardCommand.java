package ru.guardsystem.commands;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.VoteManager;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class GuardCommand implements TabExecutor {

    private static final long MIN_ONLINE_DAYS_FOR_OPEN_VOTE = 30;

    private final JavaPlugin plugin;
    private final GuardManager guardManager;
    private final VoteManager voteManager;

    public GuardCommand(JavaPlugin plugin, GuardManager guardManager, VoteManager voteManager) {
        this.plugin = plugin;
        this.guardManager = guardManager;
        this.voteManager = voteManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            sender.sendMessage("[Guard] Использование: /guard nominate <nick> | /guard impeach <nick>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String target = args[1];

        if (!(sender instanceof Player player)) {
            sender.sendMessage("[Guard] Команда только для игроков.");
            return true;
        }

        if ("nominate".equals(action)) {
            handleNominate(player, target);
            return true;
        }

        if ("impeach".equals(action)) {
            handleImpeach(player, target);
            return true;
        }

        sender.sendMessage("[Guard] Неизвестное действие. Доступно: nominate, impeach");
        return true;
    }

    private void handleNominate(Player initiator, String target) {
        int guards = guardManager.guardCount();
        String emergencyOwner = plugin.getConfig().getString("emergency-owner", "").toLowerCase(Locale.ROOT);
        boolean isEmergencyOwner = initiator.getName().equalsIgnoreCase(emergencyOwner);

        VoteManager.VoteType voteType;
        java.util.function.Predicate<Player> eligibility;

        if (guards == 0) {
            long daysOnline = playtimeDays(initiator);
            if (!isEmergencyOwner && daysOnline < MIN_ONLINE_DAYS_FOR_OPEN_VOTE) {
                initiator.sendMessage("[Guard] Guard=0. Нужен online >= " + MIN_ONLINE_DAYS_FOR_OPEN_VOTE + " дн. Сейчас: " + daysOnline + " дн.");
                return;
            }
            voteType = VoteManager.VoteType.OPEN_GUARD_NOMINATE;
            eligibility = p -> true;
        } else {
            if (!guardManager.isGuard(initiator.getName())) {
                initiator.sendMessage("[Guard] Только действующий Guard может выдвигать кандидата.");
                return;
            }
            voteType = VoteManager.VoteType.GUARD_NOMINATE;
            eligibility = p -> guardManager.isGuard(p.getName());
        }

        Optional<String> error = voteManager.startVote(voteType, target, "guard nomination", initiator, eligibility);
        if (error.isPresent()) {
            initiator.sendMessage(error.get());
            return;
        }

        voteManager.activeSnapshot().ifPresent(snapshot ->
                initiator.sendMessage("[Guard] Старт " + snapshot.type() + ": target=" + snapshot.target()
                        + " | yes=0 no=0 | таймер=" + snapshot.secondsLeft() + "с"));
    }

    private void handleImpeach(Player initiator, String target) {
        if (guardManager.guardCount() == 0) {
            initiator.sendMessage("[Guard] Нельзя объявить импичмент: состав Guard пуст.");
            return;
        }
        if (!guardManager.isGuard(initiator.getName())) {
            initiator.sendMessage("[Guard] Только действующий Guard может инициировать импичмент.");
            return;
        }

        Optional<String> error = voteManager.startVote(
                VoteManager.VoteType.GUARD_IMPEACH,
                target,
                "guard impeachment",
                initiator,
                p -> guardManager.isGuard(p.getName())
        );

        if (error.isPresent()) {
            initiator.sendMessage(error.get());
            return;
        }

        voteManager.activeSnapshot().ifPresent(snapshot ->
                initiator.sendMessage("[Guard] Старт impeach: target=" + snapshot.target()
                        + " | yes=0 no=0 | таймер=" + snapshot.secondsLeft() + "с"));
    }

    private long playtimeDays(Player player) {
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return ticks / 20L / 60L / 60L / 24L;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("nominate", "impeach");
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return List.of();
    }
}
