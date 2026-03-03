package ru.guardsystem.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.GuardManager;
import ru.guardsystem.service.SessionManager;

import java.util.Collections;
import java.util.List;

public class GuardCommand implements TabExecutor {

    private final GuardManager guardManager;
    private final SessionManager sessionManager;

    public GuardCommand(GuardManager guardManager, SessionManager sessionManager) {
        this.guardManager = guardManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sessionId = sessionManager.startSession("guard", sender.getName());

        if (args.length >= 3 && "rollback".equalsIgnoreCase(args[0]) && "request".equalsIgnoreCase(args[1])) {
            guardManager.requestRollback(sessionId, sender.getName(), args[2]);
            sender.sendMessage("Rollback requested for " + args[2]);
        } else if (args.length >= 3 && "rollback".equalsIgnoreCase(args[0]) && "approve".equalsIgnoreCase(args[1])) {
            guardManager.approveRollback(sessionId, sender.getName(), args[2]);
            sender.sendMessage("Rollback approved for " + args[2]);
        } else if (args.length >= 3 && "transfer".equalsIgnoreCase(args[0])) {
            guardManager.transferGuard(sessionId, sender.getName(), args[1], args[2]);
            sender.sendMessage("Guard transferred from " + args[1] + " to " + args[2]);
        } else if (args.length >= 3 && "impeach".equalsIgnoreCase(args[0]) && "start".equalsIgnoreCase(args[1])) {
            guardManager.startImpeach(sessionId, sender.getName(), args[2]);
            sender.sendMessage("Impeach started for " + args[2]);
        } else if (args.length >= 4 && "impeach".equalsIgnoreCase(args[0]) && "result".equalsIgnoreCase(args[1])) {
            guardManager.registerImpeachResult(sessionId, sender.getName(), args[2], args[3]);
            sender.sendMessage("Impeach result for " + args[2] + ": " + args[3]);
        } else {
            sender.sendMessage("Usage: /guard rollback request|approve <target> | transfer <from> <to> | impeach start <target> | impeach result <target> <result>");
        }

        sessionManager.finishSession(sessionId);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
