package com.premisave.listing.service;

import com.premisave.listing.dto.MpesaStkPushRequest;
import com.premisave.listing.entity.Payment;
import com.premisave.listing.repository.PaymentRepository;
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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaService {

    private final RestTemplate restTemplate;
    private final PaymentRepository paymentRepository;

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
        return "sandbox".equals(environment)
                ? "https://sandbox.safaricom.co.ke"
                : "https://api.safaricom.co.ke";
    }

    // ====================== ACCESS TOKEN ======================

    public String getAccessToken() {
        String url = getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials";
        String auth = Base64.getEncoder().encodeToString((consumerKey + ":" + consumerSecret).getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + auth);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        if (response.getBody() == null || !response.getBody().containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain M-Pesa access token — empty response.");
        }
        return (String) response.getBody().get("access_token");
    }

    // ====================== STK PUSH ======================

    /**
     * Initiates an M-Pesa STK Push and updates the existing pending Payment record
     * with the CheckoutRequestID returned by Safaricom.
     *
     * This is critical: the callback from Safaricom will carry CheckoutRequestID,
     * and we need to match it back to our Payment via transactionRef.
     *
     * @param request    the STK push details (phone, amount, reference)
     * @param paymentId  the Payment.id already created by PaymentService — we update its transactionRef
     * @return the raw Safaricom response
     */
    public Map<String, Object> initiateStkPush(MpesaStkPushRequest request, String paymentId) {
        String accessToken = getAccessToken();
        String url = getBaseUrl() + "/mpesa/stkpush/v1/processrequest";

        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String password = Base64.getEncoder()
                .encodeToString((shortcode + passkey + timestamp).getBytes());

        Map<String, Object> payload = new HashMap<>();
        payload.put("BusinessShortCode", shortcode);
        payload.put("Password", password);
        payload.put("Timestamp", timestamp);
        payload.put("TransactionType", "CustomerPayBillOnline");
        payload.put("Amount", request.getAmount().intValue()); // Safaricom expects whole number
        payload.put("PartyA", request.getPhoneNumber());
        payload.put("PartyB", shortcode);
        payload.put("PhoneNumber", request.getPhoneNumber());
        payload.put("CallBackURL", callbackUrl);
        payload.put("AccountReference", request.getAccountReference());
        payload.put("TransactionDesc",
                request.getTransactionDesc() != null ? request.getTransactionDesc() : "Premisave Payment");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);

        Map<String, Object> body = response.getBody();
        log.info("M-Pesa STK Push Response: {}", body);

        if (body != null && paymentId != null) {
            // Store CheckoutRequestID as transactionRef so the callback can find this payment
            String checkoutRequestId = (String) body.get("CheckoutRequestID");
            if (checkoutRequestId != null && !checkoutRequestId.isBlank()) {
                List<Payment> payments = paymentRepository.findById(paymentId)
                        .map(List::of)
                        .orElse(List.of());
                if (!payments.isEmpty()) {
                    Payment payment = payments.get(0);
                    payment.setTransactionRef(checkoutRequestId);
                    paymentRepository.save(payment);
                    log.info("Payment {} transactionRef updated to CheckoutRequestID: {}",
                            paymentId, checkoutRequestId);
                }
            }
        }

        return body;
    }

    /**
     * Overload for backward compatibility — STK push without linking a paymentId.
     */
    public Map<String, Object> initiateStkPush(MpesaStkPushRequest request) {
        return initiateStkPush(request, null);
    }
}