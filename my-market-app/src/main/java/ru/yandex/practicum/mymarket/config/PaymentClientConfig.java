package ru.yandex.practicum.mymarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.api.PaymentsApi;
import ru.yandex.practicum.mymarket.payment.invoker.ApiClient;
import ru.yandex.practicum.mymarket.payment.model.Balance;
import ru.yandex.practicum.mymarket.payment.model.PaymentRequest;
import ru.yandex.practicum.mymarket.service.PaymentGateway;
import ru.yandex.practicum.mymarket.service.PaymentRejectedException;

@Configuration
public class PaymentClientConfig {

    @Bean
    public PaymentsApi paymentsApi(@Value("${payment.base-url}") String baseUrl) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        return new PaymentsApi(client);
    }

    @Bean
    public PaymentGateway paymentGateway(PaymentsApi paymentsApi) {
        return new OpenApiPaymentGateway(paymentsApi);
    }

    static class OpenApiPaymentGateway implements PaymentGateway {

        private final PaymentsApi api;

        OpenApiPaymentGateway(PaymentsApi api) {
            this.api = api;
        }

        @Override
        public Mono<Long> balance() {
            return api.getBalance()
                    .map(Balance::getAmount)
                    .switchIfEmpty(Mono.error(new PaymentRejectedException(new IllegalStateException("empty balance"))))
                    .onErrorMap(OpenApiPaymentGateway::reject);
        }

        @Override
        public Mono<Void> pay(long amount) {
            return api.pay(new PaymentRequest().amount(amount))
                    .switchIfEmpty(Mono.error(new PaymentRejectedException(new IllegalStateException("empty payment"))))
                    .then()
                    .onErrorMap(OpenApiPaymentGateway::reject);
        }

        private static Throwable reject(Throwable failure) {
            if (failure instanceof PaymentRejectedException) {
                return failure;
            }
            if (failure instanceof WebClientResponseException response
                    && response.getStatusCode().value() == HttpStatus.PAYMENT_REQUIRED.value()) {
                return new PaymentRejectedException(response);
            }
            return new PaymentRejectedException(failure);
        }
    }
}
