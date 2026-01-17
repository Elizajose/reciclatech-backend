package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    // Já tínhamos esse:
    List<Oferta> findByStatus(Oferta.StatusOferta status);

    // ADICIONE ESSE NOVO (Filtra por ID do Vendedor + Status):
    List<Oferta> findByVendedorIdAndStatus(Long vendedorId, Oferta.StatusOferta status);
}