package br.com.smartatende.controller;

import br.com.smartatende.service.AtendimentoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StatusController {

    private final AtendimentoService atendimentoService;

    public StatusController(AtendimentoService atendimentoService) {
        this.atendimentoService = atendimentoService;
    }

    @GetMapping("/api/status")
    public Map<String, String> status() {

        return Map.of(
            "projeto", "SmartAtende AI",
            "mensagem", atendimentoService.gerarMensagem(),
            "status", "Online"
        );
    }

    @GetMapping("/api/boas-vindas")
    public String boasVindas() {
        return atendimentoService.responderUsuario("João");
    }
}
