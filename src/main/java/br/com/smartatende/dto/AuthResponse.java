package br.com.smartatende.dto;

public class AuthResponse {

    private final String token;
    private final String nome;
    private final String email;

    public AuthResponse(String token, String nome, String email) {
        this.token = token;
        this.nome = nome;
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }
}
