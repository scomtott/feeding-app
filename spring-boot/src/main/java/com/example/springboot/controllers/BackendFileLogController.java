package com.example.springboot.controllers;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.springboot.models.BackendFileLogResponse;
import com.example.springboot.models.BackendFileLogStorageResponse;
import com.example.springboot.services.BackendFileLogQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/backend-file-logs")
@RequiredArgsConstructor
public class BackendFileLogController {

    private final BackendFileLogQueryService backendFileLogQueryService;

    @GetMapping("/window")
    public BackendFileLogResponse getLogsByWindow(
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String sourceClass,
        @RequestParam(required = false) String logLevel,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer limit
    ) {
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        if (limit != null && limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'limit' must be greater than 0");
        }
        return backendFileLogQueryService.getLogsByTimeWindow(
            fromInstant,
            toInstant,
            sourceClass,
            logLevel,
            category,
            limit
        );
    }

    @GetMapping("/latest")
    public BackendFileLogResponse getLatestLogs(
        @RequestParam(defaultValue = "200") int count,
        @RequestParam(required = false) String sourceClass,
        @RequestParam(required = false) String logLevel,
        @RequestParam(required = false) String category
    ) {
        if (count <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'count' must be greater than 0");
        }
        return backendFileLogQueryService.getLastLogs(count, sourceClass, logLevel, category);
    }

    @GetMapping("/storage")
    public BackendFileLogStorageResponse getStorageUsage() {
        return backendFileLogQueryService.getStorageUsage();
    }

    private static Instant parseInstant(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid '" + parameterName + "' timestamp. Use ISO-8601 format."
            );
        }
    }
}
