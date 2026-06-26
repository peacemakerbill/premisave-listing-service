package com.premisave.listing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaC2BService {

    private final RestTemplate restTemplate;
    private final MpesaService mpesaService;

    @Value("${mpesa.daraja.shortcode}")
    private String shortcode;

    @Value("${mpesa.daraja.confirmation-url}")
    private String confirmationUrl;

    @Value("${mpesa.daraja.validation-url}")
    private String validationUrl;

    @Value("${mpesa.daraja.environment:sandbox}")
    private String environment;

    private String getBaseUrl() {
        return "sandbox".equals(environment) 
                ? "https://sandbox.safaricom.co.ke" 
                : "https://api.safaricom.co.ke";
    }

    @SuppressWarnings("rawtypes")
	public void registerC2BUrls() {
        String accessToken = mpesaService.getAccessToken();
        String url = getBaseUrl() + "/mpesa/c2b/v2/registerurl";

        Map<String, String> payload = new HashMap<>();
        payload.put("ShortCode", shortcode);
        payload.put("ResponseType", "Completed");
        payload.put("ConfirmationURL", confirmationUrl);
        payload.put("ValidationURL", validationUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            log.info("M-Pesa C2B URLs Registration Response: {}", response.getBody());
        } catch (Exception e) {
            log.error("Failed to register M-Pesa C2B URLs: {}", e.getMessage());
            throw new RuntimeException("C2B Registration failed", e);
        }
    }
}