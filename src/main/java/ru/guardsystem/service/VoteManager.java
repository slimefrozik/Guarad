package ru.guardsystem.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class VoteManager {

    private static final Duration BAN_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration BAN_COOLDOWN = Duration.ofHours(48);
    private static final Duration NOMINATION_CONFIRMATION_WINDOW = Duration.ofHours(24);

    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private final GuardManager guardManager;

    private final Map<UUID, VoteSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, Instant> banCooldownUntil = new LinkedHashMap<>();
    private String votesJson;

    public VoteManager(PersistenceLayer persistenceLayer, AuditLogger auditLogger, GuardManager guardManager) {
        this.persistenceLayer = persistenceLayer;
        this.auditLogger = auditLogger;
        this.guardManager = guardManager;
    }

    public void load() {
        this.votesJson = persistenceLayer.loadVotesJson();
        auditLogger.log("VoteManager loaded votes.json");
    }

    public void save() {
        this.votesJson = buildDebugStateJson();
        persistenceLayer.saveVotesJson(votesJson);
        auditLogger.log("VoteManager saved votes.json");
    }

    public synchronized VoteCreateResult createSession(VoteType voteType, UUID initiator, UUID target) {
        Objects.requireNonNull(voteType, "voteType");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(target, "target");

        guardManager.enforceInactivityRule();
        if (!guardManager.hasGuardPermission(initiator)) {
            return VoteCreateResult.rejected("Initiator does not have active Guard rights.");
        }

        if (voteType == VoteType.BAN) {
            Instant cooldownUntil = banCooldownUntil.get(target);
            if (cooldownUntil != null && cooldownUntil.isAfter(Instant.now())) {
                return VoteCreateResult.rejected("Ban vote cooldown for target is still active.");
            }
        }

        if (voteType == VoteType.NOMINATION) {
            if (initiator.equals(target)) {
                return VoteCreateResult.rejected("Self-nomination is not allowed.");
            }
            if (guardManager.getAllGuardIds().contains(target)) {
                return VoteCreateResult.rejected("Cannot nominate a current Guard.");
            }
        }

        VoteSession session = new VoteSession(UUID.randomUUID(), voteType, initiator, target, Instant.now(), SessionStatus.OPEN);
        sessions.put(session.id(), session);

        if (voteType == VoteType.BAN) {
            banCooldownUntil.put(target, Instant.now().plus(BAN_COOLDOWN));
        }

        return VoteCreateResult.created(session);
    }

    public synchronized VoteDecisionResult castVote(UUID sessionId, UUID voter, VoteDecision decision) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(decision, "decision");

        guardManager.enforceInactivityRule();
        VoteSession session = sessions.get(sessionId);
        if (session == null) {
            return VoteDecisionResult.rejected("Vote session not found.");
        }
        if (session.status() != SessionStatus.OPEN && session.status() != SessionStatus.AWAITING_CONFIRMATION) {
            return VoteDecisionResult.rejected("Vote session is not active.");
        }
        if (!guardManager.hasGuardPermission(voter)) {
            return VoteDecisionResult.rejected("Only active Guards can vote.");
        }
        if (session.status() == SessionStatus.AWAITING_CONFIRMATION) {
            return VoteDecisionResult.rejected("Nomination is awaiting confirmation, voting closed.");
        }

        enforceTimeoutRules(session);
        if (session.status() != SessionStatus.OPEN) {
            return VoteDecisionResult.rejected("Vote session is not open.");
        }

        session.votes().put(voter, decision);
        resolveSession(session);

        return VoteDecisionResult.accepted(session.status());
    }

    public synchronized boolean confirmNomination(UUID sessionId, UUID nominee) {
        VoteSession session = sessions.get(sessionId);
        if (session == null || session.type() != VoteType.NOMINATION || session.status() != SessionStatus.AWAITING_CONFIRMATION) {
            return false;
        }
        if (!session.target().equals(nominee)) {
            return false;
        }
        if (session.confirmationDeadline() != null && Instant.now().isAfter(session.confirmationDeadline())) {
            session.setStatus(SessionStatus.EXPIRED);
            return false;
        }

        boolean added = guardManager.registerGuard(nominee);
        session.setStatus(added ? SessionStatus.APPROVED : SessionStatus.REJECTED);
        return added;
    }

    public synchronized Optional<VoteSession> findSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public synchronized List<VoteSession> getSessions() {
        return new ArrayList<>(sessions.values());
    }

    public synchronized void sweepExpiredSessions() {
        for (VoteSession session : sessions.values()) {
            enforceTimeoutRules(session);
        }
    }

    public void touch() {
        auditLogger.log("VoteManager touch called");
    }

    private void enforceTimeoutRules(VoteSession session) {
        Instant now = Instant.now();
        if (session.type() == VoteType.BAN && session.status() == SessionStatus.OPEN) {
            if (session.createdAt().plus(BAN_TIMEOUT).isBefore(now)) {
                session.setStatus(SessionStatus.ANNULLED);
            }
            return;
        }

        if (session.type() == VoteType.NOMINATION && session.status() == SessionStatus.AWAITING_CONFIRMATION) {
            if (session.confirmationDeadline() != null && session.confirmationDeadline().isBefore(now)) {
                session.setStatus(SessionStatus.EXPIRED);
            }
        }
    }

    private void resolveSession(VoteSession session) {
        int activeGuards = Math.max(1, guardManager.getActiveGuardCount());
        long yesVotes = session.votes().values().stream().filter(vote -> vote == VoteDecision.YES).count();
        long noVotes = session.votes().values().stream().filter(vote -> vote == VoteDecision.NO).count();

        switch (session.type()) {
            case BAN -> {
                if (noVotes > 0) {
                    session.setStatus(SessionStatus.REJECTED);
                    return;
                }
                if (yesVotes >= activeGuards) {
                    session.setStatus(SessionStatus.APPROVED);
                }
            }
            case NOMINATION -> {
                if (noVotes > 0) {
                    session.setStatus(SessionStatus.REJECTED);
                    return;
                }
                if (yesVotes >= activeGuards) {
                    session.setStatus(SessionStatus.AWAITING_CONFIRMATION);
                    session.setConfirmationDeadline(Instant.now().plus(NOMINATION_CONFIRMATION_WINDOW));
                    guardManager.removeGuard(session.initiator());
                }
            }
            case IMPEACHMENT -> {
                int threshold = guardManager.getRequiredImpeachmentVotes();
                if (yesVotes >= threshold) {
                    guardManager.removeGuard(session.target());
                    session.setStatus(SessionStatus.APPROVED);
                } else if (noVotes > activeGuards - threshold) {
                    session.setStatus(SessionStatus.REJECTED);
                }
            }
            case ROLLBACK -> {
                if (noVotes > 0) {
                    session.setStatus(SessionStatus.REJECTED);
                    return;
                }
                if (yesVotes >= activeGuards) {
                    session.setStatus(SessionStatus.APPROVED);
                }
            }
            default -> throw new IllegalStateException("Unhandled vote type: " + session.type());
        }
    }

    private String buildDebugStateJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"activeSessions\": ").append(sessions.size()).append(",\n");
        builder.append("  \"sessions\": [\n");
        int index = 0;
        for (VoteSession session : sessions.values()) {
            builder.append("    {\"id\":\"").append(session.id())
                    .append("\",\"type\":\"").append(session.type())
                    .append("\",\"status\":\"").append(session.status())
                    .append("\"}");
            if (++index < sessions.size()) {
                builder.append(',');
            }
            builder.append("\n");
        }
        builder.append("  ]\n}\n");
        return builder.toString();
    }

    public enum VoteType {
        BAN,
        NOMINATION,
        IMPEACHMENT,
        ROLLBACK
    }

    public enum VoteDecision {
        YES,
        NO
    }

    public enum SessionStatus {
        OPEN,
        AWAITING_CONFIRMATION,
        APPROVED,
        REJECTED,
        ANNULLED,
        EXPIRED
    }

    public static final class VoteSession {
        private final UUID id;
        private final VoteType type;
        private final UUID initiator;
        private final UUID target;
        private final Instant createdAt;
        private final Map<UUID, VoteDecision> votes;
        private SessionStatus status;
        private Instant confirmationDeadline;

        public VoteSession(UUID id, VoteType type, UUID initiator, UUID target, Instant createdAt, SessionStatus status) {
            this.id = id;
            this.type = type;
            this.initiator = initiator;
            this.target = target;
            this.createdAt = createdAt;
            this.votes = new LinkedHashMap<>();
            this.status = status;
        }

        public UUID id() { return id; }
        public VoteType type() { return type; }
        public UUID initiator() { return initiator; }
        public UUID target() { return target; }
        public Instant createdAt() { return createdAt; }
        public Map<UUID, VoteDecision> votes() { return votes; }
        public SessionStatus status() { return status; }
        public Instant confirmationDeadline() { return confirmationDeadline; }
        public void setStatus(SessionStatus status) { this.status = status; }
        public void setConfirmationDeadline(Instant confirmationDeadline) { this.confirmationDeadline = confirmationDeadline; }
    }

    public record VoteCreateResult(boolean created, String reason, VoteSession session) {
        static VoteCreateResult created(VoteSession session) {
            return new VoteCreateResult(true, null, session);
        }

        static VoteCreateResult rejected(String reason) {
            return new VoteCreateResult(false, reason, null);
        }
    }

    public record VoteDecisionResult(boolean accepted, String reason, SessionStatus currentStatus) {
        static VoteDecisionResult accepted(SessionStatus status) {
            return new VoteDecisionResult(true, null, status);
        }

        static VoteDecisionResult rejected(String reason) {
            return new VoteDecisionResult(false, reason, null);
        }
    }
}
