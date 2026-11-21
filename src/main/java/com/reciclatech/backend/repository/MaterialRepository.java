package com.reciclatech.backend.repository;
import com.reciclatech.backend.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {
    List<Material> findAllByArmazemIdOrderByNomeAsc(Long armazemId);

    /** Fase 27: preços de vários armazéns numa consulta só (cotação pública da página inicial). */
    List<Material> findAllByArmazemIdIn(java.util.Collection<Long> armazemIds);
}