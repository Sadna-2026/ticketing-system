package com.ticketing.presentation.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.application.services.NotificationQueryService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> getPendingNotifications(@RequestHeader("X-Member-Id") String memberId) {
        List<String> notifications = notificationQueryService.getPendingNotifications(memberId);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearPendingNotifications(@RequestHeader("X-Member-Id") String memberId) {
        notificationQueryService.clearPendingNotifications(memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
