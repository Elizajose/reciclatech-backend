package com.reciclatech.backend.repository;
import com.reciclatech.backend.model.CaixaDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaixaDiarioRepository extends JpaRepository<CaixaDiario, Long> {
    Optional<CaixaDiario> findByArmazemIdAndDataFechamento(Long armazemId, LocalDate data);
    List<CaixaDiario> findAllByArmazemIdOrderByDataFechamentoDesc(Long armazemId);
}