package com.premisave.listing.service;

import com.premisave.listing.config.StorageProperties;
import com.premisave.listing.enums.StorageMode;
import com.premisave.listing.entity.ImageAsset;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.exception.StorageUnavailableException;
import com.premisave.listing.storage.ImageProcessor;
import com.premisave.listing.storage.ImageSize;
import com.premisave.listing.repository.ImageAssetRepository;
import com.premisave.listing.storage.StoredImage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Keeps listing pictures in a folder, local or on a mounted network share,
 * chosen by the STORAGE_* settings (same names as wallet-service).
 *
 * Every upload becomes three clean JPEG files (thumb, medium, original)
 * named after a random id, never after the client's file name:
 * <base>/<first two chars of id>/<id>_thumb.jpg and so on. The database only
 * ever holds the URL, which ends in /images/<id>.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageStorageService {

    public static final Pattern FILE_ID = Pattern.compile("^[a-f0-9]{32}$");
    private static final String URL_PATH = "/images/";
    private static final long HEALTH_CACHE_MS = 5_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StorageProperties props;
    private final ImageAssetRepository assets;

    private Path base;
    private volatile long lastCheckAt;
    private volatile String lastProblem;

    // ====================== STARTUP ======================

    @PostConstruct
    void init() {
        if (props.getMode() == StorageMode.NETWORK) {
            StorageProperties.Network net = props.getNetwork();
            if (isBlank(net.getMountPath())) {
                throw new IllegalStateException(
                        "STORAGE_MODE is NETWORK but STORAGE_NETWORK_MOUNT_PATH is not set.");
            }
            Path mount = Paths.get(net.getMountPath()).toAbsolutePath().normalize();
            if (net.isRequireMount() && !isMounted(mount)) {
                throw new IllegalStateException("Storage share is not mounted at " + mount
                        + " (" + net.getProtocol() + " //" + net.getHost() + "/" + net.getShare()
                        + "). Mount it, or set STORAGE_NETWORK_REQUIRE_MOUNT=false for development.");
            }
            String dir = isBlank(net.getDirectory()) ? "" : net.getDirectory();
            base = mount.resolve(dir).normalize();
            if (!base.startsWith(mount)) {
                throw new IllegalStateException("STORAGE_NETWORK_DIRECTORY must stay inside the mount path.");
            }
        } else {
            if (isBlank(props.getRoot())) {
                throw new IllegalStateException("STORAGE_MODE is LOCAL but STORAGE_ROOT is not set.");
            }
            base = Paths.get(props.getRoot()).toAbsolutePath().normalize();
        }

        try {
            Files.createDirectories(base);
            probeWritable(base);
        } catch (IOException e) {
            throw new IllegalStateException("Image folder " + base + " is not usable: " + e.getMessage(), e);
        }
        log.info("Image storage ready: mode={}, folder={}", props.getMode(), base);
    }

    // ====================== WRITE ======================

    /** Saves one upload. Throws IllegalArgumentException for a bad file, StorageUnavailableException if the folder is down. */
    public StoredImage store(MultipartFile file, String ownerId) {
        ensureAvailable();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("An empty file was uploaded.");
        }
        long maxBytes = props.getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Each image must be " + props.getMaxFileSizeMb() + " MB or smaller.");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("The uploaded file could not be read.");
        }

        BufferedImage image = ImageProcessor.decode(data, props.getMaxPixels());

        String fileId = newFileId();
        List<Path> written = new ArrayList<>();
        try {
            write(fileId, ImageSize.ORIGINAL, ImageProcessor.limitWidth(image, props.getOriginalMaxWidth()), written);
            write(fileId, ImageSize.MEDIUM, ImageProcessor.limitWidth(image, props.getMediumWidth()), written);
            write(fileId, ImageSize.THUMB, ImageProcessor.limitWidth(image, props.getThumbnailWidth()), written);
        } catch (IOException e) {
            written.forEach(this::deleteQuietly);
            markDown("Could not write to the image folder: " + e.getMessage());
            throw new StorageUnavailableException("Image storage is temporarily unavailable. Please try again later.", e);
        } catch (RuntimeException e) {
            written.forEach(this::deleteQuietly);
            throw e;
        }
        try {
            assets.save(new ImageAsset(fileId, ownerId, LocalDateTime.now()));
        } catch (RuntimeException e) {
            written.forEach(this::deleteQuietly);
            throw e;
        }
        return new StoredImage(fileId, urlFor(fileId));
    }

    /**
     * Saves several uploads, all or nothing: if one fails, the ones already
     * saved are removed again and the error is thrown.
     */
    public List<StoredImage> storeAll(List<MultipartFile> files, String ownerId) {
        List<StoredImage> stored = new ArrayList<>();
        if (files == null) {
            return stored;
        }
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                stored.add(store(file, ownerId));
            }
        } catch (RuntimeException e) {
            stored.forEach(s -> delete(s.fileId()));
            throw e;
        }
        return stored;
    }

    // ====================== READ ======================

    /** Path of a stored variant, for serving. Throws NotFoundException if there is none. */
    public Path resolve(String fileId, ImageSize size) {
        if (fileId == null || !FILE_ID.matcher(fileId).matches()) {
            throw new NotFoundException("Image not found.");
        }
        ensureAvailable();
        Path path = pathFor(fileId, size);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Image not found.");
        }
        return path;
    }

    // ====================== DELETE ======================

    /** Removes all variants of an image. Never throws; a failure is logged. */
    public void delete(String fileId) {
        if (fileId == null || !FILE_ID.matcher(fileId).matches()) {
            return;
        }
        for (ImageSize size : ImageSize.values()) {
            deleteQuietly(pathFor(fileId, size));
        }
        try {
            assets.deleteById(fileId);
        } catch (RuntimeException e) {
            log.warn("Could not delete image record {}: {}", fileId, e.getMessage());
        }
    }

    /**
     * Removes the stored images behind these URLs, but only those uploaded by
     * ownerId. Pass null as ownerId for an admin clean-up that skips the check.
     */
    public void deleteOwnedByUrls(java.util.Collection<String> urls, String ownerId) {
        if (urls == null) {
            return;
        }
        urls.stream().map(this::fileIdFromUrl).filter(java.util.Objects::nonNull).distinct().forEach(id -> {
            if (ownerId == null || isOwnedBy(id, ownerId)) {
                delete(id);
            }
        });
    }

    // ====================== OWNERSHIP ======================

    public boolean isOwnedBy(String fileId, String ownerId) {
        return ownerId != null && assets.findById(fileId).map(a -> ownerId.equals(a.getOwnerId())).orElse(false);
    }

    /** Every URL that points at our storage must belong to this user. Other URLs (old Cloudinary links) pass. */
    public void assertOwned(java.util.Collection<String> urls, String ownerId) {
        if (urls == null) {
            return;
        }
        for (String url : urls) {
            String id = fileIdFromUrl(url);
            if (id != null && !isOwnedBy(id, ownerId)) {
                throw new AccessDeniedException("You can only use images that you uploaded.");
            }
        }
    }

    // ====================== URLS ======================

    public String urlFor(String fileId) {
        return publicBase() + URL_PATH + fileId;
    }

    /** The image id inside one of our own URLs, or null for anything else (for example an old Cloudinary link). */
    public String fileIdFromUrl(String url) {
        if (url == null) {
            return null;
        }
        String prefix = publicBase() + URL_PATH;
        String rest;
        if (url.startsWith(prefix)) {
            rest = url.substring(prefix.length());
        } else if (url.startsWith(URL_PATH)) {
            rest = url.substring(URL_PATH.length());
        } else {
            return null;
        }
        int cut = rest.indexOf('?');
        if (cut >= 0) {
            rest = rest.substring(0, cut);
        }
        return FILE_ID.matcher(rest).matches() ? rest : null;
    }

    public int maxImagesPerListing() {
        return props.getMaxImagesPerListing();
    }

    // ====================== INTERNALS ======================

    private void write(String fileId, ImageSize size, BufferedImage image, List<Path> written) throws IOException {
        byte[] jpeg = ImageProcessor.toJpeg(image, props.getJpegQuality());
        Path target = pathFor(fileId, size);
        Files.createDirectories(target.getParent());
        // Temp file in the same folder, then one atomic move: a reader never sees half a file.
        Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
        try {
            Files.write(temp, jpeg);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            written.add(target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path pathFor(String fileId, ImageSize size) {
        return base.resolve(fileId.substring(0, 2)).resolve(fileId + "_" + size.suffix() + ".jpg");
    }

    private static String newFileId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String publicBase() {
        String origin = props.getPublicBaseUrl() == null ? "" : props.getPublicBaseUrl().trim();
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }

    /** Throws StorageUnavailableException if the folder or share cannot be used right now. Result is cached for a few seconds. */
    private void ensureAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < HEALTH_CACHE_MS) {
            if (lastProblem != null) {
                throw new StorageUnavailableException(lastProblem);
            }
            return;
        }
        String problem = null;
        if (props.getMode() == StorageMode.NETWORK && props.getNetwork().isRequireMount()
                && !isMounted(Paths.get(props.getNetwork().getMountPath()).toAbsolutePath().normalize())) {
            problem = "Image storage is unavailable: the network share is not mounted.";
        } else if (!Files.isDirectory(base) || !Files.isWritable(base)) {
            problem = "Image storage is unavailable: the image folder cannot be written to.";
        }
        lastProblem = problem;
        lastCheckAt = now;
        if (problem != null) {
            log.warn(problem);
            throw new StorageUnavailableException(problem);
        }
    }

    private void markDown(String reason) {
        lastProblem = "Image storage is unavailable. Please try again later.";
        lastCheckAt = System.currentTimeMillis();
        log.warn("Image storage marked down: {}", reason);
    }

    /**
     * True when the path is a mount point listed by the kernel. Checking only
     * that the folder exists is not enough: an unmounted share leaves an
     * empty local folder that quietly fills the disk instead.
     */
    static boolean isMounted(Path mount) {
        Path info = Paths.get("/proc/self/mountinfo");
        if (!Files.isReadable(info)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(info)) {
                String[] parts = line.split(" ");
                if (parts.length > 4) {
                    String mountPoint = parts[4]
                            .replace("\\040", " ").replace("\\011", "\t")
                            .replace("\\012", "\n").replace("\\134", "\\");
                    if (Paths.get(mountPoint).normalize().equals(mount)) {
                        return true;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read mount information: {}", e.getMessage());
        }
        return false;
    }

    private static void probeWritable(Path dir) throws IOException {
        Path probe = Files.createTempFile(dir, ".probe-", ".tmp");
        Files.deleteIfExists(probe);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (NoSuchFileException ignored) {
            // already gone
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
