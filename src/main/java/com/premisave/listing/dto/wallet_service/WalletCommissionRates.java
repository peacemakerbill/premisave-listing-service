package com.premisave.listing.dto.wallet_service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** Data part of wallet-service's GET /internal/commission/rates. Rates are fractions: 0.01 is 1%. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletCommissionRates {
    private BigDecimal internalTransferRate;
    private BigDecimal gatewayRate;
    private String currency;
    private String chargedOn;
    private Integer commissionScale;
    private String commissionRounding;
}
