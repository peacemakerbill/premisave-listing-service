package com.premisave.listing.controller;

import com.premisave.listing.service.ImageStorageService;
import com.premisave.listing.storage.ImageSize;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Public image endpoint: GET /images/{fileId}?size=thumb|medium|original.
 * The id is a random 32-character hex string and nothing else is accepted,
 * so a path can never leave the image folder. In production, put nginx or a
 * CDN in front of this (or serve the folder directly); the cache headers
 * below make that safe, since an image id never changes its content.
 */
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageStorageService storage;

    @GetMapping("/{fileId}")
    public ResponseEntity<FileSystemResource> get(
            @PathVariable String fileId,
            @RequestParam(required = false) String size,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) throws IOException {

        ImageSize imageSize = ImageSize.parse(size);
        Path path = storage.resolve(fileId, imageSize);

        String etag = "\"" + fileId + "-" + imageSize.suffix() + "\"";
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .contentLength(Files.size(path))
                .eTag(etag)
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}