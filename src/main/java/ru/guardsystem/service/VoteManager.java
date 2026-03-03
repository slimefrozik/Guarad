package ru.guardsystem.service;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
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

    private ActiveVote activeVote;

    public VoteManager(JavaPlugin plugin, PersistenceLayer persistenceLayer, AuditLogger auditLogger, GuardManager guardManager) {
        this.plugin = plugin;
        this.persistenceLayer = persistenceLayer;
        this.auditLogger = auditLogger;
        this.guardManager = guardManager;
    }

    public void load() {
        persistenceLayer.loadVotesJson();
        auditLogger.log("VoteManager initialized");
    }

    public void save() {
        persistenceLayer.saveVotesJson("{\n  \"votes\": []\n}\n");
        auditLogger.log("VoteManager save called");
    }

    public synchronized Optional<String> startVote(VoteType type,
                                                   String target,
                                                   String reason,
                                                   CommandSender initiator,
                                                   Predicate<Player> eligibilityRule) {
        if (activeVote != null) {
            return Optional.of("[Vote] Уже идёт голосование: " + shortLabel(activeVote.type) + " " + activeVote.target);
        }

        String initiatorName = initiator.getName();
        ActiveVote vote = new ActiveVote(type, target, reason, initiatorName, Instant.now().plusSeconds(DEFAULT_DURATION_SECONDS), eligibilityRule);
        vote.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> expireVote(vote), DEFAULT_DURATION_SECONDS * 20L);
        activeVote = vote;

        auditLogger.log("Vote started: " + type + " target=" + target + " by=" + initiatorName + " reason=" + reason);
        return Optional.empty();
    }

    public synchronized VoteResult castVote(Player voter, VoteChoice choice) {
        if (activeVote == null) {
            return VoteResult.error("[Vote] Нет активного голосования.");
        }
        if (!activeVote.eligibilityRule.test(voter)) {
            return VoteResult.error("[Vote] У вас нет права голоса в этом голосовании.");
        }

        activeVote.ballots.put(voter.getUniqueId(), choice);
        VoteSnapshot snapshot = snapshot();
        return VoteResult.ok("[Vote] Принято: " + choice.name().toLowerCase(Locale.ROOT)
                + " | yes=" + snapshot.yes + " no=" + snapshot.no + " total=" + snapshot.total
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
        Bukkit.broadcastMessage("[Vote] Аннулирован: " + shortLabel(current.type) + " " + current.target + " (" + reason + ")");
        auditLogger.log("Vote cancelled: " + current.type + " target=" + current.target + " reason=" + reason);
    }

    private synchronized void expireVote(ActiveVote vote) {
        if (activeVote != vote) {
            return;
        }

        VoteSnapshot snapshot = snapshot();
        boolean accepted = snapshot != null && snapshot.yes > snapshot.no;

        if (accepted && snapshot != null) {
            applyOutcome(snapshot);
            Bukkit.broadcastMessage("[Vote] Принят: " + shortLabel(snapshot.type) + " " + snapshot.target
                    + " | yes=" + snapshot.yes + " no=" + snapshot.no);
            auditLogger.log("Vote accepted: " + snapshot.type + " target=" + snapshot.target);
        } else if (snapshot != null) {
            Bukkit.broadcastMessage("[Vote] Отклонён: " + shortLabel(snapshot.type) + " " + snapshot.target
                    + " | yes=" + snapshot.yes + " no=" + snapshot.no);
            auditLogger.log("Vote rejected: " + snapshot.type + " target=" + snapshot.target);
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
                    online.kickPlayer("VoteBan: " + snapshot.reason);
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

    private String shortLabel(VoteType type) {
        return switch (type) {
            case VOTEBAN -> "voteban";
            case GUARD_NOMINATE, OPEN_GUARD_NOMINATE -> "guard nominate";
            case GUARD_IMPEACH -> "guard impeach";
        };
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
