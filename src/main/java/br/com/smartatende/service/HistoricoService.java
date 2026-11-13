package br.com.smartatende.service;

import br.com.smartatende.dto.ConversaResponse;
import br.com.smartatende.model.Conversa;
import br.com.smartatende.repository.ConversaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoricoService {

    private static final int TAMANHO_MAXIMO = 500;

    private final ConversaRepository repository;

    public HistoricoService(ConversaRepository repository) {
        this.repository = repository;
    }

    public List<ConversaResponse> listar(
            String email,
            String sessao,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), TAMANHO_MAXIMO),
                Sort.by(Sort.Direction.ASC, "id"));

        List<Conversa> conversas = (sessao == null || sessao.isBlank())
                ? repository.findByUsuarioEmailOrderByIdAsc(email, pageable)
                : repository.findByUsuarioEmailAndSessaoOrderByIdAsc(
                        email, sessao, pageable);

        return conversas.stream()
                .map(ConversaResponse::from)
                .toList();
    }

    @Transactional
    public long excluir(String email, String sessao) {
        return (sessao == null || sessao.isBlank())
                ? repository.deleteByUsuarioEmail(email)
                : repository.deleteByUsuarioEmailAndSessao(email, sessao);
    }
}
