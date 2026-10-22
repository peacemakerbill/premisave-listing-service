package com.premisave.listing.config;

import com.premisave.listing.enums.StorageMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the storage.* section in application.yml, filled from the same
 * STORAGE_* variables wallet-service uses. Images are kept in a folder: a
 * local one (LOCAL) or one on a network share that is mounted on this
 * machine (NETWORK).
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private StorageMode mode = StorageMode.LOCAL;

    /** Folder used when mode is LOCAL. */
    private String root = "./file-storage/premisave-listing";

    private Network network = new Network();

    /** Origin that serves GET /images/{id}; used to build the URLs saved on listings. */
    private String publicBaseUrl = "http://localhost:8082";

    private int maxFileSizeMb = 10;
    private int maxImagesPerListing = 20;
    private long maxPixels = 40_000_000L;
    private int thumbnailWidth = 320;
    private int mediumWidth = 960;
    private int originalMaxWidth = 2560;
    private float jpegQuality = 0.85f;

    @Data
    public static class Network {
        /** Informational (SMB, NFS, ...); the share itself is mounted by the operating system. */
        private String protocol = "SMB";
        private String host;
        private String share;

        /** Where the share is mounted on this machine. */
        private String mountPath;

        /** Folder for this service inside the mount. */
        private String directory = "premisave-listing";

        /** Refuse to start or serve if mountPath is not an actual mount. */
        private boolean requireMount = true;
    }
}
