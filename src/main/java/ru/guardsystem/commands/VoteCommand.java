package ru.guardsystem.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.SessionManager;

import java.util.List;
import java.util.Locale;

public class VoteCommand implements TabExecutor {

    private static final List<String> OPTIONS = List.of("yes", "no");

    private final SessionManager sessionManager;
    private final GuardManager guardManager;

    public VoteCommand(SessionManager sessionManager, GuardManager guardManager) {
        this.sessionManager = sessionManager;
        this.guardManager = guardManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || (!"yes".equalsIgnoreCase(args[0]) && !"no".equalsIgnoreCase(args[0]))) {
            sender.sendMessage("Использование: /vote <yes|no>");
            return true;
        }

        if (!guardManager.isGuard(sender.getName())) {
            sender.sendMessage("Голосовать могут только Guard.");
            return true;
        }

        boolean voteYes = "yes".equalsIgnoreCase(args[0]);
        SessionManager.VoteResult result = sessionManager.voteRollback(sender.getName(), voteYes, guardManager.getActiveGuardCount());

        if (!result.hasSession()) {
            sender.sendMessage("Нет активной ROLLBACK-сессии.");
            return true;
        }
        if (result.alreadyApproved()) {
            sender.sendMessage("Сессия уже одобрена. Выполните /guard rollback execute.");
            return true;
        }

        sender.sendMessage("Голос принят. yes=" + result.yesVotes() + " no=" + result.noVotes() +
            " required=" + result.requiredVotes() + " (activeGuards=" + result.activeGuards() + ")");

        if (result.session().isApproved()) {
            sender.sendMessage("ROLLBACK-сессия одобрена. Доступно: /guard rollback execute");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return OPTIONS;
        }
        return List.of();
    }
}
