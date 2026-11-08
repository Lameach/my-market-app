package ru.yandex.practicum.mymarket.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.yandex.practicum.mymarket.dto.CatalogPage;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CatalogService;

@Controller
public class ItemController {

    private final CatalogService catalog;
    private final CartService cart;

    public ItemController(CatalogService catalog, CartService cart) {
        this.catalog = catalog;
        this.cart = cart;
    }

    @GetMapping({"/", "/items"})
    public String items(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "1") int pageNumber,
            HttpSession session,
            Model model) {
        CatalogPage page = catalog.show(search, sort, pageSize, pageNumber, session.getId());
        model.addAttribute("items", page.items());
        model.addAttribute("search", page.search());
        model.addAttribute("sort", page.sort());
        model.addAttribute("paging", page.paging());
        return "items";
    }

    @PostMapping("/items")
    public String change(
            @RequestParam long id,
            @RequestParam String action,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "1") int pageNumber,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        cart.change(session.getId(), id, Actions.catalog(action));
        redirectAttributes.addAttribute("search", search);
        redirectAttributes.addAttribute("sort", sort);
        redirectAttributes.addAttribute("pageSize", pageSize);
        redirectAttributes.addAttribute("pageNumber", pageNumber);
        return "redirect:/items";
    }

    @GetMapping("/items/{id}")
    public String item(@PathVariable long id, HttpSession session, Model model) {
        model.addAttribute("item", catalog.item(id, session.getId()));
        return "item";
    }

    @PostMapping("/items/{id}")
    public String changeOne(
            @PathVariable long id,
            @RequestParam String action,
            HttpSession session,
            Model model) {
        cart.change(session.getId(), id, Actions.catalog(action));
        model.addAttribute("item", catalog.item(id, session.getId()));
        return "item";
    }
}
