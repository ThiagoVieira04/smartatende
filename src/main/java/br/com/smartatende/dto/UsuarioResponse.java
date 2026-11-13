package br.com.smartatende.dto;

public class UsuarioResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final String role;

    public UsuarioResponse(Long id, String nome, String email, String role) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
