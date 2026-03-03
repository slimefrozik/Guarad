package ru.guardsystem.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    public static final int MAX_RADIUS = 50;
    public static final Duration MAX_ROLLBACK_DEPTH = Duration.ofDays(7);

    private final AuditLogger auditLogger;

    private RollbackSession rollbackSession;
    private int activeSessionCount;

    public SessionManager(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public void load() {
        this.activeSessionCount = 0;
        this.rollbackSession = null;
        auditLogger.log("SessionManager initialized with 0 active sessions");
    }

    public void save() {
        auditLogger.log("SessionManager save called");
    }

    public int getActiveSessionCount() {
        return activeSessionCount;
    }

    public boolean validateRollbackLimits(int radius, int seconds) {
        if (radius > MAX_RADIUS || radius < 1) {
            return false;
        }
        Duration depth = Duration.ofSeconds(seconds);
        return !depth.isNegative() && !depth.minus(MAX_ROLLBACK_DEPTH).isPositive();
    }

    public RollbackSession startRollbackSession(String initiator, String target, int radius, int timeSeconds) {
        this.rollbackSession = new RollbackSession(
            UUID.randomUUID(),
            initiator.toLowerCase(Locale.ROOT),
            target,
            radius,
            timeSeconds,
            Instant.now(),
            new HashMap<>(),
            false
        );
        this.activeSessionCount = 1;
        auditLogger.log("Created rollback vote session id=" + rollbackSession.id + " target=" + target + " radius=" + radius + " seconds=" + timeSeconds);
        return rollbackSession;
    }

    public VoteResult voteRollback(String voter, boolean yes, int activeGuardCount) {
        if (rollbackSession == null) {
            return VoteResult.noSession();
        }
        if (rollbackSession.approved) {
            return VoteResult.alreadyApproved(rollbackSession);
        }
        rollbackSession.votes.put(voter.toLowerCase(Locale.ROOT), yes);

        long yesVotes = rollbackSession.votes.values().stream().filter(Boolean::booleanValue).count();
        long noVotes = rollbackSession.votes.size() - yesVotes;

        int requiredVotes = (int) Math.ceil(activeGuardCount * (2.0 / 3.0));
        if (requiredVotes <= 0) {
            requiredVotes = Integer.MAX_VALUE;
        }
        if (yesVotes >= requiredVotes) {
            rollbackSession.approved = true;
            auditLogger.log("Rollback session approved id=" + rollbackSession.id + " yes=" + yesVotes + " no=" + noVotes + " required=" + requiredVotes + " activeGuards=" + activeGuardCount);
        } else {
            auditLogger.log("Rollback session vote id=" + rollbackSession.id + " yes=" + yesVotes + " no=" + noVotes + " required=" + requiredVotes + " activeGuards=" + activeGuardCount);
        }

        return VoteResult.of(rollbackSession, yesVotes, noVotes, requiredVotes, activeGuardCount);
    }

    public RollbackSession getRollbackSession() {
        return rollbackSession;
    }

    public void clearRollbackSession() {
        if (rollbackSession != null) {
            auditLogger.log("Rollback session cleared id=" + rollbackSession.id);
        }
        rollbackSession = null;
        activeSessionCount = 0;
    }

    public void logRollbackBypassAttempt(String actor, String reason) {
        auditLogger.log("SECURITY: blocked direct rollback attempt by=" + actor + " reason=" + reason);
    }

    public static final class RollbackSession {
        private final UUID id;
        private final String initiator;
        private final String target;
        private final int radius;
        private final int timeSeconds;
        private final Instant createdAt;
        private final Map<String, Boolean> votes;
        private boolean approved;

        private RollbackSession(UUID id, String initiator, String target, int radius, int timeSeconds, Instant createdAt, Map<String, Boolean> votes, boolean approved) {
            this.id = id;
            this.initiator = initiator;
            this.target = target;
            this.radius = radius;
            this.timeSeconds = timeSeconds;
            this.createdAt = createdAt;
            this.votes = votes;
            this.approved = approved;
        }

        public UUID getId() {
            return id;
        }

        public String getTarget() {
            return target;
        }

        public int getRadius() {
            return radius;
        }

        public int getTimeSeconds() {
            return timeSeconds;
        }

        public boolean isApproved() {
            return approved;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public int getVoteCount() {
            return votes.size();
        }

        public String getInitiator() {
            return initiator;
        }
    }

    public record VoteResult(boolean hasSession, boolean alreadyApproved, RollbackSession session, long yesVotes, long noVotes,
                             int requiredVotes, int activeGuards) {
        static VoteResult noSession() {
            return new VoteResult(false, false, null, 0, 0, 0, 0);
        }

        static VoteResult alreadyApproved(RollbackSession session) {
            return new VoteResult(true, true, session, 0, 0, 0, 0);
        }

        static VoteResult of(RollbackSession session, long yesVotes, long noVotes, int requiredVotes, int activeGuards) {
            return new VoteResult(true, false, session, yesVotes, noVotes, requiredVotes, activeGuards);
        }
    }
}
