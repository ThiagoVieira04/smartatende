package br.com.smartatende;

import br.com.smartatende.model.Conversa;
import br.com.smartatende.model.Usuario;
import br.com.smartatende.repository.ConversaRepository;
import br.com.smartatende.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConversaRepository conversaRepository;

    @BeforeEach
    void limparBanco() {
        conversaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void registraELoga() throws Exception {
        registrar("João", "joao@teste.com", "senha123");

        Usuario usuario = usuarioRepository.findByEmail("joao@teste.com").orElseThrow();
        assertEquals("ROLE_USER", usuario.getRole());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("joao@teste.com", "senha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("joao@teste.com"));
    }

    @Test
    void emailDuplicadoRetorna409() throws Exception {
        registrar("João", "joao@teste.com", "senha123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson("Outro", "joao@teste.com", "outrasenha")))
                .andExpect(status().isConflict());
    }

    @Test
    void senhaErradaRetorna401() throws Exception {
        registrar("João", "joao@teste.com", "senha123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("joao@teste.com", "errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaProtegidaSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/chat/historico"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaProtegidaComTokenRetorna200() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");

        mockMvc.perform(get("/api/chat/historico")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void meRetornaUsuarioAutenticado() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("joao@teste.com"))
                .andExpect(jsonPath("$.nome").value("João"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    void meSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historicoIsoladoPorUsuarioEFiltroDeSessao() throws Exception {
        String tokenA = registrar("Ana", "ana@teste.com", "senha123");
        String tokenB = registrar("Bia", "bia@teste.com", "senha123");

        Usuario ana = usuarioRepository.findByEmail("ana@teste.com").orElseThrow();
        Usuario bia = usuarioRepository.findByEmail("bia@teste.com").orElseThrow();

        salvarConversa(ana, "s1", "Pergunta A1", "Resposta A1");
        salvarConversa(ana, "s2", "Pergunta A2", "Resposta A2");
        salvarConversa(bia, "s1", "Pergunta B1", "Resposta B1");

        mockMvc.perform(get("/api/chat/historico")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pergunta").value("Pergunta A1"))
                .andExpect(jsonPath("$[1].pergunta").value("Pergunta A2"));

        mockMvc.perform(get("/api/chat/historico")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pergunta").value("Pergunta B1"));

        mockMvc.perform(get("/api/chat/historico")
                .param("sessao", "s1")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pergunta").value("Pergunta A1"));
    }

    @Test
    void refreshRenovaTokensERevogaAposLogout() throws Exception {
        String json = registrarCompleto("João", "joao@teste.com", "senha123");
        String access = JsonPath.read(json, "$.token");
        String refresh = JsonPath.read(json, "$.refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeUsuarioRemovidoRetorna401() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");

        usuarioRepository.deleteAll();

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenNaoAcessaRotaProtegida() throws Exception {
        String json = registrarCompleto("João", "joao@teste.com", "senha123");
        String refresh = JsonPath.read(json, "$.refreshToken");

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenNaoRenova() throws Exception {
        String json = registrarCompleto("João", "joao@teste.com", "senha123");
        String access = JsonPath.read(json, "$.token");

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + access + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void respostaIncluiHeadersDeSegurancaERequestId() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");

        mockMvc.perform(get("/api/status")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Security-Policy",
                        containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void historicoRespeitaPaginacao() throws Exception {
        String token = registrar("Ana", "ana@teste.com", "senha123");
        Usuario ana = usuarioRepository.findByEmail("ana@teste.com").orElseThrow();

        for (int i = 0; i < 5; i++) {
            salvarConversa(ana, "s1", "Pergunta " + i, "Resposta " + i);
        }

        mockMvc.perform(get("/api/chat/historico")
                .param("sessao", "s1")
                .param("page", "0")
                .param("size", "2")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pergunta").value("Pergunta 0"))
                .andExpect(jsonPath("$[1].pergunta").value("Pergunta 1"));

        mockMvc.perform(get("/api/chat/historico")
                .param("sessao", "s1")
                .param("page", "2")
                .param("size", "2")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pergunta").value("Pergunta 4"));
    }

    @Test
    void excluiHistoricoDaSessao() throws Exception {
        String token = registrar("Ana", "ana@teste.com", "senha123");
        Usuario ana = usuarioRepository.findByEmail("ana@teste.com").orElseThrow();

        salvarConversa(ana, "s1", "Pergunta 1", "Resposta 1");
        salvarConversa(ana, "s2", "Pergunta 2", "Resposta 2");

        mockMvc.perform(delete("/api/chat/historico")
                .param("sessao", "s1")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(1, conversaRepository.count());

        mockMvc.perform(get("/api/chat/historico")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pergunta").value("Pergunta 2"));
    }

    @Test
    void excluiTodoHistorico() throws Exception {
        String token = registrar("Ana", "ana@teste.com", "senha123");
        Usuario ana = usuarioRepository.findByEmail("ana@teste.com").orElseThrow();

        salvarConversa(ana, "s1", "Pergunta 1", "Resposta 1");
        salvarConversa(ana, "s2", "Pergunta 2", "Resposta 2");

        mockMvc.perform(delete("/api/chat/historico")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(0, conversaRepository.count());
    }

    private String registrarCompleto(
            String nome,
            String email,
            String senha) throws Exception {

        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson(nome, email, senha)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String registrar(String nome, String email, String senha) throws Exception {
        return JsonPath.read(
                registrarCompleto(nome, email, senha),
                "$.token");
    }

    private String registroJson(String nome, String email, String senha) {
        return """
                {"nome":"%s","email":"%s","senha":"%s"}
                """.formatted(nome, email, senha);
    }

    private String loginJson(String email, String senha) {
        return """
                {"email":"%s","senha":"%s"}
                """.formatted(email, senha);
    }

    private void salvarConversa(
            Usuario usuario,
            String sessao,
            String pergunta,
            String resposta) {

        Conversa conversa = new Conversa();
        conversa.setUsuario(usuario);
        conversa.setSessao(sessao);
        conversa.setPergunta(pergunta);
        conversa.setResposta(resposta);

        conversaRepository.save(conversa);
    }
}
