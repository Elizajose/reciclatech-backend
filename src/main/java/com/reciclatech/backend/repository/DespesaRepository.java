package com.reciclatech.backend.repository;
import com.reciclatech.backend.model.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, Long> {
    List<Despesa> findAllByArmazemIdOrderByDataDespesaDesc(Long armazemId);
    List<Despesa> findAllByArmazemIdAndDataDespesa(Long armazemId, LocalDate data);
}