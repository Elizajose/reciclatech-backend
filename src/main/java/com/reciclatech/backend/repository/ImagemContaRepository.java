package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.ImagemConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImagemContaRepository extends JpaRepository<ImagemConta, Long> {
    Optional<ImagemConta> findByTipoAndRefId(String tipo, Long refId);

    /** Só os ids que TÊM imagem (não carrega os bytes). */
    @Query("SELECT i.refId FROM ImagemConta i WHERE i.tipo = :tipo AND i.refId IN :ids")
    List<Long> idsComImagem(@Param("tipo") String tipo, @Param("ids") Collection<Long> ids);
}
