package br.com.smartatende.service;

import br.com.smartatende.model.Usuario;
import br.com.smartatende.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService
        implements UserDetailsService {

    private final UsuarioRepository repository;

    public UsuarioDetailsService(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        Usuario usuario = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + email));

        String role = usuario.getRole() == null
                ? "USER"
                : usuario.getRole().replaceFirst("^ROLE_", "");

        return User.withUsername(usuario.getEmail())
                .password(usuario.getSenha() == null ? "" : usuario.getSenha())
                .roles(role)
                .build();
    }
}
