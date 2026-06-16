package com.premisave.listing.repository;

import com.premisave.listing.entity.PaymentReceipt;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentReceiptRepository extends MongoRepository<PaymentReceipt, String> {
}