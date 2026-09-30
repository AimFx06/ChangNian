package com.changnian.controller;

import com.changnian.common.ApiResponse;
import com.changnian.service.AiInsightService;
import com.changnian.service.AiService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;
    private final AiInsightService aiInsightService;

    public AiController(AiService aiService, AiInsightService aiInsightService) {
        this.aiService = aiService;
        this.aiInsightService = aiInsightService;
    }

    @PostMapping("/weekly-report")
    public ApiResponse<AiService.AiTextResult> weeklyReport(@RequestBody AiRequest request) {
        return ApiResponse.ok(aiService.generateWeeklyReport(request.signals()));
    }

    @PostMapping("/topics")
    public ApiResponse<AiService.AiTextResult> topics(@RequestBody AiRequest request) {
        return ApiResponse.ok(aiService.generateTopics(request.signals()));
    }

    @PostMapping("/topics/refresh")
    public ApiResponse<AiInsightService.InsightRefreshResult> refreshTopics(@RequestBody AiRequest request) {
        return ApiResponse.ok(aiInsightService.refreshCurrentElder(request.elderUsername()));
    }

    @PostMapping("/insights/refresh")
    public ApiResponse<AiInsightService.InsightRefreshResult> refreshInsights(@RequestBody AiRequest request) {
        return ApiResponse.ok(aiInsightService.refreshCurrentElder(request.elderUsername()));
    }

    public record AiRequest(List<String> signals, String elderUsername) {
    }
}
