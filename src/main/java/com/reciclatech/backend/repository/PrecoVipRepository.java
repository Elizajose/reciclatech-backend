package com.reciclatech.backend.repository;
import com.reciclatech.backend.model.PrecoVip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrecoVipRepository extends JpaRepository<PrecoVip, Long> {
    List<PrecoVip> findAllByArmazemId(Long armazemId);
    Optional<PrecoVip> findByArmazemIdAndTelefoneCatadorAndMaterialNome(Long armazemId, String telefone, String material);
}