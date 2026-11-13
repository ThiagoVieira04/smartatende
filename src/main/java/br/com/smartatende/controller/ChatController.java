package br.com.smartatende.controller;

import br.com.smartatende.config.OpenApiConfig;
import br.com.smartatende.dto.ConversaResponse;
import br.com.smartatende.dto.MensagemRequest;
import br.com.smartatende.dto.MensagemResponse;
import br.com.smartatende.service.AIService;
import br.com.smartatende.service.HistoricoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ChatController {

    private final AIService aiService;
    private final HistoricoService historicoService;

    public ChatController(
            AIService aiService,
            HistoricoService historicoService) {
        this.aiService = aiService;
        this.historicoService = historicoService;
    }

    @PostMapping
    public MensagemResponse conversar(
            @Valid @RequestBody MensagemRequest request,
            Authentication authentication) {

        String resposta =
                aiService.perguntar(
                        authentication.getName(),
                        request.getSessao(),
                        request.getMensagem());

        return new MensagemResponse(resposta);
    }

    @GetMapping
    public MensagemResponse instrucoes() {
        return new MensagemResponse(
                "Use POST /api/chat com JSON {\"mensagem\": \"sua pergunta\"} para conversar.");
    }

    @GetMapping("/historico")
    public List<ConversaResponse> listarHistorico(
            Authentication authentication,
            @RequestParam(required = false) String sessao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        return historicoService.listar(
                authentication.getName(),
                sessao,
                page,
                size);
    }

    @DeleteMapping("/historico")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirHistorico(
            Authentication authentication,
            @RequestParam(required = false) String sessao) {

        historicoService.excluir(authentication.getName(), sessao);
    }
}
