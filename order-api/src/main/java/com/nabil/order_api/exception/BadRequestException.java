package com.nabil.order_api.exception;


// 400 — richiesta non valida (logica di business)
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
