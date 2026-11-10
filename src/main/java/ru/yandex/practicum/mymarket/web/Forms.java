package ru.yandex.practicum.mymarket.web;

import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

final class Forms {

    private Forms() {
    }

    static String required(MultiValueMap<String, String> form, String name) {
        String value = form.getFirst(name);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    static long requiredLong(MultiValueMap<String, String> form, String name) {
        try {
            return Long.parseLong(required(form, name));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    static String text(MultiValueMap<String, String> form, String name, String fallback) {
        String value = form.getFirst(name);
        return value == null ? fallback : value;
    }

    static int number(MultiValueMap<String, String> form, String name, int fallback) {
        String value = form.getFirst(name);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
