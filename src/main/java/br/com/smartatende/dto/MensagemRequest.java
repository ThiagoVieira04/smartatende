package br.com.smartatende.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MensagemRequest {

    @Size(max = 100, message = "Sessão deve ter no máximo 100 caracteres.")
    private String sessao;

    @NotBlank(message = "Mensagem é obrigatória.")
    @Size(max = 4000, message = "Mensagem deve ter no máximo 4000 caracteres.")
    private String mensagem;

    public String getSessao() {
        return sessao;
    }

    public void setSessao(String sessao) {
        this.sessao = sessao;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }
}
