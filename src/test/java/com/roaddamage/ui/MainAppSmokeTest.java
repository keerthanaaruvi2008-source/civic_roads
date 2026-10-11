package com.roaddamage.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MainAppSmokeTest {

    @BeforeAll
    static void initHeadlessJavaFx() {
        System.setProperty("testfx.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void applicationConstructsAndFxmlControlsInjected() throws Exception {
        // Confirm MainApp constructor succeeds
        MainApp app = new MainApp();
        assertNotNull(app);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                URL fxmlUrl = getClass().getResource("/fxml/main.fxml");
                assertNotNull(fxmlUrl, "Resource /fxml/main.fxml must exist on classpath");

                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                assertNotNull(root, "Root node loaded from main.fxml must not be null");

                MainController controller = loader.getController();
                assertNotNull(controller, "Controller must be instantiated and injected by FXMLLoader");

                // Audit expected @FXML injected fields
                List<String> expectedFxIds = List.of(
                        "originalView",
                        "annotatedCanvas",
                        "logTable",
                        "colFrame",
                        "colTimestamp",
                        "colClass",
                        "colConfidence",
                        "colScore",
                        "colLevel",
                        "colExplanation",
                        "alertList",
                        "statusLabel",
                        "openButton",
                        "startButton",
                        "stopButton",
                        "exportButton",
                        "progress"
                );

                for (String fxId : expectedFxIds) {
                    Field field = MainController.class.getDeclaredField(fxId);
                    field.setAccessible(true);
                    Object value = field.get(controller);
                    assertNotNull(value, "@FXML field '" + fxId + "' must be injected and non-null");
                }

                controller.shutdown();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread FXML loading");
        if (failure.get() != null) {
            throw new AssertionError("MainApp smoke test failed on JavaFX thread", failure.get());
        }
    }
}
