package com.roaddamage.export;

import com.roaddamage.core.model.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CsvReportExporterTest {

    @Test
    void rejectsNullParameters() {
        assertThrows(NullPointerException.class, () -> CsvReportExporter.export(null, List.of()));
        assertThrows(NullPointerException.class, () -> CsvReportExporter.export(Path.of("test.csv"), null));

        DamageRow nullRow = null;
        List<DamageRow> rowsWithNull = Collections.singletonList(nullRow);
        assertThrows(NullPointerException.class, () -> CsvReportExporter.export(Path.of("test.csv"), rowsWithNull));
    }

    @Test
    void exportsEmptyListWithHeaderOnly(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("empty.csv");
        CsvReportExporter.export(target, List.of());

        assertTrue(Files.isRegularFile(target));
        List<String> lines = Files.readAllLines(target);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("frame_index,timestamp_ms,damage_class"));
    }

    @Test
    void escapesRfc4180SpecialCharacters() {
        assertEquals("simple", CsvReportExporter.escapeRfc4180("simple"));
        assertEquals("\"with,comma\"", CsvReportExporter.escapeRfc4180("with,comma"));
        assertEquals("\"with\"\"quote\"", CsvReportExporter.escapeRfc4180("with\"quote"));
        assertEquals("\"with\nnewline\"", CsvReportExporter.escapeRfc4180("with\nnewline"));
        assertEquals("\"with\r\ncrlf\"", CsvReportExporter.escapeRfc4180("with\r\ncrlf"));
    }

    @Test
    void exportsRowsAndCreatesParentDirectories(@TempDir Path tempDir) throws Exception {
        Path subDir = tempDir.resolve("nested").resolve("reports");
        Path target = subDir.resolve("damage_report.csv");

        DamageRow row1 = new DamageRow(
                12L,
                400L,
                "Pothole",
                0.954f,
                50.0f, 60.0f, 150.0f, 180.0f,
                0.035f,
                1.2f,
                85,
                SeverityLevel.CRITICAL,
                "Priority score 85, high hazard."
        );

        DamageRow row2 = new DamageRow(
                15L,
                500L,
                "Longitudinal crack",
                0.825f,
                10.0f, 20.0f, 30.0f, 400.0f,
                0.012f,
                13.3f,
                65,
                SeverityLevel.HIGH,
                "Elongated crack with \"special quote\", and comma."
        );

        CsvReportExporter.export(target, List.of(row1, row2));

        assertTrue(Files.isRegularFile(target));
        List<String> lines = Files.readAllLines(target);
        assertEquals(3, lines.size());

        // Header check
        assertTrue(lines.get(0).startsWith("frame_index,"));

        // Row 1 check
        assertTrue(lines.get(1).startsWith("12,400,Pothole,0.9540,50.00,60.00,150.00,180.00"));
        assertTrue(lines.get(1).contains(",85,CRITICAL,"));

        // Row 2 RFC 4180 escaped check
        assertTrue(lines.get(2).startsWith("15,500,Longitudinal crack,"));
        assertTrue(lines.get(2).contains("\"Elongated crack with \"\"special quote\"\", and comma.\""));
    }

    @Test
    void csvRoundTripVerification(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("roundtrip.csv");

        DamageRow original = new DamageRow(
                101L,
                2500L,
                "Alligator crack",
                0.9123f,
                12.5f, 25.0f, 150.75f, 200.25f,
                0.042f,
                1.75f,
                82,
                SeverityLevel.CRITICAL,
                "Priority 82: Extensive fatigue failure with, comma and \"embedded quotes\"."
        );

        CsvReportExporter.export(target, List.of(original));
        assertTrue(Files.isRegularFile(target));

        String content = Files.readString(target);
        String[] lines = content.split("\r?\n");
        assertEquals(2, lines.length);

        // Verify exact values in output line
        String rowLine = lines[1];
        assertTrue(rowLine.startsWith("101,2500,Alligator crack,0.9123,12.50,25.00,150.75,200.25,0.042000,1.7500,82,CRITICAL,"));
        assertTrue(rowLine.endsWith("\"Priority 82: Extensive fatigue failure with, comma and \"\"embedded quotes\"\".\""));
    }
}
