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
}
