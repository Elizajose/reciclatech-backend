package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Parceiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParceiroRepository extends JpaRepository<Parceiro, Long> {
    Optional<Parceiro> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
