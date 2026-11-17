// Declares the database operations used by the inventory transaction service.
package com.example.stock;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import static com.example.stock.InventoryModels.*;

@Mapper
interface InventoryMapper {
    List<Commodity> commodities();

    /** Locks an active commodity row for the enclosing transaction. */
    Long lockCommodity(long id);

    void insertCommodity(@Param("input") CommodityInput input, @Param("generated") GeneratedId generated);

    void updateCommodity(@Param("id") long id, @Param("input") CommodityInput input);

    boolean commodityHasDocuments(long id);

    void deactivateCommodity(long id);

    List<Warehouse> warehouses();

    /** Locks an active warehouse row for the enclosing transaction. */
    Long lockWarehouse(long id);

    void insertWarehouse(@Param("input") WarehouseInput input, @Param("generated") GeneratedId generated);

    void updateWarehouse(@Param("id") long id, @Param("input") WarehouseInput input);

    boolean warehouseHasDocuments(long id);

    void deactivateWarehouse(long id);

    List<Stock> stocks();

    Balance balance(Pair pair);

    /** Reads master-data existence and its balance from a single database snapshot. */
    Balance quotedBalance(Pair pair);

    void upsertBalance(@Param("pair") Pair pair, @Param("balance") Balance balance);

    void deleteBalance(Pair pair);

    /** Returns a UTC instant strictly after all existing movements for this pair. */
    LocalDateTime nextMovementTime(Pair pair);

    /** Orders both document types by timestamp with a deterministic order for ties. */
    List<Movement> history(Pair pair);

    List<Document> inbounds();

    Document inbound(long id);

    Pair inboundPair(long id);

    /** Locks and rereads an inbound only after its known master-data rows have been locked. */
    Pair lockInboundPair(long id);

    void insertInbound(@Param("pair") Pair pair, @Param("values") InboundValues values,
                       @Param("createdAt") LocalDateTime createdAt, @Param("previous") Balance previous,
                       @Param("generated") GeneratedId generated);

    void updateInbound(@Param("id") long id, @Param("pair") Pair pair, @Param("values") InboundValues values);

    void updateInboundSnapshot(@Param("id") long id, @Param("previous") Balance previous);

    void deleteInbound(long id);

    List<Document> outbounds();

    Document outbound(long id);

    void insertOutbound(@Param("pair") Pair pair, @Param("quantity") int quantity,
                        @Param("amount") BigDecimal amount, @Param("createdAt") LocalDateTime createdAt,
                        @Param("previous") Balance previous, @Param("generated") GeneratedId generated);

    void updateOutboundCost(@Param("id") long id, @Param("amount") BigDecimal amount,
                            @Param("previous") Balance previous);
}
