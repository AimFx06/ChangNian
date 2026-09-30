package com.changnian.controller;

import com.changnian.common.ApiResponse;
import com.changnian.domain.entity.MedicationRecordEntity;
import com.changnian.service.MedicationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {
    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @PostMapping("/{scheduleId}/taken")
    public ApiResponse<MedicationRecordEntity> taken(@PathVariable Long scheduleId) {
        return ApiResponse.ok(medicationService.record(scheduleId, "TAKEN"));
    }

    @PostMapping("/{scheduleId}/later")
    public ApiResponse<MedicationRecordEntity> later(@PathVariable Long scheduleId) {
        return ApiResponse.ok(medicationService.record(scheduleId, "LATER"));
    }
}
