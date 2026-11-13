const CHAVE_TOKEN = "smartatende.token";
const CHAVE_REFRESH = "smartatende.refresh";
const CHAVE_EMAIL = "smartatende.email";
const CHAVE_NOME = "smartatende.nome";

const campo = document.getElementById("mensagem");
const chatBox = document.getElementById("chat-box");
const botaoEnviar = document.getElementById("btn-enviar");

let token = localStorage.getItem(CHAVE_TOKEN);
let refreshToken = localStorage.getItem(CHAVE_REFRESH);
let email = localStorage.getItem(CHAVE_EMAIL);
let nome = localStorage.getItem(CHAVE_NOME);
let carregando = false;

document.getElementById("tab-login").addEventListener("click", mostrarLogin);
document.getElementById("tab-register").addEventListener("click", mostrarRegistro);

document.getElementById("form-login").addEventListener("submit", function (event) {
    event.preventDefault();
    login();
});

document.getElementById("form-register").addEventListener("submit", function (event) {
    event.preventDefault();
    registrar();
});

document.getElementById("btn-enviar").addEventListener("click", enviarMensagem);
document.getElementById("btn-nova-conversa").addEventListener("click", novaConversa);
document.getElementById("btn-limpar-historico").addEventListener("click", limparHistorico);
document.getElementById("btn-logout").addEventListener("click", logout);

campo.addEventListener("keypress", function (event) {
    if (event.key === "Enter" && !carregando) {
        enviarMensagem();
    }
});

function chaveSessao(email) {
    return "smartatende.sessao." + email;
}

function gerarSessao() {
    if (window.crypto && crypto.randomUUID) {
        return crypto.randomUUID();
    }

    return "sessao-" + Date.now() + "-" + Math.random().toString(36).slice(2);
}

function getSessao() {
    const chave = chaveSessao(email);
    let sessao = localStorage.getItem(chave);

    if (!sessao) {
        sessao = gerarSessao();
        localStorage.setItem(chave, sessao);
    }

    return sessao;
}

function novaConversa() {
    if (!email || carregando) {
        return;
    }

    localStorage.removeItem(chaveSessao(email));
    chatBox.innerHTML = "";
    campo.value = "";
    getSessao();
    campo.focus();
}

async function iniciar() {
    if (!token || !email) {
        mostrarAuth();
        return;
    }

    const perfil = await carregarPerfil();

    if (!perfil) {
        await logout();
        return;
    }

    mostrarChat();
    carregarHistorico();
}

async function carregarPerfil() {
    try {
        const resposta = await fetchComToken("/api/auth/me");

        if (!resposta.ok) {
            return false;
        }

        const usuario = await resposta.json();

        email = usuario.email;
        nome = usuario.nome;

        localStorage.setItem(CHAVE_EMAIL, email);
        localStorage.setItem(CHAVE_NOME, nome || "");

        return true;
    } catch (e) {
        return false;
    }
}

function rotuloUsuario() {
    return nome ? nome + " (" + email + ")" : email;
}

function mostrarAuth() {
    document.getElementById("auth").classList.remove("hidden");
    document.getElementById("chat-app").classList.add("hidden");
    mostrarLogin();
}

function mostrarChat() {
    document.getElementById("auth").classList.add("hidden");
    document.getElementById("chat-app").classList.remove("hidden");
    document.getElementById("usuario-email").innerText = rotuloUsuario();
    campo.focus();
}

function mostrarLogin() {
    document.getElementById("form-login").classList.remove("hidden");
    document.getElementById("form-register").classList.add("hidden");
    document.getElementById("tab-login").classList.add("active");
    document.getElementById("tab-register").classList.remove("active");
}

function mostrarRegistro() {
    document.getElementById("form-login").classList.add("hidden");
    document.getElementById("form-register").classList.remove("hidden");
    document.getElementById("tab-login").classList.remove("active");
    document.getElementById("tab-register").classList.add("active");
}

