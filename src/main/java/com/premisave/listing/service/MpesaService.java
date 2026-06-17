package com.premisave.listing.service;

import com.premisave.listing.dto.MpesaStkPushRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaService {

    private final RestTemplate restTemplate;

    @Value("${mpesa.daraja.consumer-key}")
    private String consumerKey;

    @Value("${mpesa.daraja.consumer-secret}")
    private String consumerSecret;

    @Value("${mpesa.daraja.shortcode}")
    private String shortcode;

    @Value("${mpesa.daraja.passkey}")
    private String passkey;

    @Value("${mpesa.daraja.callback-url}")
    private String callbackUrl;

    @Value("${mpesa.daraja.environment:sandbox}")
    private String environment;

    private String getBaseUrl() {
        return "sandbox".equals(environment) ? 
                "https://sandbox.safaricom.co.ke" : 
                "https://api.safaricom.co.ke";
    }

    public String getAccessToken() {
        String url = getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials";
        String auth = Base64.getEncoder().encodeToString((consumerKey + ":" + consumerSecret).getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + auth);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (String) response.getBody().get("access_token");
    }

    public Map<String, Object> initiateStkPush(MpesaStkPushRequest request) {
        String accessToken = getAccessToken();
        String url = getBaseUrl() + "/mpesa/stkpush/v1/processrequest";

        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String password = Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes());

        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", "CustomerPayBillOnline");
        payload.put("Amount", request.getAmount());
        payload.put("PartyA", request.getPhoneNumber());
        payload.put("PartyB", shortcode);
        payload.put("PhoneNumber", request.getPhoneNumber());
        payload.put("CallBackURL", callbackUrl);
        payload.put("AccountReference", request.getAccountReference());
        payload.put("TransactionDesc", request.getTransactionDesc() != null ? request.getTransactionDesc() : "Premisave Payment");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        
        log.info("M-Pesa STK Push Response: {}", response.getBody());
        return response.getBody();
    }
}