package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.HistoricoAlteracao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoricoAlteracaoRepository extends JpaRepository<HistoricoAlteracao, Long> {
    List<HistoricoAlteracao> findByArmazemIdOrderByDataHoraDescIdDesc(Long armazemId, Pageable pageable);
    List<HistoricoAlteracao> findByArmazemIdAndEntidadeOrderByDataHoraDescIdDesc(Long armazemId, String entidade, Pageable pageable);

    /**
     * Fase 27: última vez que cada preço foi criado ou alterado, por armazém e nome do material.
     * Cada linha: [armazemId (Long), referencia (String), data/hora (LocalDateTime)].
     */
    @org.springframework.data.jpa.repository.Query("SELECT h.armazemId, h.referencia, MAX(h.dataHora) FROM HistoricoAlteracao h "
            + "WHERE h.entidade = 'MATERIAL' AND h.acao IN ('CRIADO', 'PRECO_ALTERADO') AND h.armazemId IN :ids "
            + "GROUP BY h.armazemId, h.referencia")
    List<Object[]> ultimasAlteracoesDePreco(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
