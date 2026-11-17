// Maintains inventory and document snapshots in atomic MySQL transactions.
package com.example.stock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.stock.ApiExceptionHandler.Failure;

import static com.example.stock.InventoryModels.*;

@Service
class InventoryService {
    private final InventoryMapper mapper;

    InventoryService(InventoryMapper mapper) {
        this.mapper = mapper;
    }

    List<Commodity> commodities() {
        return mapper.commodities();
    }

    Commodity createCommodity(CommodityInput input) {
        var normalized = new CommodityInput(name(input.name(), "商品名称"), optional(input.spec(), "商品规格"));
        var generated = new GeneratedId();
        mapper.insertCommodity(normalized, generated);
        return new Commodity(generated.getValue(), normalized.name(), normalized.spec());
    }

    /** Updates the active commodity while holding its catalog lock. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Commodity updateCommodity(long id, CommodityInput input) {
        var normalized = new CommodityInput(name(input.name(), "商品名称"), optional(input.spec(), "商品规格"));
        lockCommodity(id);
        mapper.updateCommodity(id, normalized);
        return new Commodity(id, normalized.name(), normalized.spec());
    }

    /** Deactivates a commodity only if neither movement table references it. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteCommodity(long id) {
        lockCommodity(id);
        if (mapper.commodityHasDocuments(id)) {
            throw Failure.conflict("该商品已有入库或出库记录，无法删除");
        }
        mapper.deactivateCommodity(id);
    }

    List<Warehouse> warehouses() {
        return mapper.warehouses();
    }

    Warehouse createWarehouse(WarehouseInput input) {
        var normalized = new WarehouseInput(name(input.name(), "仓库名称"), optional(input.address(), "仓库地址"));
        var generated = new GeneratedId();
        mapper.insertWarehouse(normalized, generated);
        return new Warehouse(generated.getValue(), normalized.name(), normalized.address());
    }

    /** Updates the active warehouse while holding its catalog lock. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Warehouse updateWarehouse(long id, WarehouseInput input) {
        var normalized = new WarehouseInput(name(input.name(), "仓库名称"), optional(input.address(), "仓库地址"));
        lockWarehouse(id);
        mapper.updateWarehouse(id, normalized);
        return new Warehouse(id, normalized.name(), normalized.address());
    }

    /** Deactivates a warehouse only if neither movement table references it. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteWarehouse(long id) {
        lockWarehouse(id);
        if (mapper.warehouseHasDocuments(id)) {
            throw Failure.conflict("该仓库已有入库或出库记录，无法删除");
        }
        mapper.deactivateWarehouse(id);
    }

    List<Stock> stocks() {
        return mapper.stocks();
    }

    List<Document> inbounds() {
        return mapper.inbounds();
    }

    /** Appends a receipt with its pre-movement snapshot and the corresponding balance adjustment. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Document createInbound(InboundInput input) {
        Pair pair = pair(input.commodityId(), input.warehouseId());
        InboundValues values = inboundValues(input);
        lockPair(pair);

        Balance previous = balance(pair);
        Balance next = receive(previous, values.quantity(), values.amount());
        var generated = new GeneratedId();
        mapper.insertInbound(pair, values, mapper.nextMovementTime(pair), previous, generated);
        storeBalance(pair, next);
        return mapper.inbound(generated.getValue());
    }

    /** Keeps the original receipt time and replays both histories when a receipt changes pair. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Document updateInbound(long id, InboundInput input) {
        Pair target = pair(input.commodityId(), input.warehouseId());
        InboundValues values = inboundValues(input);
        Pair source = existingInboundPair(id);

        lockPairs(source, target);
        requireUnchangedPair(id, source);
        mapper.updateInbound(id, target, values);
        replay(source);
        if (!target.equals(source)) {
            replay(target);
        }
        return mapper.inbound(id);
    }

    /** Removes a receipt only if all later issues can still be fulfilled in chronological order. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteInbound(long id) {
        Pair source = existingInboundPair(id);
        lockPair(source);
        requireUnchangedPair(id, source);
        mapper.deleteInbound(id);
        replay(source);
    }

    List<Document> outbounds() {
        return mapper.outbounds();
    }

    /** Calculates a provisional issue cost from a committed balance without reserving stock. */
    Quote quote(Long commodityId, Long warehouseId, Integer quantity) {
        Balance balance = mapper.quotedBalance(pair(commodityId, warehouseId));
        if (balance == null) {
            throw Failure.notFound("商品或仓库不存在");
        }
        BigDecimal amount = quantity == null ? null : issueAmount(balance, positiveQuantity(quantity));
        return new Quote(balance.quantity(), balance.unitPrice(), amount);
    }

