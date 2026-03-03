package ru.guardsystem.service;

public class SessionManager {

    private final AuditLogger auditLogger;
    private int activeSessionCount;

    public SessionManager(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public void load() {
        this.activeSessionCount = 0;
        auditLogger.log("SessionManager initialized with 0 active sessions");
    }

    public void save() {
        auditLogger.log("SessionManager save called");
    }

    public int getActiveSessionCount() {
        return activeSessionCount;
    }
}
