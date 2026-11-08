package ru.yandex.practicum.mymarket.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.mymarket.service.OrderService;

@Controller
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public String orders(HttpSession session, Model model) {
        model.addAttribute("orders", orders.list(session.getId()));
        return "orders";
    }

    @GetMapping("/orders/{id}")
    public String order(
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean newOrder,
            HttpSession session,
            Model model) {
        model.addAttribute("order", orders.get(id, session.getId()));
        model.addAttribute("newOrder", newOrder);
        return "order";
    }

    @PostMapping("/buy")
    public String buy(HttpSession session) {
        return orders.buy(session.getId())
                .map(id -> "redirect:/orders/" + id + "?newOrder=true")
                .orElse("redirect:/cart/items");
    }
}
