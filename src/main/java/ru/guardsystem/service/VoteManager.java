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
        auditLogger.log("VoteManager loaded votes.json");
    }

    public void save() {
        if (votesJson == null) {
            return;
        }
        persistenceLayer.saveVotesJson(votesJson);
        auditLogger.log("VoteManager saved votes.json");
    }

    public void touch() {
        auditLogger.log("VoteManager touch called");
    }
}
