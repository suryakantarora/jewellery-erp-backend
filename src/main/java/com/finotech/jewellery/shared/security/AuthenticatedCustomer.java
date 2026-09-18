package com.finotech.jewellery.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A customer signed in to the customer app with their phone number.
 *
 * <p>A separate principal type, not a staff user with no permissions: every
 * staff service reads {@link AuthenticatedUser} from the context, so a customer
 * token is simply not a user there, and {@code SecurityConfig} refuses it
 * outright anywhere except the storefront endpoints.
 */
public record AuthenticatedCustomer(UUID customerId, UUID companyId, String phone,
                                    int tokenVersion) {

    public static final String AUTHORITY = "ROLE_CUSTOMER";

    public Collection<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(AUTHORITY));
    }
}
