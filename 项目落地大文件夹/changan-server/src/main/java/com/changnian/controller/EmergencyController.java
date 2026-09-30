package com.changnian.controller;

import com.changnian.common.ApiResponse;
import com.changnian.domain.entity.EmergencyEventEntity;
import com.changnian.service.EmergencyService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emergencies")
public class EmergencyController {
    private final EmergencyService emergencyService;

    public EmergencyController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    @PostMapping
    public ApiResponse<EmergencyEventEntity> create(@RequestBody EmergencyRequest request) {
        return ApiResponse.ok(emergencyService.create(request.locationText(), request.note()));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<EmergencyEventEntity> confirm(@PathVariable Long id) {
        return ApiResponse.ok(emergencyService.confirm(id));
    }

    public record EmergencyRequest(String locationText, String note) {
    }
}
