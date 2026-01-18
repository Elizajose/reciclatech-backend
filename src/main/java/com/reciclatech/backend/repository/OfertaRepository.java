package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // <--- IMPORTANTE
import org.springframework.data.repository.query.Param; // <--- IMPORTANTE
import java.util.List;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    List<Oferta> findByStatus(Oferta.StatusOferta status);

    List<Oferta> findByUsuarioIdAndStatus(Long usuarioId, Oferta.StatusOferta status);

    // --- NOVO: RECICLÔMETRO ---
    // Soma o peso de todas as ofertas que já foram VENDIDAS
    @Query("SELECT SUM(o.peso) FROM Oferta o WHERE o.status = :status")
    Double somarPesoTotal(@Param("status") Oferta.StatusOferta status);
}