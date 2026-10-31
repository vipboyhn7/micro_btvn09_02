package com.example.pharmacy;

import java.math.BigDecimal;

public record BillResponse(BigDecimal medicineTotal, BigDecimal vatRate,
                           BigDecimal vatAmount, BigDecimal totalAmount) {
}
