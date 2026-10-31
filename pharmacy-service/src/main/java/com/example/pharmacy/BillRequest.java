package com.example.pharmacy;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record BillRequest(
        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 15, fraction = 2)
        BigDecimal medicineTotal) {
}
