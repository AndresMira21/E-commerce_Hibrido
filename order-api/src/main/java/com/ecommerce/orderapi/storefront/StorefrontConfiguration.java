package com.ecommerce.orderapi.storefront;

import jakarta.servlet.DispatcherType;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StorefrontConfiguration {
    @Bean
    @Profile("!storefront")
    SecurityFilterChain legacyOrderContract(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(rules -> rules.anyRequest().permitAll()).build();
    }

    @Configuration
    @Profile("storefront")
    static class LocalStorefront implements WebMvcConfigurer {
        private final String resourceRoot;

        LocalStorefront(@Value("${storefront.frontend-root}") String frontend) {
            var root = Path.of(frontend).toAbsolutePath().normalize().toUri().toString();
            resourceRoot = root.endsWith("/") ? root : root + "/";
        }

        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            registry.addViewController("/").setViewName("forward:/index.html");
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            registry.addResourceHandler("/index.html", "/app.js", "/styles.css").addResourceLocations(resourceRoot);
            registry.addResourceHandler("/assets/**").addResourceLocations(resourceRoot + "assets/");
        }

        @Bean
        PasswordEncoder storefrontEncoder() {
            return new BCryptPasswordEncoder(12);
        }

        @Bean
        HttpSessionSecurityContextRepository storefrontContext() {
            return new HttpSessionSecurityContextRepository();
        }

        @Bean
        SecurityFilterChain storefrontSecurity(HttpSecurity http, HttpSessionSecurityContextRepository context, PurchaseSocket purchases)
                throws Exception {
            return http.securityContext(config -> config.securityContextRepository(context))
                    .requestCache(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(rules -> rules
                            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js", "/styles.css",
                                    "/assets/portada-azul.jpg", "/api/auth/csrf", "/api/auth/session",
                                    "/assets/fondo/Fondo tecnológico azul eléctrico minimalista.png",
                                    "/assets/products/nexo.jpg", "/assets/products/trazo.jpg", "/assets/products/foco.jpg",
                                    "/assets/products/puente.jpg", "/assets/products/onda.jpg", "/assets/products/marco24.jpg",
                                    "/api/catalog/featured").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/catalog/quote").authenticated()
                            .requestMatchers(HttpMethod.POST, "/api/me/purchases").hasRole("CUSTOMER")
                            .requestMatchers(HttpMethod.GET, "/api/me/notifications").hasRole("CUSTOMER")
                            .requestMatchers(HttpMethod.POST, "/api/me/notifications/read-all", "/api/me/notifications/*/read",
                                    "/api/me/notifications/delete-all", "/api/me/notifications/*/delete").hasRole("CUSTOMER")
                            .requestMatchers(HttpMethod.GET, "/api/me/purchases", "/api/me/purchases/*", "/ws/purchases").hasRole("CUSTOMER")
                            .requestMatchers(HttpMethod.GET, "/api/catalog/products", "/assets/products/*.jpg").authenticated()
                            .anyRequest().denyAll())
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint((request, response, failure) ->
                                    jsonError(response, 401, "Inicia sesión para continuar."))
                            .accessDeniedHandler((request, response, failure) ->
                                    jsonError(response, 403, "Acceso denegado o solicitud caducada.")))
                    .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("ECOMMERCE_SESSION")
                            .addLogoutHandler((request, response, authentication) -> purchases.closeSession(request.getSession(false)))
                            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                    .build();
        }

        private void jsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
                throws IOException {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"" + message + "\"}");
        }
    }
}
