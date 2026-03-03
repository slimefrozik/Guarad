package ru.guardsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AuditLogger {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "voteban_initiated",
            "vote_cast",
            "vote_result",
            "rollback_requested",
            "rollback_approved",
            "guard_transfer",
            "impeach_started",
            "impeach_result"
    );

    private final Path jsonLogFile;
    private final Path humanLogFile;
    private final ObjectMapper objectMapper;

    public AuditLogger(Path logsDirectory) {
        try {
            Files.createDirectories(logsDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create logs directory", ex);
        }
        this.jsonLogFile = logsDirectory.resolve("audit.jsonl");
        this.humanLogFile = logsDirectory.resolve("guard-actions.log");
        this.objectMapper = new ObjectMapper();
    }

    public synchronized void log(String message) {
        logEvent("vote_result", "system", Map.of("message", message));
    }

    public synchronized void logEvent(String eventType, String sessionId, Map<String, Object> payload) {
        if (!SUPPORTED_EVENTS.contains(eventType)) {
            throw new IllegalArgumentException("Unsupported audit event type: " + eventType);
        }

        Instant now = Instant.now();
        Map<String, Object> event = new HashMap<>();
        event.put("timestamp", now.toString());
        event.put("session_id", sessionId);
        event.put("event", eventType);
        event.put("payload", payload == null ? Map.of() : payload);

        String jsonLine = toJson(event) + System.lineSeparator();
        String humanLine = buildHumanLine(now, eventType, sessionId, payload) + System.lineSeparator();

        try {
            Files.writeString(jsonLogFile, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(humanLogFile, humanLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write audit log", ex);
        }
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize audit event", ex);
        }
    }

    private String buildHumanLine(Instant now, String eventType, String sessionId, Map<String, Object> payload) {
        return "[" + now + "]"
                + " [session=" + sessionId + "]"
                + " [event=" + eventType + "]"
                + " " + (payload == null ? "{}" : payload);
    }
}
