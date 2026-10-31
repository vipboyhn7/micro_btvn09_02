package com.example.pharmacy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RefreshScope
@RestController
public class BillController {
    private final BigDecimal vatRate;

    public BillController(@Value("${pharmacy.vat-rate}") BigDecimal vatRate) {
        if (vatRate.signum() < 0 || vatRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("pharmacy.vat-rate must be between 0 and 100 percent");
        }
        this.vatRate = vatRate;
    }

    @PostMapping("/api/v1/bill")
    public BillResponse calculateBill(@Valid @RequestBody BillRequest request) {
        BigDecimal medicineTotal = request.medicineTotal().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal vatAmount = medicineTotal.multiply(vatRate)
                .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        return new BillResponse(medicineTotal, vatRate, vatAmount, medicineTotal.add(vatAmount));
    }
}
