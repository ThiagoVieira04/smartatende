package br.com.smartatende.controller;

import br.com.smartatende.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, String> status() {

        return Map.of(
            "projeto", "SmartAtende AI",
            "mensagem", "Bem-vindo ao SmartAtende AI!",
            "status", "Online"
        );
    }
}
