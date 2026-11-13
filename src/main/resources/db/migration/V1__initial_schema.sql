CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    senha VARCHAR(255),
    role VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_email
    ON usuarios (email);

CREATE TABLE IF NOT EXISTS conversa (
    id BIGSERIAL PRIMARY KEY,
    sessao VARCHAR(100),
    pergunta TEXT,
    resposta TEXT,
    usuario_id BIGINT REFERENCES usuarios (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE conversa
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;

UPDATE conversa
    SET created_at = NOW()
    WHERE created_at IS NULL;

ALTER TABLE conversa
    ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE conversa
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE conversa
    ALTER COLUMN pergunta TYPE TEXT;

CREATE INDEX IF NOT EXISTS idx_conversa_usuario_sessao_id
    ON conversa (usuario_id, sessao, id);
