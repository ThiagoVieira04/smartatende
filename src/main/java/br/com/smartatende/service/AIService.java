package br.com.smartatende.service;

import br.com.smartatende.model.Conversa;
import br.com.smartatende.model.Usuario;
import br.com.smartatende.repository.ConversaRepository;
import br.com.smartatende.repository.UsuarioRepository;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final ChatClient chatClient;
    private final ConversaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final AiCircuitBreaker circuitBreaker;
    private final AiMetrics aiMetrics;
    private final int historyLimit;
    private final int maxRetries;
    private final Duration backoffInitial;
    private final double backoffMultiplier;
    private final Duration backoffMax;

    public AIService(
            ChatClient chatClient,
            ConversaRepository repository,
            UsuarioRepository usuarioRepository,
            AiCircuitBreaker circuitBreaker,
            AiMetrics aiMetrics,
            @Value("${app.ai.history-limit:20}") int historyLimit,
            @Value("${app.ai.retry.max-retries:2}") int maxRetries,
            @Value("${app.ai.retry.backoff-initial:500ms}") Duration backoffInitial,
            @Value("${app.ai.retry.backoff-multiplier:2}") double backoffMultiplier,
            @Value("${app.ai.retry.backoff-max:2s}") Duration backoffMax) {
        this.chatClient = chatClient;
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.circuitBreaker = circuitBreaker;
        this.aiMetrics = aiMetrics;
        this.historyLimit = Math.max(1, historyLimit);
        this.maxRetries = Math.max(0, maxRetries);
        this.backoffInitial = positiveOrDefault(backoffInitial, Duration.ofMillis(500));
        this.backoffMultiplier = backoffMultiplier < 1 ? 2 : backoffMultiplier;
        this.backoffMax = positiveOrDefault(backoffMax, Duration.ofSeconds(2));
    }

    public String perguntar(String email, String sessao, String mensagem) {

        if (mensagem == null || mensagem.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mensagem é obrigatória.");
        }

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuário não autenticado."));

        String sessaoFinal = (sessao == null || sessao.isBlank())
                ? "default"
                : sessao.trim();

        String historico = montarHistorico(email, sessaoFinal);

        String prompt = """
                Histórico da conversa:

                %s

                Pergunta atual:
                %s
                """.formatted(historico, mensagem);

        log.debug("Prompt montado para a IA:\n{}", prompt);

        String resposta = gerarRespostaComProtecao(prompt);

        Conversa conversa = new Conversa();
        conversa.setUsuario(usuario);
        conversa.setSessao(sessaoFinal);
        conversa.setPergunta(mensagem);
        conversa.setResposta(resposta);

        repository.save(conversa);

        return resposta;
    }

    private String montarHistorico(String email, String sessao) {

        List<Conversa> historico = repository
                .findByUsuarioEmailAndSessaoOrderByIdDesc(
                        email,
                        sessao,
                        PageRequest.of(0, historyLimit));

        StringBuilder contexto = new StringBuilder();

        for (int i = historico.size() - 1; i >= 0; i--) {
            Conversa conversa = historico.get(i);

            contexto.append("Usuário: ")
                    .append(conversa.getPergunta())
                    .append("\n");
            contexto.append("Assistente: ")
                    .append(conversa.getResposta())
                    .append("\n");
        }

        return contexto.toString();
    }

    private String gerarRespostaComProtecao(String prompt) {

        if (!circuitBreaker.allowRequest()) {
            aiMetrics.recordCircuitBlocked();
            log.warn("Circuit breaker da IA aberto; requisição bloqueada.");

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "A IA está temporariamente indisponível. "
                            + "Tente novamente em instantes.");
        }

        try {
            String resposta = chamarIA(prompt);
            circuitBreaker.recordSuccess();
            aiMetrics.recordSuccess();
            return resposta;
        } catch (Exception exception) {
            circuitBreaker.recordFailure();
            aiMetrics.recordFailure(classifyFailure(exception));
            throw mapAiException(exception);
        }
    }

    private String chamarIA(String prompt) {

        int tentativas = maxRetries + 1;
        RuntimeException ultimaFalha = null;
        Duration espera = backoffInitial;

        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            try {
                return chamarIAUmaVez(prompt);
            } catch (RuntimeException exception) {
                ultimaFalha = exception;

                if (tentativa >= tentativas || !isTransient(exception)) {
                    throw exception;
                }

                log.warn("Falha transitória na IA (tentativa {}/{}): {}. Retentando em {}.",
                        tentativa, tentativas, exception.getMessage(), espera);

                aiMetrics.recordRetry();
                dormir(espera);
                espera = proximaEspera(espera);
            }
        }

        throw ultimaFalha;
    }

    private String chamarIAUmaVez(String prompt) {

        String resposta = chatClient.prompt()
                .system("""
                        Você é o SmartAtende AI.
                        Responda sempre em português.
                        Seja cordial.
                        Mantenha o contexto da conversa.
                        """)
                .user(prompt)
                .call()
                .content();

        if (resposta == null || resposta.isBlank()) {
            throw new IllegalStateException("A IA retornou uma resposta vazia.");
        }

        return resposta;
    }

    private boolean isTransient(Throwable throwable) {

        Throwable current = throwable;

        while (current != null) {
            if (current instanceof OpenAIServiceException serviceException) {
                return serviceException.statusCode() >= 500;
            }
            if (current instanceof OpenAIRetryableException
                    || current instanceof OpenAIIoException
                    || current instanceof ResourceAccessException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private String classifyFailure(Exception exception) {

        if (isRateLimit(exception)) {
            return "rate_limit";
        }

        if (isTimeout(exception)) {
            return "timeout";
        }

        return "provider";
    }

    private ResponseStatusException mapAiException(Exception exception) {

        String message = exception.getMessage() == null ? "" : exception.getMessage();

        log.warn("Falha ao consultar a IA: {}", message);

        if (isRateLimit(exception)) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "O limite de uso da IA foi atingido. Tente novamente mais tarde.",
                    exception);
        }

        if (isTimeout(exception)) {
            return new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "A IA demorou demais para responder. Tente novamente em instantes.",
                    exception);
        }

        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Não foi possível consultar a IA neste momento. "
                        + "Tente novamente em instantes.",
                exception);
    }

    private boolean isRateLimit(Throwable throwable) {

        Throwable current = throwable;

        while (current != null) {
            if (current instanceof OpenAIServiceException serviceException
                    && serviceException.statusCode() == 429) {
                return true;
            }
            if (current.getMessage() != null && current.getMessage().contains("429")) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private boolean isTimeout(Throwable throwable) {

        Throwable current = throwable;

        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof OpenAIIoException && current.getMessage() != null) {
                String message = current.getMessage().toLowerCase(Locale.ROOT);
                if (message.contains("timeout") || message.contains("timed out")) {
                    return true;
                }
            }
            current = current.getCause();
        }

        return false;
    }

    private Duration proximaEspera(Duration atual) {

        long millis = (long) (atual.toMillis() * backoffMultiplier);
        Duration proxima = Duration.ofMillis(millis);

        return proxima.compareTo(backoffMax) > 0 ? backoffMax : proxima;
    }

    private void dormir(Duration espera) {

        try {
            Thread.sleep(espera.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry da IA interrompido.", exception);
        }
    }

    private Duration positiveOrDefault(Duration value, Duration defaultValue) {

        if (value == null || value.isZero() || value.isNegative()) {
            return defaultValue;
        }

        return value;
    }
}
