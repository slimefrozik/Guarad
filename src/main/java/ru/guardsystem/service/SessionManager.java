package ru.guardsystem.service;

public class SessionManager {

    private final AuditLogger auditLogger;
    private int activeSessionCount;
    private long sessionSequence;

    public SessionManager(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public void load() {
        this.activeSessionCount = 0;
        this.sessionSequence = 0;
    }

    public void save() {
        auditLogger.log("SessionManager save called");
    }

    public int getActiveSessionCount() {
        return activeSessionCount;
    }

    public synchronized String startSession(String action, String actor) {
        sessionSequence++;
        activeSessionCount++;
        return action + "-" + actor + "-" + sessionSequence;
    }

    public synchronized void finishSession(String sessionId) {
        if (activeSessionCount > 0) {
            activeSessionCount--;
        }
        auditLogger.logEvent("vote_result", sessionId, java.util.Map.of("status", "session_closed"));
    }
}
