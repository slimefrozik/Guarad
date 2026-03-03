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
        sender.sendMessage("VoteBan command is wired. Active sessions: " + sessionManager.getActiveSessionCount());
        voteManager.touch();
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
