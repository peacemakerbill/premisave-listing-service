package com.premisave.listing.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Who uploaded a stored image. Lets the service refuse to attach or delete
 * somebody else's picture just because its URL was pasted into a request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "image_assets")
public class ImageAsset {

    /** The image's file id. */
    @Id
    private String fileId;

    @Indexed
    private String ownerId;

    private LocalDateTime createdAt;
}
