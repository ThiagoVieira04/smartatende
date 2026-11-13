package br.com.smartatende.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String senha;
    private final String role;
    private final long tokenVersion;

    public UsuarioDetails(
            Long id,
            String email,
            String senha,
            String role,
            long tokenVersion) {
        this.id = id;
        this.email = email;
        this.senha = senha;
        this.role = role;
        this.tokenVersion = tokenVersion;
    }

    public Long getId() {
        return id;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = role == null || role.isBlank()
                ? "ROLE_USER"
                : (role.startsWith("ROLE_") ? role : "ROLE_" + role);

        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return senha == null ? "" : senha;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
