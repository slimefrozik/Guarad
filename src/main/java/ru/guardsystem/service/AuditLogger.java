package ru.guardsystem.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class AuditLogger {

    private final Path logFile;

    public AuditLogger(Path logsDirectory) {
        try {
            Files.createDirectories(logsDirectory);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create logs directory", ex);
        }
        this.logFile = logsDirectory.resolve("audit.log");
    }

    public synchronized void log(String message) {
        String line = Instant.now() + " " + message + System.lineSeparator();
        try {
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write audit log", ex);
        }
    }
}
