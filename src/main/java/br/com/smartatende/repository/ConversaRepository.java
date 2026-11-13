package br.com.smartatende.repository;

import br.com.smartatende.model.Conversa;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversaRepository
        extends JpaRepository<Conversa, Long> {

    List<Conversa> findByUsuarioEmailOrderByIdAsc(String email);

    List<Conversa> findByUsuarioEmailAndSessaoOrderByIdAsc(String email, String sessao);

    List<Conversa> findByUsuarioEmailAndSessaoOrderByIdDesc(
            String email,
            String sessao,
            Pageable pageable);
}
