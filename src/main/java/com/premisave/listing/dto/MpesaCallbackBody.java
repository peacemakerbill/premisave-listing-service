package com.premisave.listing.dto;

import lombok.Data;
import java.util.List;

@Data
public class MpesaCallbackBody {

    private String MerchantRequestID;
    private String CheckoutRequestID;
    private String ResultCode;
    private String ResultDesc;

    private CallbackMetadata CallbackMetadata;

    @Data
    public static class CallbackMetadata {
        private List<Item> Item;

        @Data
        public static class Item {
            private String Name;
            private String Value;
        }
    }
}