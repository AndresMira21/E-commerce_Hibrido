package com.ecommerce.hibrido.dashboard.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationsSecurityTest {
    @Test
    void allowsRepeatedAuthenticationAfterCredentialsAreErased() {
        var security = new OperationsSecurity();
        var encoder = security.operationsPasswordEncoder();
        var staff = security.staff("operador", "clave-local-segura", encoder);
        var first = staff.loadUserByUsername("operador");
        ((CredentialsContainer) first).eraseCredentials();
        assertThat(encoder.matches("clave-local-segura", staff.loadUserByUsername("operador").getPassword())).isTrue();
        assertThatThrownBy(() -> staff.loadUserByUsername("cliente")).isInstanceOf(UsernameNotFoundException.class);
    }
}
