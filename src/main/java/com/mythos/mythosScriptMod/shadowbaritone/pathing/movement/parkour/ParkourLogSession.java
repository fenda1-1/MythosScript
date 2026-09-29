package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Dedicated UTF-8 JSONL sink; never passes telemetry to the application logger. */
public final class ParkourLogSession implements Closeable {
    private final Path path;
    private final BufferedWriter writer;
    private boolean closed;

    public ParkourLogSession(Path directory) throws IOException {
        Files.createDirectories(directory);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
        path = Files.createTempFile(directory, "parkour-" + stamp + "-", ".jsonl");
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
    }

    public Path getPath() { return path; }

    public synchronized void write(JsonObject record) throws IOException {
        if (closed) return;
        writer.write(record.toString());
        writer.newLine();
    }

    public synchronized void flush() throws IOException {
        if (!closed) writer.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            writer.close();
        }
    }
}
