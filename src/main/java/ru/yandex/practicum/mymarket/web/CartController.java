package ru.yandex.practicum.mymarket.web;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.CartService;

@Controller
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping("/cart/items")
    public String cart(HttpSession session, Model model) {
        show(session.getId(), model);
        return "cart";
    }

    @PostMapping("/cart/items")
    public String change(
            @RequestParam long id,
            @RequestParam String action,
            HttpSession session,
            Model model) {
        cart.change(session.getId(), id, Actions.cart(action));
        show(session.getId(), model);
        return "cart";
    }

    private void show(String sessionId, Model model) {
        List<ItemDto> items = cart.lines(sessionId);
        model.addAttribute("items", items == null ? List.of() : items);
        model.addAttribute("total", cart.total(sessionId));
    }
}
