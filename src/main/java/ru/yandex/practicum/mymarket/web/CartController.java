package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.CartService;

@Controller
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping("/cart/items")
    public Mono<String> cart(WebSession session, Model model) {
        session.start();
        String sessionId = session.getId();
        return Mono.zip(cart.lines(sessionId), cart.total(sessionId))
                .doOnNext(tuple -> {
                    model.addAttribute("items", tuple.getT1());
                    model.addAttribute("total", tuple.getT2());
                })
                .thenReturn("cart");
    }

    @PostMapping("/cart/items")
    public Mono<String> change(ServerWebExchange exchange, WebSession session) {
        session.start();
        return exchange.getFormData().flatMap(form -> cart
                .change(session.getId(), Forms.requiredLong(form, "id"), Actions.cart(Forms.required(form, "action")))
                .thenReturn("redirect:/cart/items"));
    }
}
