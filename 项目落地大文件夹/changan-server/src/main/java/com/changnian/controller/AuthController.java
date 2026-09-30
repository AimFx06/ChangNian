package com.changnian.controller;

import com.changnian.common.ApiResponse;
import com.changnian.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String DEMO_INVITE_CODE = "VIVO-01";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthService.AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(new AuthService.RegisterCommand(
                request.username(),
                request.password(),
                request.displayName(),
                request.role(),
                request.phone(),
                request.gender(),
                request.familyCode()
        )));
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.AuthResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @GetMapping("/demo")
    public ApiResponse<DemoAccountView> demoAccounts() {
        return ApiResponse.ok(new DemoAccountView("child", "mother", "father", "16888", DEMO_INVITE_CODE));
    }

    @PutMapping("/me")
    public ApiResponse<AuthService.AuthResult> updateProfile(@Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok(authService.updateProfile(request.displayName(), request.phone(), request.gender()));
    }

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String displayName,
            @NotBlank String role,
            String phone,
            String gender,
            String familyCode
    ) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ProfileRequest(@NotBlank String displayName, String phone, String gender) {
    }

    public record DemoAccountView(String childUsername, String motherUsername, String fatherUsername, String password, String familyCode) {
    }
}
