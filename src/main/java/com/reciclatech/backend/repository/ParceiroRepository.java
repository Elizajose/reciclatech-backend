package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Parceiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParceiroRepository extends JpaRepository<Parceiro, Long> {
    Optional<Parceiro> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /** Trava a linha do dono até o fim da transação: dois cadastros de armazém ao mesmo tempo não furam o limite do plano. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Parceiro p WHERE p.id = :id")
    Optional<Parceiro> travarPorId(@org.springframework.data.repository.query.Param("id") Long id);
}
