package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
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
    public Mono<String> items(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "NO") String sort,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "1") int pageNumber,
            WebSession session,
            Model model) {
        session.start();
        return catalog.show(search, sort, pageSize, pageNumber, session.getId())
                .doOnNext(page -> {
                    model.addAttribute("items", page.items());
                    model.addAttribute("search", page.search());
                    model.addAttribute("sort", page.sort());
                    model.addAttribute("paging", page.paging());
                })
                .thenReturn("items");
    }

    @PostMapping("/items")
    public Mono<String> change(ServerWebExchange exchange, WebSession session) {
        session.start();
        return exchange.getFormData().flatMap(form -> {
            long id = Forms.requiredLong(form, "id");
            String search = Forms.text(form, "search", "");
            String sort = Forms.text(form, "sort", "NO");
            int pageSize = Forms.number(form, "pageSize", 5);
            int pageNumber = Forms.number(form, "pageNumber", 1);
            return cart.change(session.getId(), id, Actions.catalog(Forms.required(form, "action")))
                    .thenReturn("redirect:" + catalogLocation(search, sort, pageSize, pageNumber));
        });
    }

    @GetMapping("/items/{id}")
    public Mono<String> item(@PathVariable long id, WebSession session, Model model) {
        session.start();
        return catalog.item(id, session.getId())
                .doOnNext(found -> model.addAttribute("item", found))
                .thenReturn("item");
    }

    @PostMapping("/items/{id}")
    public Mono<String> changeOne(
            @PathVariable long id,
            ServerWebExchange exchange,
            WebSession session,
            Model model) {
        session.start();
        String sessionId = session.getId();
        return exchange.getFormData().flatMap(form -> cart
                .change(sessionId, id, Actions.catalog(Forms.required(form, "action")))
                .then(catalog.item(id, sessionId))
                .doOnNext(found -> model.addAttribute("item", found))
                .thenReturn("item"));
    }

    private static String catalogLocation(String search, String sort, int pageSize, int pageNumber) {
        return UriComponentsBuilder.fromPath("/items")
                .queryParam("search", search)
                .queryParam("sort", sort)
                .queryParam("pageSize", pageSize)
                .queryParam("pageNumber", pageNumber)
                .build()
                .encode()
                .toUriString();
    }
}
