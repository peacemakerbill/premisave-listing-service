package com.premisave.listing.dto;

import lombok.Data;
import java.util.Map;

@Data
public class MpesaCallbackResponse {

    private String MerchantRequestID;
    private String CheckoutRequestID;
    private String ResultCode;
    private String ResultDesc;

    // Flexible map for the full callback body (Recommended)
    private Map<String, Object> Body;
}