// Applies the inventory quantity, price and currency rules independently of storage.
package com.example.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.example.stock.InventoryModels.InboundValues;

final class InventoryMath {
    private static final int PRICE_SCALE = 6;
    private static final int AMOUNT_SCALE = 2;
    private static final int MAX_INTEGER_DIGITS = 18;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private InventoryMath() {
    }

    /** Completes an inbound from two inputs or validates a supplied triple at the published scales. */
    static InboundValues inboundValues(Integer quantity, BigDecimal price, BigDecimal amount) {
        int missing = (quantity == null ? 1 : 0) + (price == null ? 1 : 0) + (amount == null ? 1 : 0);
        if (missing > 1) {
            throw new IllegalArgumentException("数量、单价和金额至少填写两项");
        }
        if (quantity != null && quantity <= 0) {
            throw new IllegalArgumentException("数量必须为正整数");
        }

        BigDecimal unitPrice = price == null ? null : checkedDecimal(price, PRICE_SCALE, "单价");
        BigDecimal total = amount == null ? null : checkedDecimal(amount, AMOUNT_SCALE, "金额");
        if (unitPrice == null) {
            return new InboundValues(quantity, unitPrice(quantity, total), total);
        }
        if (total == null) {
            return new InboundValues(quantity, unitPrice, checkedDecimal(
                    multipliedAmount(quantity, unitPrice), AMOUNT_SCALE, "金额"));
        }
        if (quantity == null) {
            return new InboundValues(deriveQuantity(unitPrice, total), unitPrice, total);
        }

        // A previously saved amount-based input must round-trip when the form sends all three values.
        if (!multipliedAmount(quantity, unitPrice).equals(total)
                && !unitPrice(quantity, total).equals(unitPrice)) {
            throw new IllegalArgumentException("数量、单价和金额不一致");
        }
        return new InboundValues(quantity, unitPrice, total);
    }

    /** Adds quantities with overflow detection so the transaction can reject an oversized balance. */
    static int addQuantity(int currentQuantity, int incomingQuantity) {
        return Math.addExact(currentQuantity, incomingQuantity);
    }

    /** Checks whether an accumulated balance fits the database currency column. */
    static BigDecimal balanceAmount(BigDecimal amount) {
        return checkedDecimal(amount, AMOUNT_SCALE, "库存金额");
    }

    /** Calculates the display price from the balance, using zero for an empty stock. */
    static BigDecimal unitPrice(int quantity, BigDecimal amount) {
        if (quantity == 0) {
            return BigDecimal.ZERO.setScale(PRICE_SCALE);
        }
        return amount.divide(BigDecimal.valueOf(quantity), PRICE_SCALE, ROUNDING);
    }

    /** Allocates exact proportional cost and charges any remaining cents when a movement clears stock. */
    static BigDecimal outboundAmount(int quantity, int availableQuantity, BigDecimal availableAmount) {
        if (quantity == availableQuantity) {
            return availableAmount;
        }
        return availableAmount.multiply(BigDecimal.valueOf(quantity))
                .divide(BigDecimal.valueOf(availableQuantity), AMOUNT_SCALE, ROUNDING);
    }

    private static int deriveQuantity(BigDecimal unitPrice, BigDecimal amount) {
        if (unitPrice.signum() == 0 || amount.signum() == 0) {
            throw new IllegalArgumentException("单价或金额为零时请填写数量");
        }
        BigDecimal rounded = amount.divide(unitPrice, 0, ROUNDING);
        int quantity;
        try {
            quantity = rounded.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("数量不能超过 2147483647");
        }
        if (!multipliedAmount(quantity, unitPrice).equals(amount)) {
            throw new IllegalArgumentException("单价和金额无法计算出整数数量");
        }
        return quantity;
    }

    private static BigDecimal multipliedAmount(int quantity, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(AMOUNT_SCALE, ROUNDING);
    }

    private static BigDecimal checkedDecimal(BigDecimal value, int scale, String name) {
        if (value.signum() < 0 || value.precision() - (long) value.scale() > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException(name + "必须非负，且整数部分最多保留 18 位");
        }
        if (value.stripTrailingZeros().scale() > scale) {
            throw new IllegalArgumentException(name + "最多保留 " + scale + " 位小数");
        }
        return value.setScale(scale);
    }
}
