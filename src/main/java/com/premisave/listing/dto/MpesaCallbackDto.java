package com.premisave.listing.dto;

import lombok.Data;
import java.util.Map;

@Data
public class MpesaCallbackDto {
    private String MerchantRequestID;
    private String CheckoutRequestID;
    private String ResultCode;
    private String ResultDesc;
    private Map<String, Object> CallbackMetadata;
}