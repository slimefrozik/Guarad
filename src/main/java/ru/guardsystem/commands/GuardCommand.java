package ru.guardsystem.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.CoreProtectService;
import ru.guardsystem.service.GuardGuiService;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.GuardPermissionService;
import ru.guardsystem.service.SessionManager;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class GuardCommand implements TabExecutor {

    private final GuardManager guardManager;
    private final SessionManager sessionManager;
    private final CoreProtectService coreProtectService;
    private final GuardGuiService guardGuiService;
    private final GuardPermissionService guardPermissionService;

    public GuardCommand(GuardManager guardManager,
                        SessionManager sessionManager,
                        CoreProtectService coreProtectService,
                        GuardGuiService guardGuiService,
                        GuardPermissionService guardPermissionService) {
        this.guardManager = guardManager;
        this.sessionManager = sessionManager;
        this.coreProtectService = coreProtectService;
        this.guardGuiService = guardGuiService;
        this.guardPermissionService = guardPermissionService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда доступна только игрокам.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Использование: /guard gui | /guard admin <add|remove> <игрок> | /guard transfer <игрок> | /guard rollback <request|execute|status>");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "gui" -> handleGui(player);
            case "admin" -> handleAdmin(player, args);
            case "transfer" -> handleTransfer(player, args);
            case "inspect" -> handleInspect(player);
            case "rollback" -> handleRollback(player, args);
            default -> {
                sender.sendMessage("Неизвестная подкоманда guard.");
                yield true;
            }
        };
    }

    private boolean handleGui(Player sender) {
        guardGuiService.openMainMenu(sender);
        return true;
    }

    private boolean handleInspect(Player sender) {
        sender.sendMessage("Guard теперь могут использовать CoreProtect напрямую: /co inspect");
        return true;
    }

    private boolean handleAdmin(Player sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("Эта команда доступна только OP-админам.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage("Использование: /guard admin <add|remove> <игрок>");
            return true;
        }

        String action = args[1].toLowerCase();
        String target = args[2];
        boolean changed;

        if ("add".equals(action)) {
            changed = guardManager.addGuard(target);
            sender.sendMessage(changed ? "Игрок " + target + " добавлен в Guard." : "Игрок уже находится в Guard.");
        } else if ("remove".equals(action)) {
            changed = guardManager.removeGuard(target);
            sender.sendMessage(changed ? "Игрок " + target + " удалён из Guard." : "Игрок не состоит в Guard.");
        } else {
            sender.sendMessage("Использование: /guard admin <add|remove> <игрок>");
            return true;
        }

        guardManager.save();
        Player onlineTarget = Bukkit.getPlayerExact(target);
        if (onlineTarget != null) {
            guardPermissionService.syncPlayer(onlineTarget);
        }
        return true;
    }

    private boolean handleTransfer(Player sender, String[] args) {
        if (!guardManager.isGuard(sender.getName())) {
            sender.sendMessage("Передавать роль могут только Guard.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("Использование: /guard transfer <игрок>");
            return true;
        }

        String target = args[1];
        if (sender.getName().equalsIgnoreCase(target)) {
            sender.sendMessage("Нельзя передать роль самому себе.");
            return true;
        }

        if (guardManager.isGuard(target)) {
            sender.sendMessage("Этот игрок уже Guard.");
            return true;
        }

        if (!guardManager.transferGuardRole(sender.getName(), target)) {
            sender.sendMessage("Не удалось передать роль Guard.");
            return true;
        }

        guardManager.save();
        guardPermissionService.syncPlayer(sender);
        Player onlineTarget = Bukkit.getPlayerExact(target);
        if (onlineTarget != null) {
            guardPermissionService.syncPlayer(onlineTarget);
        }
        sender.sendMessage("Роль Guard передана игроку " + target + ".");
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
                sender.sendMessage("Неверное действие rollback.");
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
            sender.sendMessage("Использование: /guard rollback request <игрок> <radius> <hours>");
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
        sender.sendMessage("Создана ROLLBACK-сессия id=" + session.getId() + ". Голосуйте /vote yes|no (порог 2/3 от активных Guard).");
        return true;
    }

    private boolean handleRollbackExecute(Player sender) {
        SessionManager.RollbackSession session = sessionManager.getRollbackSession();
        if (session == null) {
            sessionManager.logRollbackBypassAttempt(sender.getName(), "rollback without session");
            sender.sendMessage("Rollback запрещён: нет созданной/одобренной сессии.");
            return true;
        }
        if (!session.isApproved()) {
            sessionManager.logRollbackBypassAttempt(sender.getName(), "rollback without approval; sessionId=" + session.getId());
            sender.sendMessage("Rollback запрещён: сессия не одобрена.");
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

        sender.sendMessage("ROLLBACK сессия id=" + session.getId() +
            " инициатор=" + session.getInitiator() +
            " цель=" + session.getTarget() +
            " радиус=" + session.getRadius() +
            " секунд=" + session.getTimeSeconds() +
            " одобрено=" + session.isApproved() +
            " голосов=" + session.getVoteCount() +
            " создано=" + session.getCreatedAt());
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
            return List.of("gui", "admin", "transfer", "rollback");
        }

        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return List.of("add", "remove");
        }

        if (args.length == 2 && "rollback".equalsIgnoreCase(args[0])) {
            return List.of("request", "execute", "status");
        }

        if (args.length == 3 && "admin".equalsIgnoreCase(args[0])) {
            return new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        if (args.length == 2 && "transfer".equalsIgnoreCase(args[0])) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !guardManager.isGuard(name))
                .toList();
        }

        return List.of();
    }
}
