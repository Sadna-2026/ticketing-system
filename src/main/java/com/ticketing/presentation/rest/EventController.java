package com.ticketing.presentation.rest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.EditEventRequest;
import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.LotteryRegistrationRequest;
import com.ticketing.application.dto.LotteryRegistrationResponse;
import com.ticketing.application.services.EventService;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> createEvent(@RequestHeader("X-Session-Token") String token, @RequestBody CreateEventRequest request) {
        UUID eventId = eventService.createEvent(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(eventId));
    }

    @PostMapping("/{eventId}/lottery")
    public ResponseEntity<ApiResponse<LotteryRegistrationResponse>> registerForLottery(@RequestHeader("X-Session-Token") String token, @RequestBody LotteryRegistrationRequest request) {
        LotteryRegistrationResponse response = eventService.registerForLottery(token, request);
        if (response.success()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(response.message()));
        }
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> cancelEvent(@RequestHeader("X-Session-Token") String token, @PathVariable UUID eventId) {
        eventService.cancelEvent(token, eventId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventDetailsDTO>> editEvent(@RequestHeader("X-Session-Token") String token, @PathVariable UUID eventId, @RequestBody EditEventRequest request) {
        EventDetailsDTO dto = eventService.editEvent(token, request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{eventId}/map")
    public ResponseEntity<ApiResponse<EventMapDTO>> getEventMap(@PathVariable UUID eventId) {
        Optional<EventMapDTO> dto = eventService.getEventMap(eventId);
        return dto.map(val -> ResponseEntity.ok(ApiResponse.success(val))).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Event map not found")));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<EventSummaryDTO>>> searchEvents(@RequestBody SearchEventsRequest request) {
        List<EventSummaryDTO> results = eventService.searchEvents(request);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
