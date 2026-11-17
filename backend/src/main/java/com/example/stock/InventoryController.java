// Exposes the master-data, document and stock APIs used by the Vue application.
package com.example.stock;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.example.stock.InventoryModels.*;

@RestController
@RequestMapping("/api")
class InventoryController {
    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/commodities")
    List<Commodity> commodities() {
        return inventory.commodities();
    }

    @PostMapping("/commodities")
    @ResponseStatus(HttpStatus.CREATED)
    Commodity createCommodity(@RequestBody CommodityInput input) {
        return inventory.createCommodity(input);
    }

    @PutMapping("/commodities/{id}")
    Commodity updateCommodity(@PathVariable long id, @RequestBody CommodityInput input) {
        return inventory.updateCommodity(id, input);
    }

    @DeleteMapping("/commodities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCommodity(@PathVariable long id) {
        inventory.deleteCommodity(id);
    }

    @GetMapping("/warehouses")
    List<Warehouse> warehouses() {
        return inventory.warehouses();
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    Warehouse createWarehouse(@RequestBody WarehouseInput input) {
        return inventory.createWarehouse(input);
    }

    @PutMapping("/warehouses/{id}")
    Warehouse updateWarehouse(@PathVariable long id, @RequestBody WarehouseInput input) {
        return inventory.updateWarehouse(id, input);
    }

    @DeleteMapping("/warehouses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteWarehouse(@PathVariable long id) {
        inventory.deleteWarehouse(id);
    }

    @GetMapping("/inbounds")
    List<Document> inbounds() {
        return inventory.inbounds();
    }

    @PostMapping("/inbounds")
    @ResponseStatus(HttpStatus.CREATED)
    Document createInbound(@RequestBody InboundInput input) {
        return inventory.createInbound(input);
    }

    @PutMapping("/inbounds/{id}")
    Document updateInbound(@PathVariable long id, @RequestBody InboundInput input) {
        return inventory.updateInbound(id, input);
    }

    @DeleteMapping("/inbounds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteInbound(@PathVariable long id) {
        inventory.deleteInbound(id);
    }

    @GetMapping("/outbounds")
    List<Document> outbounds() {
        return inventory.outbounds();
    }

    @PostMapping("/outbounds")
    @ResponseStatus(HttpStatus.CREATED)
    Document createOutbound(@RequestBody OutboundInput input) {
        return inventory.createOutbound(input);
    }

    /** Returns the current quantity and price, plus a provisional cost when quantity is supplied. */
    @GetMapping("/outbounds/quote")
    Quote quote(@RequestParam long commodityId, @RequestParam long warehouseId,
                @RequestParam(required = false) Integer quantity) {
        return inventory.quote(commodityId, warehouseId, quantity);
    }

    @GetMapping("/stocks")
    List<Stock> stocks() {
        return inventory.stocks();
    }
}
