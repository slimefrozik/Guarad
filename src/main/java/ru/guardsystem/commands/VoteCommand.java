package ru.guardsystem.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.SessionManager;
import ru.guardsystem.service.VoteManager;

import java.util.List;

public class VoteCommand implements TabExecutor {

    private static final List<String> OPTIONS = List.of("yes", "no");
    private final VoteManager voteManager;
    private final SessionManager sessionManager;

    public VoteCommand(VoteManager voteManager, SessionManager sessionManager) {
        this.voteManager = voteManager;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String choice = args.length > 0 ? args[0] : "abstain";
        String sessionId = sessionManager.startSession("vote", sender.getName());
        voteManager.castVote(sessionId, sender.getName(), choice);
        voteManager.registerVoteResult(sessionId, "accepted");
        sender.sendMessage("Vote recorded: " + choice);
        sessionManager.finishSession(sessionId);
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
