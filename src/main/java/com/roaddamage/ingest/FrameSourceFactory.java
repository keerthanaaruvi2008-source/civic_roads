package com.roaddamage.ingest;

import com.roaddamage.core.error.IngestionException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Factory for creating appropriate FrameSource implementations based on media file extensions.
 */
public final class FrameSourceFactory {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".bmp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".avi", ".mov", ".mkv");

    private FrameSourceFactory() {
        throw new UnsupportedOperationException("Factory class cannot be instantiated");
    }

    /**
     * Opens a FrameSource suitable for the specified media file path.
     *
     * @param path the path to the media file
     * @return an ImageFrameSource or VideoFrameGrabber instance
     * @throws IngestionException if the file extension is unsupported or opening fails
     */
    public static FrameSource open(Path path) throws IngestionException {
        Objects.requireNonNull(path, "path must not be null");
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IngestionException("ingest", "Invalid file path: " + path, null, Map.of("path", path.toString()));
        }
        String filenameStr = fileName.toString().toLowerCase(Locale.ROOT);
        int dot = filenameStr.lastIndexOf('.');
        if (dot < 0) {
            throw new IngestionException("ingest", "File has no extension: " + path, null, Map.of("path", path.toString()));
        }
        String ext = filenameStr.substring(dot);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return new ImageFrameSource(path);
        } else if (VIDEO_EXTENSIONS.contains(ext)) {
            return new VideoFrameGrabber(path);
        } else {
            throw new IngestionException("ingest", "Unsupported media format: " + ext, null,
                    Map.of("path", path.toString(), "extension", ext));
        }
    }
}
