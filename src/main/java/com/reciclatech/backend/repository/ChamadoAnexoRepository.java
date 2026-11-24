package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.ChamadoAnexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChamadoAnexoRepository extends JpaRepository<ChamadoAnexo, Long> {
    List<ChamadoAnexo> findAllByChamadoIdOrderByIdAsc(Long chamadoId);
    long countByChamadoId(Long chamadoId);
}
