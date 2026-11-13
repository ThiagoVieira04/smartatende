# SmartAtende AI

API de atendimento inteligente com autenticação JWT, histórico de conversas por usuário e integração com modelos de linguagem via Spring AI.

## Stack

- Java 21
- Spring Boot 4.1.2
- Spring Security + JWT (JJWT)
- Rate limiting por IP
- Spring Data JPA + PostgreSQL
- Spring AI (OpenAI-compatible)
- Actuator + Micrometer
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
| `DB_POOL_MAX_SIZE` | não | `10` | Tamanho máximo do pool Hikari |
| `DB_POOL_MIN_IDLE` | não | `2` | Conexões idle mantidas no pool |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | não | `30000` | Timeout para obter conexão do pool |
| `DB_POOL_MAX_LIFETIME_MS` | não | `1800000` | Tempo máximo de vida de uma conexão do pool |
| `JAVA_OPTS` | não (Docker) | `-XX:MaxRAMPercentage=75.0` | Opções da JVM no container |
| `JWT_SECRET` | sim em prod | segredo de desenvolvimento | Chave usada para assinar os tokens JWT |
| `OPENCODE_API_KEY` | sim para usar IA | `not-set` | Chave da API do provedor de IA |
| `OPENAI_BASE_URL` | não | `https://opencode.ai/zen/v1` | URL base compatível com OpenAI |
| `OPENAI_MODEL` | não | `deepseek-v4.1-flash-free` | Modelo de chat usado pela aplicação |

Exemplo no PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/smartatende"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "postgres"
$env:JWT_SECRET = "uma-chave-forte-com-pelo-menos-32-caracteres"
$env:OPENCODE_API_KEY = "sua-chave"
$env:OPENAI_BASE_URL = "https://opencode.ai/zen/v1"
$env:OPENAI_MODEL = "deepseek-v4.1-flash-free"
```

> O modelo `deepseek-v4.1-flash-free` é gratuito e pode retornar `429` quando o limite de uso é atingido. Para uso contínuo, configure `OPENAI_MODEL` e `OPENAI_BASE_URL` para um provedor/modelo com cota adequada.

## Autenticação

O registro e o login retornam um par de tokens:

- `token`: access token JWT, válido por 15 minutos.
- `refreshToken`: refresh token JWT, válido por 7 dias.

Envie o access token no header `Authorization: Bearer <token>`. Quando ele expirar, use `POST /api/auth/refresh` com o refresh token para obter um novo par. O `POST /api/auth/logout` incrementa a versão do usuário e invalida os tokens emitidos anteriormente.

## Rate limit

Os limites são aplicados por IP em uma janela fixa de 60 segundos:

| Categoria | Endpoints | Limite padrão |
| --- | --- | --- |
| `auth` | `POST /api/auth/login`, `/register`, `/refresh` | 20/min |
| `chat` | `POST /api/chat` | 30/min |

Respostas limitadas retornam `429 Too Many Requests`, com `Retry-After`, `X-RateLimit-Limit` e `X-RateLimit-Remaining`. As requisições permitidas também recebem os headers `X-RateLimit-Limit` e `X-RateLimit-Remaining`.

As configurações ficam em `app.rate-limit.*` (`enabled`, `window`, `auth-per-minute`, `chat-per-minute`). O perfil `test` desabilita o rate limit.

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
- Info: `http://localhost:8080/actuator/info` (inclui nome e versão do build)

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

O Actuator expõe `health`, `info` e `metrics`. O `info` combina os dados de `info.app.*` com os metadados gerados pelo `build-info` (artefato, grupo e versão).

O health check inclui o componente `aiCircuitBreaker` com `state`, `consecutiveFailures` e `openUntil`. Os detalhes aparecem para usuários autenticados (`show-details=when-authorized`).

Métricas de autenticação:

| Métrica | Descrição |
| --- | --- |
| `smartatende.auth.login` | Logins por resultado (`success`/`failure`) |
| `smartatende.auth.register` | Cadastros bem-sucedidos |

Métricas da IA:

