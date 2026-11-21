package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Chamado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChamadoRepository extends JpaRepository<Chamado, Long> {
    List<Chamado> findAllByArmazemIdOrderByAtualizadoEmDesc(Long armazemId);
    long countByArmazemIdAndLidoPeloArmazemFalse(Long armazemId);
    Optional<Chamado> findByIdAndArmazemId(Long id, Long armazemId);
    List<Chamado> findTop200ByOrderByAtualizadoEmDesc();
    long countByLidoPeloSuporteFalse();
}
