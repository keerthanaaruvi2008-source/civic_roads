package com.roaddamage.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MainAppTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @Test
    void loadsFxmlAndInitializesController() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                URL fxmlUrl = getClass().getResource("/fxml/main.fxml");
                assertNotNull(fxmlUrl, "Cannot find /fxml/main.fxml");

                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                assertNotNull(root);

                MainController controller = loader.getController();
                assertNotNull(controller, "MainController must be injected by FXMLLoader");

                Scene scene = new Scene(root, 1200, 720);
                URL cssUrl = getClass().getResource("/css/app.css");
                assertNotNull(cssUrl, "Cannot find /css/app.css");
                scene.getStylesheets().add(cssUrl.toExternalForm());

                controller.shutdown();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out initializing FXML");
        if (failure.get() != null) {
            throw new AssertionError("Failed loading FXML or initializing controller", failure.get());
        }
    }
}
