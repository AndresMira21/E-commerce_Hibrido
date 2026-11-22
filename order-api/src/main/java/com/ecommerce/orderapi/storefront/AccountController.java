package com.ecommerce.orderapi.storefront;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("storefront")
@RequestMapping("/api/auth")
class AccountController {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final HttpSessionSecurityContextRepository contexts;
    private final String unknownAccountHash;

    AccountController(JdbcClient jdbc, PasswordEncoder encoder, HttpSessionSecurityContextRepository contexts) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.contexts = contexts;
        this.unknownAccountHash = encoder.encode(UUID.randomUUID().toString());
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return withoutCache(HttpStatus.OK, token);
    }

    @GetMapping("/session")
    ResponseEntity<Session> session(Authentication authentication) {
        return withoutCache(HttpStatus.OK, new Session(authentication == null ? null : (Customer) authentication.getPrincipal()));
    }

    @PostMapping("/register")
    ResponseEntity<?> register(@Valid @RequestBody Registration input, HttpServletRequest request,
                               HttpServletResponse response) {
        if (!storefrontEmail(input.email()) || !validPassword(input.password()) || !input.password().equals(input.confirmation())) {
            return error(HttpStatus.BAD_REQUEST, "Revisa el correo y usa una contraseña coincidente de al menos 6 caracteres y hasta 72 bytes.");
        }
        var customer = new Customer(UUID.randomUUID(), input.name().strip(), normalizeEmail(input.email()));
        try {
            jdbc.sql("""
                    INSERT INTO orders.customer_accounts (id, full_name, email, password_hash)
                    VALUES (:id, :name, :email, :hash)
                    """)
                    .param("id", customer.id()).param("name", customer.name()).param("email", customer.email())
                    .param("hash", encoder.encode(input.password())).update();
        } catch (DuplicateKeyException duplicate) {
            return error(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }
        return withoutCache(HttpStatus.CREATED, signIn(customer, request, response));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        if (!validPassword(input.password())) {
            return error(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
        }

        var account = jdbc.sql("""
                SELECT id, full_name, email, password_hash
                FROM orders.customer_accounts WHERE email = :email
                """)
                .param("email", normalizeEmail(input.email()))
                .query((row, index) -> new Account(new Customer(row.getObject("id", UUID.class),
                        row.getString("full_name"), row.getString("email")), row.getString("password_hash")))
                .optional();
        var matches = encoder.matches(input.password(), account.map(Account::hash).orElse(unknownAccountHash));
        if (!matches || account.isEmpty()) {
            return error(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
        }
        return withoutCache(HttpStatus.OK, signIn(account.get().customer(), request, response));
    }

    private Session signIn(Customer customer, HttpServletRequest request, HttpServletResponse response) {
        var previous = request.getSession(false);
        if (previous != null) previous.invalidate();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                customer, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return new Session(customer);
    }

    private static boolean validPassword(String password) {
        return password.codePointCount(0, password.length()) >= 6
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.indexOf(0) < 0;
    }

    private static boolean storefrontEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseEntity<?> error(HttpStatus status, String message) {
        return withoutCache(status, Map.of("error", message));
    }

    private static <T> ResponseEntity<T> withoutCache(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    record Registration(@NotBlank @Size(max = 100) String name,
                        @NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 72) String password,
                        @NotBlank @Size(max = 72) String confirmation) {}
    record Login(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}
    record Session(Customer user) {}
    record Customer(UUID id, String name, String email) implements java.io.Serializable {}
    private record Account(Customer customer, String hash) {}
}
