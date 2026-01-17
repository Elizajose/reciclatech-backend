package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    Optional<Material> findByNome(String nome);
}