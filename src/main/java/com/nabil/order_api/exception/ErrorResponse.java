package com.nabil.order_api.exception;


import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        List<String> details   // per errori di validazione multipli
) {
    // costruttore senza details per errori semplici
    public static ErrorResponse of(int status, String error,
                                   String message, String path) {
        return new ErrorResponse(status, error, message, path,
                LocalDateTime.now(), List.of());
    }

    public static ErrorResponse of(int status, String error,
                                   String message, String path,
                                   List<String> details) {
        return new ErrorResponse(status, error, message, path,
                LocalDateTime.now(), details);
    }
}