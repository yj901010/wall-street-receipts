package com.wallstreetreceipts.api.web.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

final class OperatorBearerAuthenticationToken extends AbstractAuthenticationToken {

    private static final String PRINCIPAL = "local-operator";

    private String bearerToken;

    private OperatorBearerAuthenticationToken(
            String bearerToken,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.bearerToken = bearerToken;
    }

    static OperatorBearerAuthenticationToken unauthenticated(String bearerToken) {
        return new OperatorBearerAuthenticationToken(bearerToken, java.util.List.of());
    }

    static OperatorBearerAuthenticationToken authenticated(
            Collection<? extends GrantedAuthority> authorities) {
        OperatorBearerAuthenticationToken authentication =
                new OperatorBearerAuthenticationToken(null, authorities);
        authentication.setAuthenticated(true);
        return authentication;
    }

    @Override
    public Object getCredentials() {
        return bearerToken;
    }

    @Override
    public Object getPrincipal() {
        return PRINCIPAL;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        bearerToken = null;
    }
}
