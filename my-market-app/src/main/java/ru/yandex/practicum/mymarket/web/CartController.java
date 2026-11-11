package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.InsufficientFundsException;
import ru.yandex.practicum.mymarket.service.PaymentAuthorizationException;
import ru.yandex.practicum.mymarket.service.PaymentGateway;

@Controller
public class CartController {

    static final String INSUFFICIENT = "Недостаточно средств для оплаты заказа";
    static final String UNAVAILABLE = "Сервис платежей недоступен";
    static final String UNAUTHORIZED = "Сервис платежей отклонил авторизацию клиента";

    private final CartService cart;
    private final PaymentGateway payments;

    public CartController(CartService cart, PaymentGateway payments) {
        this.cart = cart;
        this.payments = payments;
    }

    @GetMapping("/cart/items")
    public Mono<String> cart(Model model) {
        return CurrentBuyer.context().flatMap(context -> {
            Long userId = CurrentBuyer.userId(context);
            return cart.lines(userId).flatMap(items -> {
            long total = items.stream().mapToLong(ItemDto::sum).sum();
            model.addAttribute("items", items);
            model.addAttribute("total", total);
            model.addAttribute("canBuy", false);
            model.addAttribute("paymentMessage", null);
            if (items.isEmpty()) {
                return Mono.just("cart");
            }
            return payments.balance(userId)
                    .map(balance -> {
                        if (balance >= total) {
                            model.addAttribute("canBuy", true);
                        } else {
                            model.addAttribute("paymentMessage", INSUFFICIENT);
                        }
                        return "cart";
                    })
                    .onErrorResume(ex -> {
                        model.addAttribute("paymentMessage", message(ex));
                        return Mono.just("cart");
                    });
            });
        });
    }

    @PostMapping("/cart/items")
    public Mono<String> change(ServerWebExchange exchange) {
        return CurrentBuyer.id().flatMap(userId -> exchange.getFormData().flatMap(form -> cart
                .change(userId, Forms.requiredLong(form, "id"), Actions.cart(Forms.required(form, "action")))
                .thenReturn("redirect:/cart/items")));
    }

    private static String message(Throwable failure) {
        if (failure instanceof PaymentAuthorizationException) {
            return UNAUTHORIZED;
        }
        if (failure instanceof InsufficientFundsException) {
            return INSUFFICIENT;
        }
        return UNAVAILABLE;
    }
}
