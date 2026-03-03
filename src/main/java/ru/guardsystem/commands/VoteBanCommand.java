package ru.guardsystem.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.SessionManager;
import ru.guardsystem.service.VoteManager;

import java.util.Collections;
import java.util.List;

public class VoteBanCommand implements TabExecutor {

    private final VoteManager voteManager;
    private final SessionManager sessionManager;

    public VoteBanCommand(VoteManager voteManager, SessionManager sessionManager) {
        this.voteManager = voteManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String target = args.length > 0 ? args[0] : "unknown";
        String sessionId = sessionManager.startSession("voteban", sender.getName());
        voteManager.startVoteBan(sessionId, sender.getName(), target);
        sender.sendMessage("VoteBan initiated for " + target + ". Active sessions: " + sessionManager.getActiveSessionCount());
        sessionManager.finishSession(sessionId);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