async function login() {
    const emailInput = document.getElementById("login-email").value.trim();
    const senha = document.getElementById("login-senha").value;

    await autenticar(
        "/api/auth/login",
        { email: emailInput, senha: senha },
        "auth-msg-login"
    );
}

async function registrar() {
    const nomeInput = document.getElementById("reg-nome").value.trim();
    const emailInput = document.getElementById("reg-email").value.trim();
    const senha = document.getElementById("reg-senha").value;

    await autenticar(
        "/api/auth/register",
        { nome: nomeInput, email: emailInput, senha: senha },
        "auth-msg-register"
    );
}

async function autenticar(url, corpo, idMensagem) {

    try {
        const resposta = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(corpo)
        });

        if (!resposta.ok) {
            document.getElementById(idMensagem).innerText =
                mensagemPorStatus(resposta.status);
            return;
        }

        const dados = await resposta.json();

        token = dados.token;
        refreshToken = dados.refreshToken || null;
        email = dados.email;
        nome = dados.nome;

        localStorage.setItem(CHAVE_TOKEN, token);
        localStorage.setItem(CHAVE_REFRESH, refreshToken || "");
        localStorage.setItem(CHAVE_EMAIL, email);
        localStorage.setItem(CHAVE_NOME, nome || "");

        document.getElementById("auth-msg-login").innerText = "";
        document.getElementById("auth-msg-register").innerText = "";

        mostrarChat();
        carregarHistorico();
    } catch (e) {
        document.getElementById(idMensagem).innerText =
            "Não foi possível conectar ao servidor.";
    }
}

function mensagemPorStatus(status) {
    if (status === 409) {
        return "E-mail já cadastrado.";
    }
    if (status === 401) {
        return "Credenciais inválidas.";
    }
    if (status === 429) {
        return "Muitas tentativas. Aguarde um instante.";
    }
    if (status === 400) {
        return "Preencha todos os campos corretamente.";
    }
    return "Não foi possível autenticar (" + status + ").";
}

async function renovarToken() {
    if (!refreshToken) {
        return false;
    }

    try {
        const resposta = await fetch("/api/auth/refresh", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ refreshToken: refreshToken })
        });

        if (!resposta.ok) {
            return false;
        }

        const dados = await resposta.json();

        token = dados.token;
        refreshToken = dados.refreshToken || refreshToken;
        email = dados.email;
        nome = dados.nome;

        localStorage.setItem(CHAVE_TOKEN, token);
        localStorage.setItem(CHAVE_REFRESH, refreshToken);
        localStorage.setItem(CHAVE_EMAIL, email);
        localStorage.setItem(CHAVE_NOME, nome || "");

        return true;
    } catch (e) {
        return false;
    }
}

async function fetchComToken(url, options) {
    const requisicao = Object.assign({}, options || {});
    const headers = new Headers(requisicao.headers || {});

    if (token) {
        headers.set("Authorization", "Bearer " + token);
    }

    requisicao.headers = headers;

    let resposta = await fetch(url, requisicao);

    if (resposta.status === 401 && refreshToken) {
        const renovado = await renovarToken();

        if (renovado) {
            const novasHeaders = new Headers(requisicao.headers || {});
            novasHeaders.set("Authorization", "Bearer " + token);
            resposta = await fetch(url, {
                method: requisicao.method,
                headers: novasHeaders,
                body: requisicao.body
            });
        }
    }

    return resposta;
}

async function logout() {
    if (token) {
        try {
            await fetch("/api/auth/logout", {
                method: "POST",
                headers: {
                    Authorization: "Bearer " + token
                }
            });
        } catch (e) {
            // A sessão local deve ser encerrada mesmo se o servidor falhar.
        }
    }

    token = null;
    refreshToken = null;
    email = null;
    nome = null;

    localStorage.removeItem(CHAVE_TOKEN);
    localStorage.removeItem(CHAVE_REFRESH);
    localStorage.removeItem(CHAVE_EMAIL);
    localStorage.removeItem(CHAVE_NOME);

    chatBox.innerHTML = "";
    campo.value = "";
    definirCarregando(false);
    mostrarAuth();
}

