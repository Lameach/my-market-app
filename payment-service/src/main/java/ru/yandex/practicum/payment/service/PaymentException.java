package ru.yandex.practicum.payment.service;

import org.springframework.http.HttpStatus;

public class PaymentException extends RuntimeException {

    private final HttpStatus status;

    public PaymentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
