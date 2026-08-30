package com.example.orderservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    // Static list that grows forever → memory leak
    private static final List<byte[]> LEAK = new ArrayList<>();

    @GetMapping("/leak")
    public String leak(@RequestParam(defaultValue = "10") int mb) {
        // Allocate MB megabytes and never release them
        LEAK.add(new byte[mb * 1024 * 1024]);
        return "Leaked " + mb + " MB. Total leaked objects: " + LEAK.size();
    }
}