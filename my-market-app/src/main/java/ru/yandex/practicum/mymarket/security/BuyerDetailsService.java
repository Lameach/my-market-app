package ru.yandex.practicum.mymarket.security;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.repository.UserRepository;

@Service
public class BuyerDetailsService implements ReactiveUserDetailsService {

    private final UserRepository users;

    public BuyerDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return users.findByUsername(username)
                .map(user -> new Buyer(user.getId(), user.getUsername(), user.getPassword(), user.isEnabled()));
    }
}
