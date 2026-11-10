package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.PaymentGateway;
import ru.yandex.practicum.mymarket.service.PaymentRejectedException;

@Controller
public class CartController {

    static final String INSUFFICIENT = "Недостаточно средств для оплаты заказа";
    static final String UNAVAILABLE = "Сервис платежей недоступен";

    private final CartService cart;
    private final PaymentGateway payments;

    public CartController(CartService cart, PaymentGateway payments) {
        this.cart = cart;
        this.payments = payments;
    }

    @GetMapping("/cart/items")
    public Mono<String> cart(WebSession session, Model model) {
        session.start();
        return cart.lines(session.getId()).flatMap(items -> {
            long total = items.stream().mapToLong(ItemDto::sum).sum();
            model.addAttribute("items", items);
            model.addAttribute("total", total);
            model.addAttribute("canBuy", false);
            model.addAttribute("paymentMessage", null);
            if (items.isEmpty()) {
                return Mono.just("cart");
            }
            return payments.balance()
                    .map(balance -> {
                        if (balance >= total) {
                            model.addAttribute("canBuy", true);
                        } else {
                            model.addAttribute("paymentMessage", INSUFFICIENT);
                        }
                        return "cart";
                    })
                    .onErrorResume(PaymentRejectedException.class, ex -> {
                        model.addAttribute("paymentMessage", UNAVAILABLE);
                        return Mono.just("cart");
                    });
        });
    }

    @PostMapping("/cart/items")
    public Mono<String> change(ServerWebExchange exchange, WebSession session) {
        session.start();
        return exchange.getFormData().flatMap(form -> cart
                .change(session.getId(), Forms.requiredLong(form, "id"), Actions.cart(Forms.required(form, "action")))
                .thenReturn("redirect:/cart/items"));
    }
}
