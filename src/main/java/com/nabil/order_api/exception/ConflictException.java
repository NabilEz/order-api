package com.nabil.order_api.exception;


// 409 — conflitto (es. email duplicata)
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
