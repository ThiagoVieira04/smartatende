package br.com.smartatende.service;

import br.com.smartatende.dto.AuthResponse;
import br.com.smartatende.dto.LoginRequest;
import br.com.smartatende.dto.RegisterRequest;
import br.com.smartatende.model.Usuario;
import br.com.smartatende.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse registrar(RegisterRequest request) {

        if (request == null
                || isBlank(request.getNome())
                || isBlank(request.getEmail())
                || isBlank(request.getSenha())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nome, e-mail e senha são obrigatórios.");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "E-mail já cadastrado.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.getNome().trim());
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(request.getSenha()));

        usuarioRepository.save(usuario);

        return resposta(usuario);
    }

    public AuthResponse login(LoginRequest request) {

        if (request == null
                || isBlank(request.getEmail())
                || isBlank(request.getSenha())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Credenciais inválidas.");
        }

        String email = request.getEmail().trim().toLowerCase();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Credenciais inválidas."));

        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Credenciais inválidas.");
        }

        return resposta(usuario);
    }

    private AuthResponse resposta(Usuario usuario) {
        return new AuthResponse(
                jwtService.gerarToken(usuario.getEmail()),
                usuario.getNome(),
                usuario.getEmail());
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}
