package ru.guardsystem.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.guardsystem.service.VoteManager;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class VoteBanCommand implements TabExecutor {

    private final VoteManager voteManager;

    public VoteBanCommand(VoteManager voteManager) {
        this.voteManager = voteManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage("[VoteBan] Использование: /voteban <nick> <reason>");
            return true;
        }

        String target = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        Optional<String> error = voteManager.startVote(
                VoteManager.VoteType.VOTEBAN,
                target,
                reason,
                sender,
                player -> true
        );

        if (error.isPresent()) {
            sender.sendMessage(error.get());
            return true;
        }

        voteManager.activeSnapshot().ifPresent(snapshot ->
                sender.sendMessage("[VoteBan] Старт: target=" + snapshot.target() + " reason=" + snapshot.reason()
                        + " | yes=0 no=0 | таймер=" + snapshot.secondsLeft() + "с"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
