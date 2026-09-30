package com.changnian.controller;

import com.changnian.common.ApiResponse;
import com.changnian.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/child")
    public ApiResponse<HomeService.ChildHomeView> childHome() {
        return ApiResponse.ok(homeService.childHome());
    }

    @GetMapping("/elder")
    public ApiResponse<HomeService.ElderHomeView> elderHome() {
        return ApiResponse.ok(homeService.elderHome());
    }
}
