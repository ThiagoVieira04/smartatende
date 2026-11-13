# SmartAtende AI

API de atendimento inteligente com autenticação JWT, histórico de conversas por usuário e integração com modelos de linguagem via Spring AI.

## Stack

- Java 21
- Spring Boot 4.1.2
- Spring Security + JWT (JJWT)
- Spring Data JPA + PostgreSQL
- Spring AI (OpenAI-compatible)
- Frontend estático em HTML, CSS e JavaScript
- Testes com JUnit 5, MockMvc e H2

## Pré-requisitos

- JDK 21
- PostgreSQL 16 ou superior
- Uma chave de API compatível com OpenAI (o projeto usa o endpoint do OpenCode por padrão)

## Configuração

Crie o banco de dados:

```sql
CREATE DATABASE smartatende;
```

Defina as variáveis de ambiente:

| Variável | Obrigatória | Padrão (dev) | Descrição |
| --- | --- | --- | --- |
| `DB_URL` | não em dev | `jdbc:postgresql://localhost:5432/smartatende` | URL JDBC do PostgreSQL |
| `DB_USER` | não em dev | `postgres` | Usuário do banco |
| `DB_PASSWORD` | não em dev | `postgres` | Senha do banco |
| `JWT_SECRET` | sim em prod | segredo de desenvolvimento | Chave usada para assinar os tokens JWT |
| `OPENCODE_API_KEY` | sim para usar IA | `not-set` | Chave da API do provedor de IA |

Exemplo no PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/smartatende"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "postgres"
$env:JWT_SECRET = "uma-chave-forte-com-pelo-menos-32-caracteres"
$env:OPENCODE_API_KEY = "sua-chave"
```

## Execução

```powershell
.\mvnw.cmd spring-boot:run
```

A aplicação fica disponível em `http://localhost:8080`.

Para rodar os testes:

```powershell
.\mvnw.cmd test
```

## Documentação e saúde

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`
- Info: `http://localhost:8080/actuator/info`

## Resiliência da IA

O cliente de IA usa timeout, retry e circuit breaker:

| Configuração | Padrão | Descrição |
| --- | --- | --- |
| `spring.ai.openai.timeout` | `30s` | Timeout de cada requisição ao provedor |
| `spring.ai.openai.max-retries` | `0` | Retries do SDK desabilitados (evita repetir `429`) |
| `app.ai.retry.max-retries` | `2` | Retries para falhas transitórias (`5xx`, conexão e timeout) |
| `app.ai.retry.backoff-initial` | `500ms` | Espera inicial entre tentativas |
| `app.ai.retry.backoff-multiplier` | `2` | Fator de crescimento do backoff |
| `app.ai.retry.backoff-max` | `2s` | Espera máxima entre tentativas |
| `app.ai.circuit-breaker.failure-threshold` | `5` | Falhas consecutivas para abrir o circuito |
| `app.ai.circuit-breaker.open-duration` | `30s` | Tempo em que o circuito permanece aberto |

Comportamento em falha:

- `429` (limite de uso): não faz retry e retorna `503` com mensagem específica.
- Timeout: retorna `504` após esgotar os retries.
- Outras falhas do provedor (`5xx`): retorna `502` após esgotar os retries.
- Circuito aberto: retorna `503` sem chamar o provedor.
- Em qualquer falha, a conversa não é salva no histórico.

## Observabilidade

O Actuator expõe `health`, `info` e `metrics`.

O health check inclui o componente `aiCircuitBreaker` com `state`, `consecutiveFailures` e `openUntil`. Os detalhes aparecem para usuários autenticados (`show-details=when-authorized`).

Métricas da IA:

| Métrica | Descrição |
| --- | --- |
| `smartatende.ai.requests` | Requisições por resultado (`success`/`failure`) e tipo de falha (`rate_limit`, `timeout`, `provider`) |
| `smartatende.ai.retries` | Retentativas por falhas transitórias |
| `smartatende.ai.circuit.state` | Estado do circuito (`0`=CLOSED, `1`=HALF_OPEN, `2`=OPEN) |
| `smartatende.ai.circuit.failures` | Falhas consecutivas do circuito |
| `smartatende.ai.circuit.blocked` | Requisições bloqueadas pelo circuito aberto |

```powershell
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/smartatende.ai.circuit.state
```

## Docker

O `docker-compose.yml` sobe a aplicação e o PostgreSQL juntos.

```powershell
Copy-Item .env.example .env
# edite o .env com JWT_SECRET e OPENCODE_API_KEY
docker compose up --build
```

Para parar e remover também o volume do banco:

```powershell
docker compose down -v
```

## Integração contínua

O workflow `.github/workflows/ci.yml` executa `mvn -B test` em cada push e pull request.

## Endpoints

| Método | Caminho | Autenticação | Descrição |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | não | Cria um usuário e retorna um token JWT |
| `POST` | `/api/auth/login` | não | Autentica e retorna um token JWT |
| `GET` | `/api/auth/me` | Bearer | Retorna o usuário autenticado |
| `POST` | `/api/chat` | Bearer | Envia uma mensagem para a IA |
| `GET` | `/api/chat/historico` | Bearer | Lista o histórico do usuário |
| `GET` | `/api/status` | Bearer | Retorna o status da aplicação |

### Exemplos

Registro:

```powershell
curl -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"nome":"João","email":"joao@teste.com","senha":"senha123"}'
```

Login:

```powershell
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"joao@teste.com","senha":"senha123"}'
```

Chat:

```powershell
curl -X POST http://localhost:8080/api/chat `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer SEU_TOKEN" `
  -d '{"sessao":"minha-sessao","mensagem":"O que é Spring Boot?"}'
```

Histórico:

```powershell
curl "http://localhost:8080/api/chat/historico?sessao=minha-sessao" `
  -H "Authorization: Bearer SEU_TOKEN"
```

## Estrutura

```
src/main/java/br/com/smartatende
├── config       Configurações de segurança e IA
├── controller   Endpoints REST
├── dto          Objetos de entrada e saída
├── model        Entidades JPA
├── repository   Repositórios Spring Data
├── security     Filtro JWT
└── service      Regras de negócio
```

## Perfis

- `dev` (padrão): configuração local com valores de desenvolvimento.
- `test`: banco H2 em memória, usado pelos testes automatizados.
- `prod`: exige `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` e `OPENCODE_API_KEY` via ambiente.

Para executar com um perfil específico:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
.\mvnw.cmd spring-boot:run
```
