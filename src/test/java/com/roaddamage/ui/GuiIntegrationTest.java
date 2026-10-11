package com.roaddamage.ui;

import com.roaddamage.export.CsvReportExporter;
import com.roaddamage.export.DamageRow;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import nu.pattern.OpenCV;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless JavaFX integration test validating GUI end-to-end user workflows:
 * Opening short_road.mp4, starting inspection, status updates, damage log populating,
 * and CSV report generation matching table row counts.
 */
final class GuiIntegrationTest {

    private static final Path VIDEO_PATH = Path.of("samples", "short_road.mp4");

    @BeforeAll
    static void initEnvironment() {
        OpenCV.loadLocally();
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
    }

    @Test
    @DisplayName("GUI workflow: open short_road.mp4, run inspection, verify damage log, export CSV")
    void fullGuiWorkflowEndToEnd() throws Exception {
        assertTrue(Files.isRegularFile(VIDEO_PATH), "samples/short_road.mp4 must exist");

        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<MainController> controllerRef = new AtomicReference<>();
        AtomicReference<Throwable> errRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                URL fxmlUrl = getClass().getResource("/fxml/main.fxml");
                assertNotNull(fxmlUrl, "main.fxml must exist");
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                assertNotNull(root);
                MainController controller = loader.getController();
                assertNotNull(controller);
                controllerRef.set(controller);
            } catch (Throwable t) {
                errRef.set(t);
            } finally {
                initLatch.countDown();
            }
        });

        assertTrue(initLatch.await(5, TimeUnit.SECONDS), "Timed out initializing GUI FXML");
        if (errRef.get() != null) {
            throw new AssertionError(errRef.get());
        }

        MainController controller = controllerRef.get();

        // 1. Open video
        CountDownLatch openLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            controller.setSelectedPath(VIDEO_PATH);
            assertEquals("Loaded: short_road.mp4", controller.getStatusLabel().getText());
            openLatch.countDown();
        });
        assertTrue(openLatch.await(5, TimeUnit.SECONDS));

        // 2. Start inspection
        CountDownLatch startLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            controller.onStart();
            startLatch.countDown();
        });
        assertTrue(startLatch.await(5, TimeUnit.SECONDS));

        // 3. Wait for inspection completion (poll statusLabel for "Completed")
        long deadline = System.currentTimeMillis() + 15_000L;
        boolean completed = false;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            CountDownLatch checkLatch = new CountDownLatch(1);
            AtomicReference<String> textRef = new AtomicReference<>();
            Platform.runLater(() -> {
                textRef.set(controller.getStatusLabel().getText());
                checkLatch.countDown();
            });
            checkLatch.await(2, TimeUnit.SECONDS);

            String status = textRef.get();
            if (status != null && status.startsWith("Completed")) {
                completed = true;
                break;
            }
        }
        assertTrue(completed, "Inspection should have completed within deadline");

        // 4. Verify damage log filled
        CountDownLatch logLatch = new CountDownLatch(1);
        AtomicReference<List<DamageRow>> rowsRef = new AtomicReference<>();
        Platform.runLater(() -> {
            rowsRef.set(List.copyOf(controller.getLogTable().getItems()));
            logLatch.countDown();
        });
        assertTrue(logLatch.await(5, TimeUnit.SECONDS));

        List<DamageRow> rows = rowsRef.get();
        assertNotNull(rows);
        assertFalse(rows.isEmpty(), "Damage Log must contain detected damage rows");

        // 5. Export CSV and verify row count matches detections
        Path tempCsv = Files.createTempFile("road_damage_gui_test_", ".csv");
        try {
            CsvReportExporter.export(tempCsv, rows);
            assertTrue(Files.isRegularFile(tempCsv));

            List<String> lines = Files.readAllLines(tempCsv);
            assertFalse(lines.isEmpty(), "CSV must not be empty");
            assertEquals("frame_index,timestamp_ms,damage_class,confidence,x1,y1,x2,y2,area_ratio,aspect_ratio,priority_score,severity_level,explanation",
                    lines.get(0), "CSV header must match standard schema");

            int dataRowCount = lines.size() - 1;
            assertEquals(rows.size(), dataRowCount, "CSV row count must match damage log detections");

            System.out.printf("[GUI Integration] Successfully verified: %d damage detections exported to CSV matching GUI table.%n",
                    dataRowCount);
        } finally {
            Files.deleteIfExists(tempCsv);
            Platform.runLater(controller::shutdown);
        }
    }
}
