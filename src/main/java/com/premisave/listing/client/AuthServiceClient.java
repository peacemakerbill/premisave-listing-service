package com.premisave.listing.client;

import com.premisave.listing.dto.UserProfileSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service", url = "${auth.service.url:http://localhost:8080}")
public interface AuthServiceClient {

    @GetMapping("/profile/me")
    UserProfileSummary getCurrentUserProfile(@RequestHeader("Authorization") String authorization);

    @GetMapping("/profile/user/{userId}")
    UserProfileSummary getUserPublicProfile(@PathVariable String userId, 
                                           @RequestHeader("Authorization") String authorization);
}