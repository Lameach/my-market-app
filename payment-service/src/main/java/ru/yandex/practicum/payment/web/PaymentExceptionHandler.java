package ru.yandex.practicum.payment.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import ru.yandex.practicum.payment.model.PaymentError;
import ru.yandex.practicum.payment.service.BalanceService;
import ru.yandex.practicum.payment.service.PaymentException;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<PaymentError> payment(PaymentException exception) {
        return ResponseEntity.status(exception.status()).body(new PaymentError(exception.getMessage()));
    }

    @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class})
    public ResponseEntity<PaymentError> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new PaymentError(BalanceService.NON_POSITIVE));
    }
}
