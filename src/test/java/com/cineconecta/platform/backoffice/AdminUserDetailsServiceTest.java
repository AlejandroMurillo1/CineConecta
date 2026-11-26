package com.cineconecta.platform.backoffice;

import com.cineconecta.platform.backoffice.internal.security.AdminAccount;
import com.cineconecta.platform.backoffice.internal.security.AdminPrincipal;
import com.cineconecta.platform.backoffice.internal.security.AdminUserDetailsService;
import com.cineconecta.platform.backoffice.internal.security.AdminUserStore;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserDetailsServiceTest {

    private final AdminUserStore store = mock(AdminUserStore.class);
    private final AdminUserDetailsService service = new AdminUserDetailsService(store);

    @Test
    void normalizesEmailAndGrantsAdminRole() {
        when(store.findByEmail("admin@cineconecta.local")).thenReturn(Optional.of(
                new AdminAccount("6740a1f0c3b2a1d4e5f60001", "Ana Admin", "admin@cineconecta.local", "$2a$10$hash")));

        UserDetails user = service.loadUserByUsername("  Admin@CineConecta.LOCAL ");

        assertEquals("admin@cineconecta.local", user.getUsername());
        assertEquals("$2a$10$hash", user.getPassword());
        assertEquals("ROLE_ADMIN", user.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElseThrow());
        AdminPrincipal principal = assertInstanceOf(AdminPrincipal.class, user);
        assertEquals("Ana Admin", principal.getDisplayName());
        assertEquals("6740a1f0c3b2a1d4e5f60001", principal.getAdminId());
    }

    @Test
    void unknownEmailIsRejected() {
        when(store.findByEmail("nadie@cineconecta.local")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nadie@cineconecta.local"));
    }
}
