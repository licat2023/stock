// Defines API records and the values shared by inventory calculations and SQL mappings.
package com.example.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;

final class InventoryModels {
    private InventoryModels() {
    }

    record Commodity(long id, String name, String spec) {}

    record CommodityInput(String name, String spec) {}

    record Warehouse(long id, String name, String address) {}

    record WarehouseInput(String name, String address) {}

    record Pair(long commodityId, long warehouseId) {}

    record InboundInput(Long commodityId, Long warehouseId, Integer quantity, BigDecimal unitPrice,
                        BigDecimal amount) {}

    record OutboundInput(Long commodityId, Long warehouseId, Integer quantity) {}

    record InboundValues(int quantity, BigDecimal unitPrice, BigDecimal amount) {}

    record Document(long id, long commodityId, String commodityName, long warehouseId, String warehouseName,
                    int quantity, @JsonFormat(shape = STRING) BigDecimal unitPrice,
                    @JsonFormat(shape = STRING) BigDecimal amount,
                    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime createdAt,
                    int previousQuantity, @JsonFormat(shape = STRING) BigDecimal previousUnitPrice,
                    @JsonFormat(shape = STRING) BigDecimal previousAmount) {}

    record Movement(long id, boolean outbound, int quantity, BigDecimal unitPrice, BigDecimal amount,
                    int previousQuantity, BigDecimal previousUnitPrice, BigDecimal previousAmount) {
        Balance previousBalance() {
            return new Balance(previousQuantity, previousUnitPrice, previousAmount);
        }
    }

    record Balance(int quantity, BigDecimal unitPrice, BigDecimal amount) {
        static final Balance EMPTY = new Balance(0, new BigDecimal("0.000000"), new BigDecimal("0.00"));
    }

    record Stock(long commodityId, String commodityName, long warehouseId, String warehouseName,
                 int quantity, @JsonFormat(shape = STRING) BigDecimal unitPrice,
                 @JsonFormat(shape = STRING) BigDecimal amount) {}

    record Quote(int availableQuantity, @JsonFormat(shape = STRING) BigDecimal unitPrice,
                 @JsonFormat(shape = STRING) BigDecimal amount) {}

    /** Holds the key returned by JDBC after a single-row insert. */
    static final class GeneratedId {
        private long value;

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }
    }
}
