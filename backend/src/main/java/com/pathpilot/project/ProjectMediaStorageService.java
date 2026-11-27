package com.pathpilot.project;

import com.pathpilot.common.ApiException;
import com.pathpilot.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploaded project screenshots outside the web root with strict server-side
 * magic-byte inspection, 2 MB size cap, and random UUID filenames.
 */
@Service
public class ProjectMediaStorageService {

    private static final Pattern SAFE_STORED_NAME = Pattern.compile("^[a-f0-9-]+\\.(png|jpg|webp)$");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp"
    );

    private final Path mediaRootDir;
    private final long maxFileSizeBytes;

    public ProjectMediaStorageService(AppProperties appProperties) {
        String configuredDir = appProperties.uploads() != null
                ? appProperties.uploads().dir()
                : "./uploads";
        this.maxFileSizeBytes = appProperties.uploads() != null
                ? appProperties.uploads().maxFileSizeBytes()
                : 2L * 1024 * 1024;
        this.mediaRootDir = Paths.get(configuredDir)
                .toAbsolutePath()
                .normalize()
                .resolve("project-media");
    }

    public StoredScreenshot storeScreenshot(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Please choose a PNG, JPG, or WebP screenshot to upload.",
                    Map.of("file", "Screenshot file is required.")
            );
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FILE_TOO_LARGE",
                    "Screenshot exceeds the 2 MB limit.",
                    Map.of("file", "Screenshot must be 2 MB or smaller.")
            );
        }

        String declaredType = file.getContentType();
        if (declaredType != null && !declaredType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(declaredType.toLowerCase())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FILE_TYPE",
                    "Only PNG, JPG, or WebP screenshots are allowed.",
                    Map.of("file", "Only PNG, JPG, or WebP screenshots are allowed.")
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_ERROR", "Unable to read uploaded file.");
        }

        if (bytes.length == 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Uploaded file is empty.",
                    Map.of("file", "Uploaded file is empty.")
            );
        }

        if (bytes.length > maxFileSizeBytes) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "FILE_TOO_LARGE",
                    "Screenshot exceeds the 2 MB limit.",
                    Map.of("file", "Screenshot must be 2 MB or smaller.")
            );
        }

        DetectedImage detected = detectImageSignature(bytes);
        if (detected == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FILE_TYPE",
                    "Invalid image content. Only PNG, JPG, or WebP screenshots are allowed.",
                    Map.of("file", "Only valid PNG, JPG, or WebP images are allowed.")
            );
        }

        String randomFilename = UUID.randomUUID() + detected.extension();
        try {
            Files.createDirectories(mediaRootDir);
            Path target = mediaRootDir.resolve(randomFilename).normalize();
            if (!target.startsWith(mediaRootDir)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "Invalid storage path.");
            }
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not save screenshot.");
        }

        return new StoredScreenshot(randomFilename, detected.mimeType(), bytes.length);
    }

    public byte[] loadScreenshot(String storedFilename) {
        Path path = resolveSafePath(storedFilename);
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Screenshot file was not found.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not read screenshot.");
        }
    }

    public void deleteScreenshot(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }
        if (!SAFE_STORED_NAME.matcher(storedFilename).matches()) {
            return;
        }
        Path path = mediaRootDir.resolve(storedFilename).normalize();
        if (!path.startsWith(mediaRootDir)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup of local storage file.
        }
    }

    private Path resolveSafePath(String storedFilename) {
        if (storedFilename == null || !SAFE_STORED_NAME.matcher(storedFilename).matches()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Screenshot file was not found.");
        }
        Path resolved = mediaRootDir.resolve(storedFilename).normalize();
        if (!resolved.startsWith(mediaRootDir)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Screenshot file was not found.");
        }
        return resolved;
    }

    private DetectedImage detectImageSignature(byte[] b) {
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 0x50
                && b[2] == 0x4E
                && b[3] == 0x47
                && b[4] == 0x0D
                && b[5] == 0x0A
                && b[6] == 0x1A
                && b[7] == 0x0A) {
            return new DetectedImage("image/png", ".png");
        }
        // JPEG: FF D8 FF
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF) {
            return new DetectedImage("image/jpeg", ".jpg");
        }
        // WebP: RIFF....WEBP
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new DetectedImage("image/webp", ".webp");
        }
        return null;
    }

    public Path getMediaRootDir() {
        return mediaRootDir;
    }

    private record DetectedImage(String mimeType, String extension) {
    }

    public record StoredScreenshot(String storedFilename, String contentType, long fileSizeBytes) {
    }
}
