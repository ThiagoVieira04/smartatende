package br.com.smartatende.service;

import br.com.smartatende.dto.AuthResponse;
import br.com.smartatende.dto.LoginRequest;
import br.com.smartatende.dto.RegisterRequest;
import br.com.smartatende.model.Usuario;
import br.com.smartatende.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMetrics authMetrics;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthMetrics authMetrics) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authMetrics = authMetrics;
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
        authMetrics.recordRegisterSuccess();

        return resposta(usuario);
    }

    public AuthResponse login(LoginRequest request) {

        if (request == null
                || isBlank(request.getEmail())
                || isBlank(request.getSenha())) {
            authMetrics.recordLoginFailure();
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Credenciais inválidas.");
        }

        String email = request.getEmail().trim().toLowerCase();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> {
                    authMetrics.recordLoginFailure();
                    return new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Credenciais inválidas.");
                });

        if (!passwordEncoder.matches(request.getSenha(), usuario.getSenha())) {
            authMetrics.recordLoginFailure();
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Credenciais inválidas.");
        }

        authMetrics.recordLoginSuccess();
        return resposta(usuario);
    }

    public AuthResponse refresh(String refreshToken) {

        if (isBlank(refreshToken)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token inválido ou expirado.");
        }

        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Refresh token inválido ou expirado.");
            }

            String email = jwtService.extrairEmail(refreshToken);
            long tokenVersion = jwtService.extrairTokenVersion(refreshToken);

            Usuario usuario = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Refresh token inválido ou expirado."));

            if (tokenVersion != usuario.getTokenVersion()) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Refresh token inválido ou expirado.");
            }

            return resposta(usuario);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token inválido ou expirado.",
                    exception);
        }
    }

    public void logout(String email) {

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuário não autenticado."));

        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        usuarioRepository.save(usuario);
    }

    private AuthResponse resposta(Usuario usuario) {
        return new AuthResponse(
                jwtService.gerarToken(usuario.getEmail(), usuario.getTokenVersion()),
                jwtService.gerarRefreshToken(usuario.getEmail(), usuario.getTokenVersion()),
                usuario.getNome(),
                usuario.getEmail());
    }

    private boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}
