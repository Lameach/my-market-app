package ru.yandex.practicum.mymarket.service;

public class InsufficientFundsException extends PaymentRejectedException {

    public InsufficientFundsException(Throwable cause) {
        super(cause);
    }
}
