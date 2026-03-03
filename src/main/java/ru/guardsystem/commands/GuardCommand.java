package ru.guardsystem.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.CoreProtectService;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.SessionManager;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class GuardCommand implements TabExecutor {

    private final GuardManager guardManager;
    private final SessionManager sessionManager;
    private final CoreProtectService coreProtectService;

    public GuardCommand(GuardManager guardManager, SessionManager sessionManager, CoreProtectService coreProtectService) {
        this.guardManager = guardManager;
        this.sessionManager = sessionManager;
        this.coreProtectService = coreProtectService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Использование: /guard inspect <player> <radius> <hours> | /guard rollback <request|execute|status> ...");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "inspect" -> handleInspect(player, args);
            case "rollback" -> handleRollback(player, args);
            default -> {
                sender.sendMessage("Неизвестная подкоманда guard.");
                yield true;
            }
        };
    }

    private boolean handleInspect(Player sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage("Использование: /guard inspect <player> <radius> <hours>");
            return true;
        }

        String target = args[1];
        Integer radius = parsePositiveInt(args[2]);
        Integer hours = parsePositiveInt(args[3]);
        if (radius == null || hours == null) {
            sender.sendMessage("radius/hours должны быть положительными числами.");
            return true;
        }

        int seconds = Math.toIntExact(Duration.of(hours, ChronoUnit.HOURS).toSeconds());
        if (!sessionManager.validateRollbackLimits(radius, seconds)) {
            sender.sendMessage("Лимиты превышены: radius <= 50, глубина <= 7 дней.");
            return true;
        }

        List<?> results = coreProtectService.inspect(sender, target, radius, seconds, sender.getLocation());
        sender.sendMessage("Inspect result count: " + results.size());
        return true;
    }

    private boolean handleRollback(Player sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Использование: /guard rollback <request|execute|status>");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "request" -> handleRollbackRequest(sender, args);
            case "execute" -> handleRollbackExecute(sender);
            case "status" -> handleRollbackStatus(sender);
            default -> {
                sessionManager.logRollbackBypassAttempt(sender.getName(), "unknown rollback action=" + args[1]);
                sender.sendMessage("Неверный rollback action.");
                yield true;
            }
        };
    }

    private boolean handleRollbackRequest(Player sender, String[] args) {
        if (!guardManager.isGuard(sender.getName())) {
            sender.sendMessage("Создавать rollback-сессии могут только Guard.");
            return true;
        }

        if (args.length != 5) {
            sender.sendMessage("Использование: /guard rollback request <player> <radius> <hours>");
            return true;
        }

        String target = args[2];
        Integer radius = parsePositiveInt(args[3]);
        Integer hours = parsePositiveInt(args[4]);
        if (radius == null || hours == null) {
            sender.sendMessage("radius/hours должны быть положительными числами.");
            return true;
        }

        int seconds = Math.toIntExact(Duration.of(hours, ChronoUnit.HOURS).toSeconds());
        if (!sessionManager.validateRollbackLimits(radius, seconds)) {
            sender.sendMessage("Лимиты rollback: radius <= 50, глубина <= 7 дней.");
            return true;
        }

        SessionManager.RollbackSession session = sessionManager.startRollbackSession(sender.getName(), target, radius, seconds);
        sender.sendMessage("Создана ROLLBACK-сессия id=" + session.getId() + ". Голосуйте /vote yes|no (порог 2/3 от active Guard).");
        return true;
    }

    private boolean handleRollbackExecute(Player sender) {
        SessionManager.RollbackSession session = sessionManager.getRollbackSession();
        if (session == null) {
            sessionManager.logRollbackBypassAttempt(sender.getName(), "rollback without session");
            sender.sendMessage("Rollback запрещен: нет созданной/одобренной сессии.");
            return true;
        }
        if (!session.isApproved()) {
            sessionManager.logRollbackBypassAttempt(sender.getName(), "rollback without approval; sessionId=" + session.getId());
            sender.sendMessage("Rollback запрещен: сессия не одобрена.");
            return true;
        }

        boolean success = coreProtectService.rollback(sender, session.getTarget(), session.getRadius(), session.getTimeSeconds(), sender.getLocation());
        sender.sendMessage(success ? "Rollback выполнен." : "Rollback не выполнен (см. лог).");
        if (success) {
            sessionManager.clearRollbackSession();
        }
        return true;
    }

    private boolean handleRollbackStatus(Player sender) {
        SessionManager.RollbackSession session = sessionManager.getRollbackSession();
        if (session == null) {
            sender.sendMessage("Активной rollback-сессии нет.");
            return true;
        }

        sender.sendMessage("ROLLBACK session id=" + session.getId() +
            " initiator=" + session.getInitiator() +
            " target=" + session.getTarget() +
            " radius=" + session.getRadius() +
            " seconds=" + session.getTimeSeconds() +
            " approved=" + session.isApproved() +
            " votes=" + session.getVoteCount() +
            " created=" + session.getCreatedAt());
        return true;
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("inspect", "rollback");
        }
        if (args.length == 2 && "rollback".equalsIgnoreCase(args[0])) {
            return List.of("request", "execute", "status");
        }
        return List.of();
    }
}
