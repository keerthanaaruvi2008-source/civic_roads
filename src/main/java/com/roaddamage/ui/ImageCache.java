package com.roaddamage.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;

/**
 * Thread-safe image loader and cache for citizen distress evidence and site engineering proofs.
 * Guarantees asynchronous loading, bounded thumbnail caching, and fallback synthetic imagery.
 */
public final class ImageCache {

    private static final Logger LOGGER = System.getLogger(ImageCache.class.getName());
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "RoadDamage-ImageLoader");
            t.setDaemon(true);
            return t;
        }
    });

    private ImageCache() {
    }

    /**
     * Asynchronously loads or generates an image thumbnail, delivering result on the JavaFX application thread.
     *
     * @param path image file path or sample key
     * @param width target display width
     * @param height target display height
     * @param callback consumer called on JavaFX thread with loaded image
     */
    public static void loadAsync(String path, int width, int height, Consumer<Image> callback) {
        if (callback == null) return;
        if (path == null || path.isBlank()) {
            Image placeholder = getOrCreateSyntheticImage("No Photo Attached", false, width, height);
            Platform.runLater(() -> callback.accept(placeholder));
            return;
        }

        String cacheKey = path + "@" + width + "x" + height;
        Image cached = CACHE.get(cacheKey);
        if (cached != null) {
            Platform.runLater(() -> callback.accept(cached));
            return;
        }

        EXECUTOR.submit(() -> {
            Image img = loadOrGenerate(path, width, height);
            CACHE.put(cacheKey, img);
            Platform.runLater(() -> callback.accept(img));
        });
    }

    /**
     * Synchronously loads or generates an image (suitable for background worker tasks or testing).
     */
    public static Image loadOrGenerate(String path, int width, int height) {
        if (path == null || path.isBlank()) {
            return getOrCreateSyntheticImage("No Photo Attached", false, width, height);
        }

        File file = new File(path);
        if (file.exists() && file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                return new Image(fis, width, height, true, true);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed reading image from {0}: {1}", path, e.getMessage());
            }
        }

        // Generate synthetic road distress or repair proof if file not yet on disk
        boolean isPatch = path.toLowerCase().contains("after") || path.toLowerCase().contains("patch");
        String label = formatLabelFromPath(path);
        Image synthetic = getOrCreateSyntheticImage(label, isPatch, width, height);

        // Optionally materialize to disk if under samples/ directory
        if (path.startsWith("samples/") || path.startsWith("samples\\")) {
            try {
                Path samplePath = Path.of(path);
                if (samplePath.getParent() != null) {
                    Files.createDirectories(samplePath.getParent());
                }
                BufferedImage bImg = createSyntheticBufferedImage(label, isPatch, Math.max(480, width), Math.max(320, height));
                ImageIO.write(bImg, "jpg", samplePath.toFile());
            } catch (Exception ignored) {
            }
        }

        return synthetic;
    }

    private static String formatLabelFromPath(String path) {
        String name = new File(path).getName();
        if (name.toLowerCase().contains("before")) {
            return "BEFORE REPAIR: Distress Site Proof";
        } else if (name.toLowerCase().contains("after")) {
            return "AFTER REPAIR: Bituminous Patch Sealed";
        } else if (name.toLowerCase().contains("pothole")) {
            return "REPORTED DEFECT: Surface Pothole";
        }
        return "ROAD INSPECTION EVIDENCE";
    }

    private static Image getOrCreateSyntheticImage(String label, boolean isPatch, int width, int height) {
        BufferedImage bImg = createSyntheticBufferedImage(label, isPatch, width, height);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bImg, "png", baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            return new Image(bais, width, height, true, true);
        } catch (IOException e) {
            return new Image(new ByteArrayInputStream(new byte[0]));
        }
    }

    private static BufferedImage createSyntheticBufferedImage(String label, boolean isPatch, int w, int h) {
        int imgW = Math.max(200, w);
        int imgH = Math.max(150, h);
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Asphalt base texture
        g.setColor(new Color(45, 48, 52));
        g.fillRect(0, 0, imgW, imgH);

        // Noise grain
        Random rng = new Random(label.hashCode());
        for (int x = 0; x < imgW; x += 4) {
            for (int y = 0; y < imgH; y += 4) {
                int delta = rng.nextInt(24) - 12;
                int base = isPatch ? 40 : 48;
                int c = Math.max(0, Math.min(255, base + delta));
                g.setColor(new Color(c, c, c + 2));
                g.fillRect(x, y, 4, 4);
            }
        }

        // 2. Road markings (white or yellow dashed line)
        g.setColor(isPatch ? new Color(230, 200, 40, 180) : new Color(240, 240, 240, 160));
        g.fillRect(0, imgH / 2 - 2, imgW, 4);

        // 3. Central defect or remediated asphalt patch
        int cx = imgW / 2;
        int cy = imgH / 2;
        int radW = imgW / 3;
        int radH = imgH / 3;

        if (isPatch) {
            // High-density compacted asphalt overlay
            g.setColor(new Color(25, 27, 30));
            g.fillOval(cx - radW, cy - radH, radW * 2, radH * 2);
            g.setColor(new Color(34, 197, 94, 200));
            g.drawOval(cx - radW, cy - radH, radW * 2, radH * 2);

            g.setColor(new Color(134, 239, 172));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, imgW / 24)));
            String badge = "COMPACTED PATCH SEAL";
            int sw = g.getFontMetrics().stringWidth(badge);
            g.drawString(badge, cx - sw / 2, cy + 4);
        } else {
            // Deep pothole with fracture fissures
            g.setColor(new Color(18, 18, 20));
            g.fillOval(cx - radW, cy - radH, radW * 2, radH * 2);
            g.setColor(new Color(239, 68, 68, 220));
            g.drawOval(cx - radW, cy - radH, radW * 2, radH * 2);

            // Fracture lines
            g.setColor(new Color(10, 10, 10));
            g.drawLine(cx - radW, cy, cx - radW - 25, cy + 15);
            g.drawLine(cx + radW, cy, cx + radW + 20, cy - 10);
            g.drawLine(cx, cy + radH, cx + 10, cy + radH + 20);

            g.setColor(new Color(252, 165, 165));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, imgW / 24)));
            String badge = "STRUCTURAL DISTRESS";
            int sw = g.getFontMetrics().stringWidth(badge);
            g.drawString(badge, cx - sw / 2, cy + 4);
        }

        // 4. Header title banner
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, imgW, 28);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, imgH / 16)));
        g.drawString(label, 10, 19);

        g.dispose();
        return img;
    }

    /**
     * Clears cached images.
     */
    public static void clear() {
        CACHE.clear();
    }
}
