package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class LoginController {

    @GetMapping("/login")
    public Mono<String> login(ServerWebExchange exchange, Model model) {
        String query = exchange.getRequest().getURI().getRawQuery();
        model.addAttribute("loginError", query != null && query.contains("error"));
        model.addAttribute("loggedOut", query != null && query.contains("logout"));
        return CurrentBuyer.context().flatMap(context -> CurrentBuyer.userId(context) == null
                ? Mono.just("login")
                : Mono.just("redirect:/"));
    }
}
