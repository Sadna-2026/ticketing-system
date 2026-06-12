package com.ticketing.presentation.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.application.SelectionRequest;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.services.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> createOrder(@RequestHeader("X-Session-Token") String token, @RequestParam UUID eventId) {
        UUID orderId = orderService.createOrder(token, eventId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(orderId));
    }

    @PostMapping("/current/items/seat")
    public ResponseEntity<ApiResponse<UUID>> addSeatToOrder(@RequestHeader("X-Session-Token") String token, @RequestParam UUID eventId, @RequestParam UUID zoneId, @RequestParam UUID seatId) {
        UUID itemId = orderService.addSeatToOrder(token, eventId, zoneId, seatId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(itemId));
    }

    @PostMapping("/current/items/ga")
    public ResponseEntity<ApiResponse<UUID>> addGATicketsToOrder(@RequestHeader("X-Session-Token") String token, @RequestParam UUID eventId, @RequestParam UUID zoneId, @RequestParam int quantity) {
        UUID itemId = orderService.addGATicketsToOrder(token, eventId, zoneId, quantity);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(itemId));
    }

    @PostMapping("/current/items/selection")
    public ResponseEntity<ApiResponse<List<UUID>>> addSelectionToOrder(@RequestHeader("X-Session-Token") String token, @RequestBody SelectionRequest request) {
        List<UUID> itemIds = orderService.addSelectionToOrder(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(itemIds));
    }

    @PostMapping("/current/checkout")
    public ResponseEntity<ApiResponse<UUID>> checkout(@RequestHeader("X-Session-Token") String token, @RequestParam(required = false) String couponCode) {
        OrderService.CheckoutCompletion completion = orderService.checkout(token, couponCode);
        return ResponseEntity.ok(ApiResponse.success(completion.purchaseId()));
    }

    @DeleteMapping("/current/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> removeItemFromOrder(@RequestHeader("X-Session-Token") String token, @PathVariable UUID itemId) {
        orderService.removeItemFromOrder(token, itemId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<ActiveOrderDto>> getActiveOrder(@RequestHeader("X-Session-Token") String token) {
        ActiveOrderDto dto = orderService.getActiveOrder(token);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PatchMapping("/current/items/ga")
    public ResponseEntity<ApiResponse<Void>> updateGAQuantity(@RequestHeader("X-Session-Token") String token, @RequestParam UUID zoneId, @RequestParam int quantity) {
        orderService.updateGAQuantity(token, zoneId, quantity);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/current")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@RequestHeader("X-Session-Token") String token) {
        orderService.cancelOrder(token);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PurchaseRecordDTO>>> getPurchaseHistory(@RequestHeader("X-Session-Token") String token) {
        List<PurchaseRecordDTO> history = orderService.getPurchaseHistory(token);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
