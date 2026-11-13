package br.com.smartatende.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class Conversa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String sessao;

    @Column(columnDefinition = "TEXT")
    private String pergunta;

    @Column(columnDefinition = "TEXT")
    private String resposta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public Conversa() {
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Conversa(String pergunta, String resposta) {
        this.pergunta = pergunta;
        this.resposta = resposta;
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

    public Usuario getUsuario() {
        return usuario;
    }

    public void setSessao(String sessao) {
        this.sessao = sessao;
    }

    public void setPergunta(String pergunta) {
        this.pergunta = pergunta;
    }

    public void setResposta(String resposta) {
        this.resposta = resposta;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}
