package br.com.smartatende.dto;

import br.com.smartatende.model.Conversa;

import java.time.Instant;

public class ConversaResponse {

    private final Long id;
    private final String sessao;
    private final String pergunta;
    private final String resposta;
    private final Instant createdAt;

    public ConversaResponse(
            Long id,
            String sessao,
            String pergunta,
            String resposta,
            Instant createdAt) {
        this.id = id;
        this.sessao = sessao;
        this.pergunta = pergunta;
        this.resposta = resposta;
        this.createdAt = createdAt;
    }

    public static ConversaResponse from(Conversa conversa) {
        return new ConversaResponse(
                conversa.getId(),
                conversa.getSessao(),
                conversa.getPergunta(),
                conversa.getResposta(),
                conversa.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getSessao() {
        return sessao;
    }

    public String getPergunta() {
        return pergunta;
    }

    public String getResposta() {
        return resposta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
