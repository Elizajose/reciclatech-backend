package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Long> {

    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("delete from RecuperacaoSenha r where r.parceiro.id = :parceiroId")
    void apagarDoParceiro(@Param("parceiroId") Long parceiroId);
}
