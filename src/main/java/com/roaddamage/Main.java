package com.roaddamage;

import ai.onnxruntime.OrtEnvironment;
import com.roaddamage.core.error.RoadDamageException;
import com.roaddamage.ui.MainApp;
import com.roaddamage.web.CivicWebServer;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javafx.application.Application;
import nu.pattern.OpenCV;
import org.opencv.core.Core;

/**
 * Main application entry point demonstrating native library initialization.
 */
public final class Main {

    private static final Logger LOGGER = System.getLogger(Main.class.getName());

    private Main() {
        // Prevent direct instantiation
    }

    /**
     * Application main method.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        try {
            OpenCV.loadLocally();
            System.out.println("OpenCV OK: " + Core.VERSION);
            System.out.println("ORT OK: " + OrtEnvironment.getAvailableProviders());
            CivicWebServer webServer = new CivicWebServer();
            webServer.start();

            boolean cliMode = false;
            for (String arg : args) {
                if ("--cli".equalsIgnoreCase(arg) || "--headless".equalsIgnoreCase(arg) || "--web".equalsIgnoreCase(arg)) {
                    cliMode = true;
                    break;
                }
            }

            if (!cliMode) {
                System.out.println("Launching JavaFX Desktop GUI (MainApp)...");
                Application.launch(MainApp.class, args);
            } else {
                System.out.println("Running in headless web mode. Press Ctrl+C or kill process to terminate.");
                Thread.currentThread().join();
            }
        } catch (RoadDamageException e) {
            LOGGER.log(Level.ERROR, "Road damage framework error occurred in component: " + e.component(), e);
        } catch (Throwable t) {
            LOGGER.log(Level.ERROR, "Unexpected error launching application: " + t.getMessage(), t);
            t.printStackTrace();
        }
    }
}
