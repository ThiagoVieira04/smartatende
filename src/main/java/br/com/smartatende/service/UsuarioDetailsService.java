package br.com.smartatende.service;

import br.com.smartatende.model.Usuario;
import br.com.smartatende.repository.UsuarioRepository;
import br.com.smartatende.security.UsuarioDetails;
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
    public UsuarioDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        Usuario usuario = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + email));

        return new UsuarioDetails(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getRole(),
                usuario.getTokenVersion());
    }
}
