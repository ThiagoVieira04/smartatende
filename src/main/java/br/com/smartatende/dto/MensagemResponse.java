package br.com.smartatende.dto;

public class MensagemResponse {

    private String resposta;

    public MensagemResponse(String resposta) {
        this.resposta = resposta;
    }

    public String getResposta() {
        return resposta;
    }

    public void setResposta(String resposta) {
        this.resposta = resposta;
    }
}