| Métrica | Descrição |
| --- | --- |
| `smartatende.ai.requests` | Requisições por resultado (`success`/`failure`) e tipo de falha (`rate_limit`, `timeout`, `provider`) |
| `smartatende.ai.retries` | Retentativas por falhas transitórias |
| `smartatende.ai.latency` | Latência das chamadas à IA, incluindo retries |
| `smartatende.ai.circuit.state` | Estado do circuito (`0`=CLOSED, `1`=HALF_OPEN, `2`=OPEN) |
| `smartatende.ai.circuit.failures` | Falhas consecutivas do circuito |
| `smartatende.ai.circuit.blocked` | Requisições bloqueadas pelo circuito aberto |

Cada resposta inclui `X-Request-Id`, também presente nos logs via MDC. Em produção (`prod`), apenas `health` e `info` são expostos e as métricas exigem autenticação.

```powershell
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/smartatende.ai.circuit.state
```

## Docker

O `docker-compose.yml` sobe a aplicação e o PostgreSQL juntos. A imagem instala `curl` e define um `HEALTHCHECK` em `/actuator/health`; o serviço da aplicação usa `restart: unless-stopped` e aguarda o banco ficar saudável.

```powershell
Copy-Item .env.example .env
# edite o .env com JWT_SECRET e OPENCODE_API_KEY
docker compose up --build
```

Acompanhe o estado dos serviços e do health check:

```powershell
docker compose ps
docker inspect --format '{{json .State.Health}}' smartatende-app-1
```

Para parar e remover também o volume do banco:

```powershell
docker compose down -v
```

## Operação

- O servidor usa `server.shutdown=graceful` e aguarda até 20s pelas requisições em andamento no encerramento.
- O pool Hikari é configurável por `DB_POOL_*`; a imagem define `JAVA_OPTS` com `-XX:MaxRAMPercentage=75.0`.
- Respostas JSON/HTML/texto são comprimidas quando o cliente envia `Accept-Encoding`.
- O `/actuator/info` expõe os metadados do build gerados pelo `spring-boot-maven-plugin`.

## Integração contínua

O workflow `.github/workflows/ci.yml` executa `./mvnw -B verify` e o build da imagem Docker em cada push e pull request.

## Endpoints

| Método | Caminho | Autenticação | Descrição |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | não | Cria um usuário e retorna access + refresh token |
| `POST` | `/api/auth/login` | não | Autentica e retorna access + refresh token |
| `POST` | `/api/auth/refresh` | não | Renova o par de tokens a partir do refresh token |
| `POST` | `/api/auth/logout` | Bearer | Invalida os tokens emitidos anteriormente |
| `GET` | `/api/auth/me` | Bearer | Retorna o usuário autenticado |
| `POST` | `/api/chat` | Bearer | Envia uma mensagem para a IA |
| `GET` | `/api/chat` | Bearer | Retorna instruções de uso do endpoint |
| `GET` | `/api/chat/historico` | Bearer | Lista o histórico do usuário (`sessao`, `page`, `size`) |
| `DELETE` | `/api/chat/historico` | Bearer | Remove o histórico do usuário (`sessao` opcional) |
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

Refresh:

```powershell
curl -X POST http://localhost:8080/api/auth/refresh `
  -H "Content-Type: application/json" `
  -d '{"refreshToken":"SEU_REFRESH_TOKEN"}'
```

Logout:

```powershell
curl -X POST http://localhost:8080/api/auth/logout `
  -H "Authorization: Bearer SEU_TOKEN"
```

Chat:

```powershell
curl -X POST http://localhost:8080/api/chat `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer SEU_TOKEN" `
  -d '{"sessao":"minha-sessao","mensagem":"O que é Spring Boot?"}'
```

Histórico paginado:

```powershell
curl "http://localhost:8080/api/chat/historico?sessao=minha-sessao&page=0&size=50" `
  -H "Authorization: Bearer SEU_TOKEN"
```

Limpar histórico da sessão:

```powershell
curl -X DELETE "http://localhost:8080/api/chat/historico?sessao=minha-sessao" `
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
├── security     Filtros JWT, request ID e rate limit
└── service      Regras de negócio
```

## Perfis

- `dev` (padrão): configuração local com valores de desenvolvimento.
- `test`: banco H2 em memória, rate limit desabilitado, usado pelos testes automatizados.
- `postgres-test`: PostgreSQL via Testcontainers para validar as migrations Flyway.
- `prod`: exige `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` e `OPENCODE_API_KEY` via ambiente.

Para executar com um perfil específico:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
.\mvnw.cmd spring-boot:run
```
