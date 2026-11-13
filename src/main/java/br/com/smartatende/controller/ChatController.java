package br.com.smartatende.controller;

import br.com.smartatende.dto.ConversaResponse;
import br.com.smartatende.dto.MensagemRequest;
import br.com.smartatende.dto.MensagemResponse;
import br.com.smartatende.model.Conversa;
import br.com.smartatende.repository.ConversaRepository;
import br.com.smartatende.service.AIService;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AIService aiService;
    private final ConversaRepository conversaRepository;

    public ChatController(AIService aiService,
            ConversaRepository conversaRepository) {
        this.aiService = aiService;
        this.conversaRepository = conversaRepository;
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
            @RequestParam(required = false) String sessao) {

        String email = authentication.getName();

        List<Conversa> conversas = (sessao == null || sessao.isBlank())
                ? conversaRepository.findByUsuarioEmailOrderByIdAsc(email)
                : conversaRepository.findByUsuarioEmailAndSessaoOrderByIdAsc(email, sessao);

        return conversas.stream()
                .map(ConversaResponse::from)
                .toList();
    }
}
