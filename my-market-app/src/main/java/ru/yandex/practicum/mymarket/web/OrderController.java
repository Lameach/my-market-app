package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.OrderService;

@Controller
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public Mono<String> orders(WebSession session, Model model) {
        session.start();
        return orders.list(session.getId())
                .doOnNext(list -> model.addAttribute("orders", list))
                .thenReturn("orders");
    }

    @GetMapping("/orders/{id}")
    public Mono<String> order(
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean newOrder,
            WebSession session,
            Model model) {
        session.start();
        model.addAttribute("newOrder", newOrder);
        return orders.get(id, session.getId())
                .doOnNext(order -> model.addAttribute("order", order))
                .thenReturn("order");
    }

    @PostMapping("/buy")
    public Mono<String> buy(WebSession session) {
        session.start();
        return orders.buy(session.getId())
                .map(id -> "redirect:/orders/" + id + "?newOrder=true")
                .defaultIfEmpty("redirect:/cart/items");
    }
}
