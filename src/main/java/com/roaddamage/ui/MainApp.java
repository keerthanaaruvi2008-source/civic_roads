package com.roaddamage.ui;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Primary JavaFX Application entrypoint for the Road Damage Detection & Inspection framework.
 */
public class MainApp extends Application {

    private static final Logger LOGGER = System.getLogger(MainApp.class.getName());

    private MainController controller;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Global uncaught exception handling
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> handleUncaughtException(throwable));

        URL fxmlUrl = getClass().getResource("/fxml/main.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Cannot find /fxml/main.fxml on classpath");
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();
        this.controller = loader.getController();

        Scene scene = new Scene(root, 1280, 800);
        URL cssUrl = getClass().getResource("/css/app.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        primaryStage.setTitle("Road Damage Inspector");
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(720);
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.requestFocus();
    }

    @Override
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            LOGGER.log(Level.INFO, "Shutting down MainApp and active controller pipelines");
            if (controller != null) {
                controller.shutdown();
            }
        }
    }

    private void handleUncaughtException(Throwable throwable) {
        LOGGER.log(Level.ERROR, "Uncaught application exception: {0}", throwable.getMessage());
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Unexpected Application Error");
                alert.setHeaderText("A non-fatal error occurred");
                alert.setContentText(throwable.getMessage() != null ? throwable.getMessage() : throwable.toString());
                alert.show();
            } catch (Exception ignored) {
                // Ignore alert display failures during shutdown
            }
        });
    }

    /**
     * Application entry point for direct launching.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
