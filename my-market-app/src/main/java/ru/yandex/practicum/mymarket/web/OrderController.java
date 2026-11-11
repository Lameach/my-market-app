package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PaymentRejectedException;

@Controller
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public Mono<String> orders(Model model) {
        return CurrentBuyer.context().flatMap(context -> orders.list(CurrentBuyer.userId(context))
                .doOnNext(list -> model.addAttribute("orders", list))
                .thenReturn("orders"));
    }

    @GetMapping("/orders/{id}")
    public Mono<String> order(
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean newOrder,
            Model model) {
        model.addAttribute("newOrder", newOrder);
        return CurrentBuyer.context().flatMap(context -> orders.get(id, CurrentBuyer.userId(context))
                .doOnNext(order -> model.addAttribute("order", order))
                .thenReturn("order"));
    }

    @PostMapping("/buy")
    public Mono<String> buy() {
        return CurrentBuyer.id().flatMap(userId -> orders.buy(userId)
                .map(id -> "redirect:/orders/" + id + "?newOrder=true")
                .onErrorResume(OrderController::rejected, ex -> Mono.just("redirect:/cart/items"))
                .defaultIfEmpty("redirect:/cart/items"));
    }

    private static boolean rejected(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof PaymentRejectedException) {
                return true;
            }
        }
        return false;
    }
}
