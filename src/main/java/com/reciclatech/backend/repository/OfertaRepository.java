package com.reciclatech.backend.repository;
import com.reciclatech.backend.model.Oferta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OfertaRepository extends JpaRepository<Oferta, Long> {
    List<Oferta> findAllByArmazemIdOrderByDataCriacaoDesc(Long armazemId);
    List<Oferta> findAllByUsuarioIdAndStatusIn(Long usuarioId, List<Oferta.StatusOferta> status);

    // Trava as linhas enquanto a transação roda: dois cliques/abas não consomem o mesmo pedido duas vezes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Oferta o WHERE o.usuario.id IN :usuarioIds AND o.status IN :status")
    List<Oferta> travarPorUsuariosEStatus(java.util.Collection<Long> usuarioIds, java.util.Collection<Oferta.StatusOferta> status);

    @Query("SELECT SUM(o.peso) FROM Oferta o WHERE o.armazem.id = :armazemId AND o.material = :nomeMaterial AND o.status IN ('VENDIDO', 'AJUSTE_POSITIVO')")
    Double somarEntradasPorMaterial(Long armazemId, String nomeMaterial);

    @Query("SELECT SUM(o.peso) FROM Oferta o WHERE o.armazem.id = :armazemId AND o.material = :nomeMaterial AND o.status IN ('SAIDA_INDUSTRIA', 'AJUSTE_NEGATIVO')")
    Double somarSaidasPorMaterial(Long armazemId, String nomeMaterial);

    /** Fase 31: itens de um recibo (atendimento) pelo código. */
    List<Oferta> findAllByLoteOrderByIdAsc(String lote);
}
