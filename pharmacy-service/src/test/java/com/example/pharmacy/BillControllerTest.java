package com.example.pharmacy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillControllerTest {
    private MockMvc mvc(String rate) {
        return MockMvcBuilders.standaloneSetup(new BillController(new BigDecimal(rate))).build();
    }

    @Test
    void calculatesMedicineTotalPlusVat() throws Exception {
        mvc("10").perform(post("/api/v1/bill").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicineTotal\":100000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicineTotal").value(100000))
                .andExpect(jsonPath("$.vatRate").value(10))
                .andExpect(jsonPath("$.vatAmount").value(10000))
                .andExpect(jsonPath("$.totalAmount").value(110000));
    }

    @Test
    void calculatesFractionalPercentageWithDecimalArithmetic() {
        BillResponse result = new BillController(new BigDecimal("8.5"))
                .calculateBill(new BillRequest(new BigDecimal("123.45")));
        assertThat(result.vatAmount()).isEqualByComparingTo("10.49");
        assertThat(result.totalAmount()).isEqualByComparingTo("133.94");
    }

    @Test
    void roundsHalfCentUp() {
        BillResponse result = new BillController(new BigDecimal("10"))
                .calculateBill(new BillRequest(new BigDecimal("0.05")));
        assertThat(result.vatAmount()).isEqualByComparingTo("0.01");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.06");
    }

    @Test
    void acceptsZeroTaxAndZeroTotal() throws Exception {
        mvc("0").perform(post("/api/v1/bill").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicineTotal\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"medicineTotal\":null}", "{\"medicineTotal\":-1}",
            "{\"medicineTotal\":1.001}", "{\"medicineTotal\":1000000000000000}",
            "{\"medicineTotal\":\"abc\"}"})
    void rejectsInvalidTotals(String json) throws Exception {
        mvc("10").perform(post("/api/v1/bill").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100.01"})
    void rejectsInvalidVatConfiguration(String rate) {
        assertThatIllegalArgumentException().isThrownBy(() -> new BillController(new BigDecimal(rate)));
    }
}
