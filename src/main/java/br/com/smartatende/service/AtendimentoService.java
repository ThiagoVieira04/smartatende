package br.com.smartatende.service;

import org.springframework.stereotype.Service;

@Service
public class AtendimentoService {

    public String gerarMensagem() {
        return "Bem-vindo ao SmartAtende AI!";
    }

    public String responderUsuario(String nome) {
        return "Olá " + nome + ", seja bem-vindo ao SmartAtende!";
    }

    public String processarMensagem(String mensagem) {

        String texto = mensagem.toLowerCase();

        if (texto.contains("spring boot")) {
            return "Spring Boot é um framework Java que acelera o desenvolvimento de aplicações.";
        }

        if (texto.contains("java")) {
            return "Java é uma linguagem orientada a objetos muito utilizada no mercado.";
        }

        if (texto.contains("olá")) {
            return "Olá! Como posso ajudar você?";
        }

        return "Desculpe, ainda estou aprendendo. Em breve terei integração com IA.";
    }
}
