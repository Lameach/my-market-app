package ru.yandex.practicum.mymarket.service;

public class PaymentRejectedException extends RuntimeException {

    public PaymentRejectedException(Throwable cause) {
        super(cause);
    }
}
