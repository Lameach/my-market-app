package ru.yandex.practicum.payment.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.api.PaymentsApi;
import ru.yandex.practicum.payment.model.Balance;
import ru.yandex.practicum.payment.model.PaymentRequest;
import ru.yandex.practicum.payment.model.PaymentResult;
import ru.yandex.practicum.payment.service.BalanceService;

@RestController
public class PaymentController implements PaymentsApi {

    private final BalanceService balances;

    public PaymentController(BalanceService balances) {
        this.balances = balances;
    }

    @Override
    public Mono<ResponseEntity<Balance>> getBalance(ServerWebExchange exchange) {
        return balances.current().map(amount -> ResponseEntity.ok(new Balance(amount)));
    }

    @Override
    public Mono<ResponseEntity<PaymentResult>> pay(Mono<PaymentRequest> paymentRequest, ServerWebExchange exchange) {
        return paymentRequest.flatMap(request -> balances.pay(request.getAmount())
                .map(left -> ResponseEntity.ok(new PaymentResult(true, left))));
    }
}
