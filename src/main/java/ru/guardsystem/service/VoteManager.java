package ru.guardsystem.service;

public class VoteManager {

    private final PersistenceLayer persistenceLayer;
    private final AuditLogger auditLogger;
    private String votesJson;

    public VoteManager(PersistenceLayer persistenceLayer, AuditLogger auditLogger) {
        this.persistenceLayer = persistenceLayer;
        this.auditLogger = auditLogger;
    }

    public void load() {
        this.votesJson = persistenceLayer.loadVotesJson();
        auditLogger.logEvent("vote_result", "system-load", java.util.Map.of("message", "VoteManager loaded votes.json"));
    }

    public void save() {
        if (votesJson == null) {
            return;
        }
        persistenceLayer.saveVotesJson(votesJson);
        auditLogger.logEvent("vote_result", "system-save", java.util.Map.of("message", "VoteManager saved votes.json"));
    }

    public void startVoteBan(String sessionId, String initiator, String target) {
        auditLogger.logEvent("voteban_initiated", sessionId, java.util.Map.of(
                "initiator", initiator,
                "target", target
        ));
    }

    public void castVote(String sessionId, String voter, String choice) {
        auditLogger.logEvent("vote_cast", sessionId, java.util.Map.of(
                "voter", voter,
                "choice", choice
        ));
    }

    public void registerVoteResult(String sessionId, String result) {
        auditLogger.logEvent("vote_result", sessionId, java.util.Map.of("result", result));
    }
}
