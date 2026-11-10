package ru.yandex.practicum.mymarket.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ValidateBeforeConvert implements BeforeConvertCallback<Object> {

    private final Validator validator;

    public ValidateBeforeConvert(Validator validator) {
        this.validator = validator;
    }

    @Override
    public Mono<Object> onBeforeConvert(Object entity, SqlIdentifier table) {
        Set<ConstraintViolation<Object>> violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            return Mono.error(new ConstraintViolationException(violations));
        }
        return Mono.just(entity);
    }
}
