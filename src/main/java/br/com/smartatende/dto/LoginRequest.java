package br.com.smartatende.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "E-mail é obrigatório.")
    @Email(message = "E-mail inválido.")
    @Size(max = 255, message = "E-mail deve ter no máximo 255 caracteres.")
    private String email;

    @NotBlank(message = "Senha é obrigatória.")
    @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres.")
    private String senha;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}
