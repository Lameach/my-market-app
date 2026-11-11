package ru.yandex.practicum.mymarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.payment.api.PaymentsApi;
import ru.yandex.practicum.mymarket.payment.invoker.ApiClient;
import ru.yandex.practicum.mymarket.payment.model.Balance;
import ru.yandex.practicum.mymarket.payment.model.PaymentRequest;
import ru.yandex.practicum.mymarket.service.InsufficientFundsException;
import ru.yandex.practicum.mymarket.service.PaymentAuthorizationException;
import ru.yandex.practicum.mymarket.service.PaymentGateway;
import ru.yandex.practicum.mymarket.service.PaymentRejectedException;

@Configuration
public class PaymentClientConfig {

    @Bean
    public PaymentsApi paymentsApi(
            @Value("${payment.base-url}") String baseUrl,
            ReactiveClientRegistrationRepository clients,
            ReactiveOAuth2AuthorizedClientService authorizedClients) {
        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clients, authorizedClients);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        oauth.setDefaultClientRegistrationId("payment");
        WebClient webClient = ApiClient.buildWebClientBuilder().filter(oauth).build();
        ApiClient client = new ApiClient(webClient);
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
        public Mono<Long> balance(long userId) {
            return api.getBalance(userId)
                    .map(Balance::getAmount)
                    .switchIfEmpty(Mono.error(new PaymentRejectedException(new IllegalStateException("empty balance"))))
                    .onErrorMap(OpenApiPaymentGateway::reject);
        }

        @Override
        public Mono<Void> pay(long userId, long amount) {
            return api.pay(new PaymentRequest().amount(amount).userId(userId))
                    .switchIfEmpty(Mono.error(new PaymentRejectedException(new IllegalStateException("empty payment"))))
                    .then()
                    .onErrorMap(OpenApiPaymentGateway::reject);
        }

        private static Throwable reject(Throwable failure) {
            if (failure instanceof PaymentRejectedException) {
                return failure;
            }
            if (failure instanceof WebClientResponseException response) {
                int status = response.getStatusCode().value();
                if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                    return new PaymentAuthorizationException(response);
                }
                if (status == HttpStatus.PAYMENT_REQUIRED.value()) {
                    return new InsufficientFundsException(response);
                }
            }
            return new PaymentRejectedException(failure);
        }
    }
}
