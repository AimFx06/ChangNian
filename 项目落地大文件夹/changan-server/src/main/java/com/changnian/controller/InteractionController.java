package com.changnian.controller;

import com.changnian.common.ApiResponse;
import com.changnian.service.HomeService;
import com.changnian.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InteractionController {
    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/topics/{topicId}/microphone-requests")
    public ApiResponse<HomeService.PendingRequestView> createMicrophoneRequest(
            @PathVariable Long topicId,
            @RequestBody(required = false) CreateMicrophoneRequest request
    ) {
        String elderUsername = request == null ? null : request.elderUsername();
        return ApiResponse.ok(interactionService.createMicrophoneRequest(topicId, elderUsername));
    }

    @PostMapping("/elder/messages/{requestId}/responses")
    public ApiResponse<HomeService.ResponseView> respond(@PathVariable Long requestId, @Valid @RequestBody ResponseRequest request) {
        return ApiResponse.ok(interactionService.respond(requestId, request.content(), request.audioUrl()));
    }

    @PostMapping("/elder/messages/responses")
    public ApiResponse<HomeService.ResponseView> directRespond(@Valid @RequestBody ResponseRequest request) {
        return ApiResponse.ok(interactionService.createDirectResponse(request.content(), request.audioUrl()));
    }

    public record ResponseRequest(String content, String audioUrl) {
    }

    public record CreateMicrophoneRequest(String elderUsername) {
    }
}
