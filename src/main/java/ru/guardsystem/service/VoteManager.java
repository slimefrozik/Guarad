package ru.guardsystem.service;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class VoteManager {

    public enum VoteChoice {
        YES,
        NO
    }

    public enum VoteType {
        VOTEBAN,
        GUARD_NOMINATE,
        GUARD_IMPEACH,
        OPEN_GUARD_NOMINATE
    }

    public record VoteSnapshot(
            VoteType type,
            String target,
            String reason,
            int yes,
            int no,
            int total,
            long secondsLeft,
            String initiator
    ) {
    }

    private static final long DEFAULT_DURATION_SECONDS = 90;

    private final JavaPlugin plugin;
    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private final GuardManager guardManager;
    private final BanInventoryService banInventoryService;

    private ActiveVote activeVote;

    public VoteManager(JavaPlugin plugin,
                       PersistenceLayer persistenceLayer,
                       AuditLogger auditLogger,
                       GuardManager guardManager,
                       BanInventoryService banInventoryService) {
        this.plugin = plugin;
        this.persistenceLayer = persistenceLayer;
        this.auditLogger = auditLogger;
        this.guardManager = guardManager;
        this.banInventoryService = banInventoryService;
    }

    public void load() {
        persistenceLayer.loadVotesJson();
        auditLogger.log("VoteManager инициализирован");
    }

    public void save() {
        persistenceLayer.saveVotesJson("{\n  \"votes\": []\n}\n");
        auditLogger.log("VoteManager сохранён");
    }

    public synchronized Optional<String> startVote(VoteType type,
                                                   String target,
                                                   String reason,
                                                   CommandSender initiator,
                                                   Predicate<Player> eligibilityRule) {
        if (activeVote != null) {
            return Optional.of("[Голосование] Уже идёт голосование: " + label(activeVote.type) + " " + activeVote.target);
        }

        String initiatorName = initiator.getName();
        ActiveVote vote = new ActiveVote(type, target, reason, initiatorName, Instant.now().plusSeconds(DEFAULT_DURATION_SECONDS), eligibilityRule);
        vote.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> expireVote(vote), DEFAULT_DURATION_SECONDS * 20L);
        activeVote = vote;

        auditLogger.log("Старт голосования: " + type + " цель=" + target + " инициатор=" + initiatorName + " причина=" + reason);
        return Optional.empty();
    }

    public synchronized VoteResult castVote(Player voter, VoteChoice choice) {
        if (activeVote == null) {
            return VoteResult.error("[Голосование] Нет активного голосования.");
        }
        if (!activeVote.eligibilityRule.test(voter)) {
            return VoteResult.error("[Голосование] У вас нет права голоса в этом голосовании.");
        }

        activeVote.ballots.put(voter.getUniqueId(), choice);
        VoteSnapshot snapshot = snapshot();
        return VoteResult.ok("[Голосование] Принято: " + (choice == VoteChoice.YES ? "за" : "против")
                + " | цель=" + snapshot.target + " | причина=" + snapshot.reason
                + " | за=" + snapshot.yes + " против=" + snapshot.no + " всего=" + snapshot.total
                + " | осталось " + snapshot.secondsLeft + "с");
    }

    public synchronized Optional<VoteSnapshot> activeSnapshot() {
        return Optional.ofNullable(snapshot());
    }

    public synchronized void cancelActiveVote(String reason) {
        if (activeVote == null) {
            return;
        }
        ActiveVote current = activeVote;
        if (current.timeoutTask != null) {
            current.timeoutTask.cancel();
        }
        activeVote = null;
        Bukkit.broadcastMessage("[Голосование] Аннулировано: " + label(current.type) + " " + current.target + " (" + reason + ")");
        auditLogger.log("Голосование отменено: " + current.type + " цель=" + current.target + " причина=" + reason);
    }

    public String label(VoteType type) {
        return switch (type) {
            case VOTEBAN -> "VoteBan";
            case GUARD_NOMINATE, OPEN_GUARD_NOMINATE -> "Назначение Guard";
            case GUARD_IMPEACH -> "Снятие Guard";
        };
    }

    private synchronized void expireVote(ActiveVote vote) {
        if (activeVote != vote) {
            return;
        }

        VoteSnapshot snapshot = snapshot();
        boolean accepted = snapshot != null && snapshot.yes > snapshot.no;

        if (accepted && snapshot != null) {
            applyOutcome(snapshot);
            Bukkit.broadcastMessage("[Голосование] Принято: " + label(snapshot.type) + " " + snapshot.target
                    + " | причина=" + snapshot.reason + " | за=" + snapshot.yes + " против=" + snapshot.no);
            auditLogger.log("Голосование принято: " + snapshot.type + " цель=" + snapshot.target);
        } else if (snapshot != null) {
            Bukkit.broadcastMessage("[Голосование] Отклонено: " + label(snapshot.type) + " " + snapshot.target
                    + " | причина=" + snapshot.reason + " | за=" + snapshot.yes + " против=" + snapshot.no);
            auditLogger.log("Голосование отклонено: " + snapshot.type + " цель=" + snapshot.target);
        }

        activeVote = null;
    }

    private void applyOutcome(VoteSnapshot snapshot) {
        switch (snapshot.type) {
            case GUARD_NOMINATE, OPEN_GUARD_NOMINATE -> guardManager.addGuard(snapshot.target);
            case GUARD_IMPEACH -> guardManager.removeGuard(snapshot.target);
            case VOTEBAN -> {
                Player online = Bukkit.getPlayerExact(snapshot.target);
                if (online != null) {
                    banInventoryService.handleOnlineBan(online);
                } else {
                    banInventoryService.handleOfflineBan(snapshot.target, snapshot.reason);
                }

                Bukkit.getBanList(BanList.Type.NAME).addBan(snapshot.target, "VoteBan: " + snapshot.reason, null, "GuardSystem");
                Player targetOnline = Bukkit.getPlayerExact(snapshot.target);
                if (targetOnline != null && targetOnline.isOnline()) {
                    targetOnline.kickPlayer("Вы забанены голосованием. Причина: " + snapshot.reason);
                }
            }
        }
    }

    private VoteSnapshot snapshot() {
        if (activeVote == null) {
            return null;
        }
        int yes = 0;
        int no = 0;
        for (VoteChoice value : activeVote.ballots.values()) {
            if (value == VoteChoice.YES) {
                yes++;
            } else {
                no++;
            }
        }

        long left = Math.max(0, Duration.between(Instant.now(), activeVote.deadline).toSeconds());
        return new VoteSnapshot(activeVote.type, activeVote.target, activeVote.reason, yes, no, yes + no, left, activeVote.initiator);
    }

    public record VoteResult(boolean success, String message) {
        public static VoteResult ok(String message) {
            return new VoteResult(true, message);
        }

        public static VoteResult error(String message) {
            return new VoteResult(false, message);
        }
    }

    private static final class ActiveVote {
        private final VoteType type;
        private final String target;
        private final String reason;
        private final String initiator;
        private final Instant deadline;
        private final Predicate<Player> eligibilityRule;
        private final Map<UUID, VoteChoice> ballots = new HashMap<>();
        private BukkitTask timeoutTask;

        private ActiveVote(VoteType type,
                           String target,
                           String reason,
                           String initiator,
                           Instant deadline,
                           Predicate<Player> eligibilityRule) {
            this.type = type;
            this.target = target;
            this.reason = reason;
            this.initiator = initiator;
            this.deadline = deadline;
            this.eligibilityRule = eligibilityRule;
        }
    }
}
