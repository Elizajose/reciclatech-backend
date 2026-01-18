package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    // Busca ofertas pelo status (Ex: DISPONIVEL)
    List<Oferta> findByStatus(Oferta.StatusOferta status);

    // --- A CORREÇÃO ESTÁ AQUI ---
    // Mudamos de 'VendedorId' para 'UsuarioId' porque o campo na Oferta agora chama 'usuario'
    List<Oferta> findByUsuarioIdAndStatus(Long usuarioId, Oferta.StatusOferta status);
}