async function carregarHistorico() {

    try {
        const resposta = await fetchComToken(
            "/api/chat/historico?sessao=" + encodeURIComponent(getSessao())
        );

        if (resposta.status === 401) {
            await logout();
            return;
        }

        if (!resposta.ok) {
            adicionarMensagem(
                "Não foi possível carregar o histórico.",
                "bot erro"
            );
            return;
        }

        const conversas = await resposta.json();

        chatBox.innerHTML = "";

        conversas.forEach(function (conversa) {
            adicionarMensagem(conversa.pergunta, "usuario", conversa.createdAt);
            adicionarMensagem(conversa.resposta, "bot", conversa.createdAt);
        });
    } catch (e) {
        adicionarMensagem(
            "Não foi possível carregar o histórico.",
            "bot erro"
        );
    }
}

async function limparHistorico() {
    if (!token || carregando) {
        return;
    }

    if (!window.confirm("Deseja apagar o histórico desta conversa?")) {
        return;
    }

    try {
        const resposta = await fetchComToken(
            "/api/chat/historico?sessao=" + encodeURIComponent(getSessao()),
            { method: "DELETE" }
        );

        if (resposta.status === 401) {
            await logout();
            return;
        }

        if (!resposta.ok) {
            adicionarMensagem(
                "Não foi possível limpar o histórico.",
                "bot erro"
            );
            return;
        }

        chatBox.innerHTML = "";
    } catch (e) {
        adicionarMensagem(
            "Não foi possível limpar o histórico.",
            "bot erro"
        );
    }
}

async function enviarMensagem() {

    if (!token) {
        mostrarAuth();
        return;
    }

    if (carregando) {
        return;
    }

    const mensagem = campo.value.trim();

    if (!mensagem) {
        return;
    }

    adicionarMensagem(mensagem, "usuario", new Date());

    campo.value = "";
    definirCarregando(true);

    const elementoCarregando = adicionarMensagem("Pensando...", "bot loading");

    try {
        const resposta = await fetchComToken(
            "/api/chat",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    sessao: getSessao(),
                    mensagem: mensagem
                })
            }
        );

        if (resposta.status === 401) {
            await logout();
            return;
        }

        if (!resposta.ok) {
            const erro = await resposta.json().catch(function () {
                return {};
            });

            elementoCarregando.remove();
            adicionarMensagem(
                erro.message || "Erro ao falar com o servidor (" + resposta.status + ").",
                "bot erro",
                new Date()
            );
            campo.value = mensagem;
            return;
        }

        const dados = await resposta.json();

        elementoCarregando.remove();
        adicionarMensagem(dados.resposta, "bot", new Date());
    } catch (e) {
        elementoCarregando.remove();
        adicionarMensagem(
            "Não foi possível conectar ao servidor.",
            "bot erro",
            new Date()
        );
        campo.value = mensagem;
    } finally {
        definirCarregando(false);
    }
}

function definirCarregando(valor) {
    carregando = valor;
    campo.disabled = valor;

    if (botaoEnviar) {
        botaoEnviar.disabled = valor;
        botaoEnviar.innerText = valor ? "Enviando..." : "Enviar";
    }
}

function formatarDataHora(valor) {
    if (!valor) {
        return "";
    }

    const data = new Date(valor);

    if (Number.isNaN(data.getTime())) {
        return "";
    }

    return data.toLocaleString("pt-BR", {
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
}

function adicionarMensagem(texto, classe, dataHora) {

    const div = document.createElement("div");
    const conteudo = document.createElement("span");

    div.className = classe;
    conteudo.innerText = texto;
    div.appendChild(conteudo);

    const hora = formatarDataHora(dataHora);

    if (hora) {
        const timestamp = document.createElement("small");
        timestamp.className = "timestamp";
        timestamp.innerText = hora;
        div.appendChild(timestamp);
    }

    chatBox.appendChild(div);

    chatBox.scrollTop = chatBox.scrollHeight;

    return div;
}

iniciar();
