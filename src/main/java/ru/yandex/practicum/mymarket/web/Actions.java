package ru.yandex.practicum.mymarket.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.yandex.practicum.mymarket.model.CartAction;

final class Actions {

    private Actions() {
    }

    static CartAction catalog(String raw) {
        CartAction action = parse(raw);
        if (action == CartAction.DELETE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return action;
    }

    static CartAction cart(String raw) {
        return parse(raw);
    }

    private static CartAction parse(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            return CartAction.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
