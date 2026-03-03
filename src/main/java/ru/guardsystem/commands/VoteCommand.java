package ru.guardsystem.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.VoteManager;

import java.util.List;
import java.util.Locale;

public class VoteCommand implements TabExecutor {

    private static final List<String> OPTIONS = List.of("yes", "no");
    private final VoteManager voteManager;

    public VoteCommand(VoteManager voteManager) {
        this.voteManager = voteManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[Vote] Только игрок может голосовать.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("[Vote] Использование: /vote yes|no");
            return true;
        }

        VoteManager.VoteChoice choice;
        String normalized = args[0].toLowerCase(Locale.ROOT);
        if ("yes".equals(normalized)) {
            choice = VoteManager.VoteChoice.YES;
        } else if ("no".equals(normalized)) {
            choice = VoteManager.VoteChoice.NO;
        } else {
            sender.sendMessage("[Vote] Допустимые значения: yes | no");
            return true;
        }

        VoteManager.VoteResult result = voteManager.castVote(player, choice);
        sender.sendMessage(result.message());
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
