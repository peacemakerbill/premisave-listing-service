package com.premisave.listing.dto.auth_service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WhoIViewedResponse {

    @JsonProperty("targetId")
    private String targetId;

    @JsonProperty("targetName")
    private String targetName;

    @JsonProperty("targetProfilePicture")
    private String targetProfilePicture;

    @JsonProperty("targetUsername")
    private String targetUsername;

    @JsonProperty("viewedAt")
    private LocalDateTime viewedAt;

    @JsonProperty("deviceType")
    private String deviceType;

    @JsonProperty("source")
    private String source;
}
