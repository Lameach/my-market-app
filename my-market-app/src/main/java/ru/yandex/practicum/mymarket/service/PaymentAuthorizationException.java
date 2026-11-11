package ru.yandex.practicum.mymarket.service;

public class PaymentAuthorizationException extends PaymentRejectedException {

    public PaymentAuthorizationException(Throwable cause) {
        super(cause);
    }
}