    /** Rechecks availability under the catalog locks, then stores the issue and updated balance. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Document createOutbound(OutboundInput input) {
        Pair pair = pair(input.commodityId(), input.warehouseId());
        int quantity = positiveQuantity(input.quantity());
        lockPair(pair);

        Balance previous = balance(pair);
        BigDecimal amount = issueAmount(previous, quantity);
        var generated = new GeneratedId();
        mapper.insertOutbound(pair, quantity, amount, mapper.nextMovementTime(pair), previous, generated);
        storeBalance(pair, issue(previous, quantity, amount));
        return mapper.outbound(generated.getValue());
    }

    /** Rebuilds a pair from its ordered movements and rewrites changed snapshots and historical issue costs. */
    private void replay(Pair pair) {
        Balance current = Balance.EMPTY;
        for (Movement movement : mapper.history(pair)) {
            boolean snapshotChanged = !current.equals(movement.previousBalance());
            if (movement.outbound()) {
                if (movement.quantity() > current.quantity()) {
                    throw Failure.conflict("历史出库单 #" + movement.id() + " 库存不足，可用 "
                            + current.quantity() + "，需要 " + movement.quantity());
                }
                BigDecimal amount = InventoryMath.outboundAmount(
                        movement.quantity(), current.quantity(), current.amount());
                if (snapshotChanged || !amount.equals(movement.amount()) || !current.unitPrice().equals(movement.unitPrice())) {
                    mapper.updateOutboundCost(movement.id(), amount, current);
                }
                current = issue(current, movement.quantity(), amount);
            } else {
                if (snapshotChanged) {
                    mapper.updateInboundSnapshot(movement.id(), current);
                }
                current = receive(current, movement.quantity(), movement.amount());
            }
        }
        storeBalance(pair, current);
    }

    private Balance balance(Pair pair) {
        return Objects.requireNonNullElse(mapper.balance(pair), Balance.EMPTY);
    }

    private Balance receive(Balance previous, int quantity, BigDecimal amount) {
        int totalQuantity;
        BigDecimal totalAmount;
        try {
            totalQuantity = InventoryMath.addQuantity(previous.quantity(), quantity);
            totalAmount = InventoryMath.balanceAmount(previous.amount().add(amount));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw Failure.conflict("库存数量或金额超出可保存范围");
        }
        return new Balance(totalQuantity, InventoryMath.unitPrice(totalQuantity, totalAmount), totalAmount);
    }

    private BigDecimal issueAmount(Balance previous, int quantity) {
        if (quantity > previous.quantity()) {
            throw Failure.conflict("库存不足，当前可用数量为 " + previous.quantity());
        }
        return InventoryMath.outboundAmount(quantity, previous.quantity(), previous.amount());
    }

    private Balance issue(Balance previous, int quantity, BigDecimal amount) {
        int remainingQuantity = previous.quantity() - quantity;
        BigDecimal remainingAmount = previous.amount().subtract(amount);
        return new Balance(remainingQuantity, InventoryMath.unitPrice(remainingQuantity, remainingAmount),
                remainingAmount);
    }

    private void storeBalance(Pair pair, Balance balance) {
        if (balance.quantity() == 0) {
            mapper.deleteBalance(pair);
        } else {
            mapper.upsertBalance(pair, balance);
        }
    }

    private Pair existingInboundPair(long id) {
        Pair pair = mapper.inboundPair(id);
        if (pair == null) {
            throw Failure.notFound("入库单不存在");
        }
        return pair;
    }

    /** Rechecks the pair after locks have been taken since another edit may have moved the receipt. */
    private void requireUnchangedPair(long id, Pair expected) {
        Pair actual = mapper.lockInboundPair(id);
        if (actual == null) {
            throw Failure.notFound("入库单不存在");
        }
        if (!expected.equals(actual)) {
            throw Failure.conflict("入库单的商品或仓库已被修改，请刷新后重试");
        }
    }

    private void lockPair(Pair pair) {
        lockCommodity(pair.commodityId());
        lockWarehouse(pair.warehouseId());
    }

    /** Takes catalog locks in ascending commodity IDs, followed by ascending warehouse IDs. */
    private void lockPairs(Pair first, Pair second) {
        lockCommodity(Math.min(first.commodityId(), second.commodityId()));
        if (first.commodityId() != second.commodityId()) {
            lockCommodity(Math.max(first.commodityId(), second.commodityId()));
        }
        lockWarehouse(Math.min(first.warehouseId(), second.warehouseId()));
        if (first.warehouseId() != second.warehouseId()) {
            lockWarehouse(Math.max(first.warehouseId(), second.warehouseId()));
        }
    }

    private void lockCommodity(long id) {
        if (mapper.lockCommodity(id) == null) {
            throw Failure.notFound("商品不存在");
        }
    }

    private void lockWarehouse(long id) {
        if (mapper.lockWarehouse(id) == null) {
            throw Failure.notFound("仓库不存在");
        }
    }

    private Pair pair(Long commodityId, Long warehouseId) {
        if (commodityId == null || commodityId <= 0 || warehouseId == null || warehouseId <= 0) {
            throw Failure.badRequest("请选择商品和仓库");
        }
        return new Pair(commodityId, warehouseId);
    }

    private int positiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw Failure.badRequest("数量必须为正整数");
        }
        return quantity;
    }

    private InboundValues inboundValues(InboundInput input) {
        try {
            return InventoryMath.inboundValues(input.quantity(), input.unitPrice(), input.amount());
        } catch (IllegalArgumentException exception) {
            throw Failure.badRequest(exception.getMessage());
        }
    }

    private String name(String value, String field) {
        String normalized = optional(value, field);
        if (normalized == null) {
            throw Failure.badRequest(field + "不能为空");
        }
        return normalized;
    }

    private String optional(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > 255) {
            throw Failure.badRequest(field + "不能超过 255 个字符");
        }
        return normalized;
    }
}
