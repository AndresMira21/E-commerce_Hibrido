package com.ecommerce.hibrido.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class OperationsSecurity {
    @Bean
    BCryptPasswordEncoder operationsPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService staff(@Value("${OPERATIONS_USERNAME:}") String username,
                             @Value("${OPERATIONS_PASSWORD:}") String password,
                             BCryptPasswordEncoder encoder) {
        var usable = !username.isBlank() && password.length() >= 12
                && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
        var encoded = usable ? encoder.encode(password) : null;
        return candidate -> {
            if (encoded == null || !username.equals(candidate)) {
                throw new UsernameNotFoundException("Operations account unavailable");
            }
            return User.withUsername(username).password(encoded).roles("OPERATIONS").build();
        };
    }

    @Bean
    SecurityFilterChain operations(HttpSecurity http) throws Exception {
        return http.httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/orders/status", "/analytics", "/ws/**")
                        .hasRole("OPERATIONS")
                        .requestMatchers(HttpMethod.POST, "/ws/**").hasRole("OPERATIONS")
                        .anyRequest().denyAll()).build();
    }
}
