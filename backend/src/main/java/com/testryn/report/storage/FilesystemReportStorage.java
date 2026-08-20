package com.testryn.report.storage;

import com.testryn.common.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * MVP {@link ReportStorage} implementation: stores files below a configurable base
 * directory (a persistent Docker volume in Compose). Storage keys are always
 * server-generated (project key + random UUID) — the client-supplied filename is used
 * only as a display-time metadatum, never to build a filesystem path, so a malicious
 * filename cannot escape the base directory.
 */
@Component
public class FilesystemReportStorage implements ReportStorage {

    private static final Pattern SAFE_PROJECT_KEY = Pattern.compile("^[A-Z0-9]{1,20}$");

    private final Path basePath;

    public FilesystemReportStorage(@Value("${testryn.storage.base-path}") String basePath) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize storage base path: " + this.basePath, e);
        }
    }

    @Override
    public StoredObject store(String projectKey, String originalFilename, String contentType, InputStream content,
                               long size) throws IOException {
        if (!SAFE_PROJECT_KEY.matcher(projectKey).matches()) {
            throw new BadRequestException("Invalid project key for storage: " + projectKey);
        }
        String extension = extractSafeExtension(originalFilename);
        String storageKey = projectKey + "/" + UUID.randomUUID() + extension;

        Path target = resolveWithinBase(storageKey);
        Files.createDirectories(target.getParent());

        MessageDigest digest = sha256();
        long bytesWritten = 0;
        try (InputStream in = content) {
            byte[] buffer = new byte[8192];
            try (var out = Files.newOutputStream(target)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    bytesWritten += read;
                }
            }
        }
        String checksum = HexFormat.of().formatHex(digest.digest());
        return new StoredObject(storageKey, bytesWritten, checksum);
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolveWithinBase(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new com.testryn.common.error.NotFoundException("Stored object not found: " + storageKey);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveWithinBase(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete stored object: " + storageKey, e);
        }
    }

    private Path resolveWithinBase(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new BadRequestException("Invalid storage key");
        }
        return resolved;
    }

    private String extractSafeExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String name = Path.of(originalFilename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot).toLowerCase();
        // Keep only a short, alphanumeric extension; drop anything unexpected rather
        // than propagating client-controlled bytes into a filesystem path.
        return ext.matches("^\\.[a-z0-9]{1,10}$") ? ext : "";
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
