package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.ChamadoMensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChamadoMensagemRepository extends JpaRepository<ChamadoMensagem, Long> {
    List<ChamadoMensagem> findAllByChamadoIdOrderByIdAsc(Long chamadoId);
    long countByChamadoId(Long chamadoId);
}
