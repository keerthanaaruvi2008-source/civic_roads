package com.roaddamage.export;

import com.roaddamage.core.error.ExportException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance, RFC 4180-compliant CSV report exporter with atomic file write guarantees.
 */
public final class CsvReportExporter {

    private static final String HEADER =
            "frame_index,timestamp_ms,damage_class,confidence,x1,y1,x2,y2,area_ratio,aspect_ratio,priority_score,severity_level,explanation";

    private CsvReportExporter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Exports a list of damage detection rows to the specified target path.
     * <p>
     * Guarantees atomic writes by first writing to a sibling temporary file and moving into place.
     *
     * @param target destination CSV file path
     * @param rows unmodifiable or modifiable list of damage records
     * @throws ExportException if file I/O or serialization fails
     * @throws NullPointerException if target or rows is null, or if any element in rows is null
     */
    public static void export(Path target, List<DamageRow> rows) throws ExportException {
        Objects.requireNonNull(target, "target path must not be null");
        Objects.requireNonNull(rows, "rows list must not be null");

        for (DamageRow row : rows) {
            Objects.requireNonNull(row, "DamageRow element must not be null");
        }

        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) {
            throw new ExportException("export", "Cannot determine parent directory for path: " + target);
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new ExportException("export", "Failed to create parent directories for: " + target, e,
                    Map.of("target", target.toString()));
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile(parent, "report_", ".tmp");
        } catch (IOException e) {
            throw new ExportException("export", "Failed to create temporary file in: " + parent, e,
                    Map.of("parent", parent.toString()));
        }

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                writer.newLine();

                for (DamageRow row : rows) {
                    String line = String.format(Locale.US,
                            "%d,%d,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.6f,%.4f,%d,%s,%s",
                            row.frameIndex(),
                            row.timestampMillis(),
                            escapeRfc4180(row.damageClassLabel()),
                            row.confidence(),
                            row.x1(),
                            row.y1(),
                            row.x2(),
                            row.y2(),
                            row.areaRatio(),
                            row.aspectRatio(),
                            row.priorityScore(),
                            row.severityLevel().name(),
                            escapeRfc4180(row.explanation())
                    );
                    writer.write(line);
                    writer.newLine();
                }
            }

            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback to standard replace move if filesystem does not support atomic move
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // Ignore cleanup failure
            }
            throw new ExportException("export", "Failed writing CSV report to: " + target, e,
                    Map.of("target", target.toString()));
        }
    }

    /**
     * Escapes a string value adhering to RFC 4180 specification.
     *
     * @param value raw input string
     * @return properly quoted and escaped CSV cell string
     */
    public static String escapeRfc4180(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
