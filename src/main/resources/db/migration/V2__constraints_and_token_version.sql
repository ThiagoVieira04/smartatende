ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 0;

UPDATE usuarios SET nome = 'Usuário ' || id WHERE nome IS NULL;
UPDATE usuarios SET email = 'usuario-' || id || '@invalid.local' WHERE email IS NULL;
UPDATE usuarios SET senha = '' WHERE senha IS NULL;
UPDATE usuarios SET role = 'ROLE_USER' WHERE role IS NULL;

ALTER TABLE usuarios
    ALTER COLUMN nome SET NOT NULL,
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN senha SET NOT NULL,
    ALTER COLUMN role SET NOT NULL;

ALTER TABLE usuarios
    ALTER COLUMN role SET DEFAULT 'ROLE_USER';

UPDATE conversa SET sessao = 'default' WHERE sessao IS NULL;
UPDATE conversa SET pergunta = '' WHERE pergunta IS NULL;
UPDATE conversa SET resposta = '' WHERE resposta IS NULL;

ALTER TABLE conversa
    ALTER COLUMN sessao SET NOT NULL,
    ALTER COLUMN pergunta SET NOT NULL,
    ALTER COLUMN resposta SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_conversa_usuario_created_at
    ON conversa (usuario_id, created_at DESC);
