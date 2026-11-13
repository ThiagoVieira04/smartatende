package br.com.smartatende;

import br.com.smartatende.model.Conversa;
import br.com.smartatende.repository.ConversaRepository;
import br.com.smartatende.repository.UsuarioRepository;
import br.com.smartatende.model.Usuario;
import br.com.smartatende.service.AiCircuitBreaker;
import com.jayway.jsonpath.JsonPath;
import com.openai.errors.OpenAIServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConversaRepository conversaRepository;

    @Autowired
    private AiCircuitBreaker circuitBreaker;

    @MockitoBean
    private ChatClient chatClient;

    private ChatClient.ChatClientRequestSpec ultimaRequest;

    @BeforeEach
    void limparBanco() {
        circuitBreaker.reset();
        conversaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void chatComSucessoPersisteHistorico() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        mockResposta("Resposta da IA");

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value("Resposta da IA"));

        assertEquals(1, conversaRepository.count());

        Conversa conversa = conversaRepository.findAll().get(0);
        assertEquals("s1", conversa.getSessao());
        assertEquals("Olá", conversa.getPergunta());
        assertEquals("Resposta da IA", conversa.getResposta());
        assertNotNull(conversa.getCreatedAt());

        mockMvc.perform(get("/api/chat/historico")
                .param("sessao", "s1")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].pergunta").value("Olá"));
    }

    @Test
    void mensagemVaziaRetorna400() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mensagem é obrigatória."));

        assertEquals(0, conversaRepository.count());
    }

    @Test
    void falhaDaIARetorna502ESemHistorico() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        mockFalha();

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        verify(chatClient, times(1)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void limiteDeUsoDaIARetorna503ESemHistorico() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        OpenAIServiceException rateLimit = mock(OpenAIServiceException.class);
        when(rateLimit.statusCode()).thenReturn(429);
        when(rateLimit.getMessage()).thenReturn("limite de uso excedido");
        mockFalha(rateLimit);

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "O limite de uso da IA foi atingido. Tente novamente mais tarde."));

        verify(chatClient, times(1)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void limiteDeUsoPorMensagemDoProvedorRetorna503() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        mockFalha(new RuntimeException(
                "429: Free usage limit reached. Free models work in OpenCode."));

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "O limite de uso da IA foi atingido. Tente novamente mais tarde."));

        verify(chatClient, times(1)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void respostaVaziaDaIARetorna502() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        mockResposta("   ");

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        verify(chatClient, times(1)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void erroNaoTransitorioDoProvedorRetorna502SemRetry() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        OpenAIServiceException falha = mock(OpenAIServiceException.class);
        when(falha.statusCode()).thenReturn(400);
        when(falha.getMessage()).thenReturn("requisição inválida");
        mockFalha(falha);

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        verify(chatClient, times(1)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void timeoutDaIARetorna504ESemHistorico() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        mockFalha(new ResourceAccessException(
                "timeout",
                new SocketTimeoutException("read timed out")));

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.message").value(
                        "A IA demorou demais para responder. Tente novamente em instantes."));

        verify(chatClient, times(3)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void falhaTransitoriaDaIARetentaAntesDeRetornar502() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        OpenAIServiceException falha = mock(OpenAIServiceException.class);
        when(falha.statusCode()).thenReturn(500);
        when(falha.getMessage()).thenReturn("erro interno do provedor");
        mockFalha(falha);

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        verify(chatClient, times(3)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void circuitBreakerAbreERetorna503SemConsultarIA() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        mockFalha();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/chat")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                    .andExpect(status().isBadGateway());
        }

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Olá\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "A IA está temporariamente indisponível. Tente novamente em instantes."));

        verify(chatClient, times(2)).prompt();
        assertEquals(0, conversaRepository.count());
    }

    @Test
    void healthExpoeEstadoDoCircuitBreaker() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.aiCircuitBreaker.status").value("UP"))
                .andExpect(jsonPath("$.components.aiCircuitBreaker.details.state").value("CLOSED"))
                .andExpect(jsonPath("$.components.aiCircuitBreaker.details.consecutiveFailures").value(0));
    }

    @Test
    void metricsExpoeEstadoDoCircuito() throws Exception {
        mockMvc.perform(get("/actuator/metrics/smartatende.ai.circuit.state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("smartatende.ai.circuit.state"))
                .andExpect(jsonPath("$.measurements[0].value").value(0.0));
    }

    @Test
    void enviaHistoricoComoMensagensEstruturadas() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        Usuario usuario = usuarioRepository.findByEmail("joao@teste.com").orElseThrow();

        Conversa anterior = new Conversa();
        anterior.setUsuario(usuario);
        anterior.setSessao("s1");
        anterior.setPergunta("Pergunta anterior");
        anterior.setResposta("Resposta anterior");
        conversaRepository.save(anterior);

        mockResposta("Resposta atual");

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessao\":\"s1\",\"mensagem\":\"Nova pergunta\"}"))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(ultimaRequest).messages(captor.capture());

        List<Message> mensagens = captor.getValue();

        assertTrue(mensagens.get(0) instanceof SystemMessage);
        assertTrue(mensagens.stream().anyMatch(mensagem ->
                mensagem instanceof UserMessage
                        && mensagem.getText().contains("Pergunta anterior")));
        assertTrue(mensagens.stream().anyMatch(mensagem ->
                mensagem instanceof AssistantMessage
                        && mensagem.getText().contains("Resposta anterior")));
        assertEquals(
                "Nova pergunta",
                ((UserMessage) mensagens.get(mensagens.size() - 1)).getText());
    }

    @Test
    void sessaoOmitidaUsaDefault() throws Exception {
        String token = registrar("João", "joao@teste.com", "senha123");
        mockResposta("Resposta da IA");

        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensagem\":\"Olá\"}"))
                .andExpect(status().isOk());

        assertEquals("default", conversaRepository.findAll().get(0).getSessao());
    }

    private void mockResposta(String conteudo) {
        ChatClient.ChatClientRequestSpec request =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response =
                mock(ChatClient.CallResponseSpec.class);

        ultimaRequest = request;

        when(chatClient.prompt()).thenReturn(request);
        when(request.messages(anyList())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn(conteudo);
    }

    private void mockFalha() {
        mockFalha(new RuntimeException("falha simulada"));
    }

    private void mockFalha(Throwable throwable) {
        ChatClient.ChatClientRequestSpec request =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response =
                mock(ChatClient.CallResponseSpec.class);

        ultimaRequest = request;

        when(chatClient.prompt()).thenReturn(request);
        when(request.messages(anyList())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenThrow(throwable);
    }

    private String registrar(String nome, String email, String senha) throws Exception {
        String json = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nome":"%s","email":"%s","senha":"%s"}
                        """.formatted(nome, email, senha)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(json, "$.token");
    }
}
