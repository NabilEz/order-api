package com.nabil.order_api.exception;


// 404 — risorsa non trovata
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " non trovato con id: " + id);
    }
}
