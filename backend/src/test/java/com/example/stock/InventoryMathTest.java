// Tests inbound derivation and exact cost allocation at the supported decimal scales.
package com.example.stock;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryMathTest {
    @Test
    void derivesAmountFromQuantityAndPrice() {
        var inbound = InventoryMath.inboundValues(3, decimal("0.335"), null);
        assertEquals(decimal("1.01"), inbound.amount());
    }

    @Test
    void derivesPriceFromQuantityAndAmountAndAcceptsItOnResave() {
        var inbound = InventoryMath.inboundValues(100_000, null, decimal("0.01"));
        assertEquals(decimal("0.000000"), inbound.unitPrice());
        assertEquals(inbound, InventoryMath.inboundValues(
                inbound.quantity(), inbound.unitPrice(), inbound.amount()));
    }

    @Test
    void derivesQuantityFromPriceAndRoundedAmount() {
        var inbound = InventoryMath.inboundValues(null, decimal("0.333333"), decimal("1.00"));
        assertEquals(3, inbound.quantity());
    }

    @Test
    void rejectsPriceAndAmountThatCannotDescribeAWholeQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryMath.inboundValues(null, decimal("2"), decimal("3")));
    }

    @Test
    void requiresAnExplicitQuantityWhenEitherMonetaryInputIsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryMath.inboundValues(null, decimal("0.004"), decimal("0.00")));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryMath.inboundValues(null, decimal("0"), decimal("0")));
        assertEquals(decimal("0.00"), InventoryMath.inboundValues(1, decimal("0"), null).amount());
    }

    @Test
    void rejectsAnInconsistentTriple() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryMath.inboundValues(3, decimal("0.3"), decimal("1.00")));
    }

    @Test
    void rejectsMissingValuesAndExcessPrecision() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryMath.inboundValues(1, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryMath.inboundValues(1, decimal("0.0000001"), null));
    }

    @Test
    void rejectsStockBalanceOutsideDatabaseBounds() {
        assertThrows(ArithmeticException.class, () -> InventoryMath.addQuantity(Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class,
                () -> InventoryMath.balanceAmount(decimal("1000000000000000000.00")));
    }

    @Test
    void allocatesFromTheExactRatioInsteadOfTheDisplayedPrice() {
        assertEquals(decimal("0.000000"), InventoryMath.unitPrice(4_000_000, decimal("0.01")));
        assertEquals(decimal("0.01"), InventoryMath.outboundAmount(
                2_000_000, 4_000_000, decimal("0.01")));
    }

    @Test
    void chargesTheRemainingBalanceWhenTheStockIsCleared() {
        assertEquals(decimal("0.01"), InventoryMath.outboundAmount(4_000_000, 4_000_000, decimal("0.01")));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
