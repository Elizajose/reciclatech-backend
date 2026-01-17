package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.repository.OfertaRepository;
import com.reciclatech.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OfertaService {

    @Autowired
    private com.reciclatech.backend.repository.MaterialRepository materialRepository;

    @Autowired
    private OfertaRepository ofertaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // 1. Cria oferta e CALCULA O PREÇO AUTOMATICAMENTE
    public Oferta criar(Oferta oferta, Long vendedorId) {
        Usuario vendedor = usuarioRepository.findById(vendedorId)
                .orElseThrow(() -> new RuntimeException("Vendedor não encontrado!"));

        oferta.setVendedor(vendedor);

        // --- A MÁGICA ACONTECE AQUI ---
        // Se o usuário não mandou preço, a gente calcula!
        if (oferta.getPrecoEstimado() == null) {
            BigDecimal precoCalculado = calcularPreco(oferta.getMaterial(), oferta.getPeso());
            oferta.setPrecoEstimado(precoCalculado);
        }

        return ofertaRepository.save(oferta);
    }

    // 2. Busca ofertas próximas
    public List<Oferta> buscarProximas(Double lat, Double lon, Double raioKm) {
        List<Oferta> todas = ofertaRepository.findByStatus(Oferta.StatusOferta.DISPONIVEL);
        return todas.stream()
                .filter(o -> calcularDistancia(lat, lon, o.getLatitude(), o.getLongitude()) <= raioKm)
                .collect(Collectors.toList());
    }

    // Lógica NOVA: Busca o preço na tabela do banco
    private BigDecimal calcularPreco(String nomeMaterial, Double peso) {
        if (nomeMaterial == null || peso == null) return BigDecimal.ZERO;

        // Busca no banco. Se achar, multiplica. Se não achar, preço é 0.
        return materialRepository.findByNome(nomeMaterial)
                .map(material -> material.getPrecoPorKg().multiply(BigDecimal.valueOf(peso)))
                .orElse(BigDecimal.ZERO);
    }

    // 4. Cálculo de GPS (Haversine)
    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        if (lat2 == 0 || lon2 == 0) return 999999;
        int R = 6371;
        double latDist = Math.toRadians(lat2 - lat1);
        double lonDist = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDist / 2) * Math.sin(latDist / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(lonDist / 2) * Math.sin(lonDist / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // 4. Aceitar Oferta (Muda o status para VENDIDO)
    public Oferta aceitarOferta(Long id) {
        Oferta oferta = ofertaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Oferta não encontrada"));

        oferta.setStatus(Oferta.StatusOferta.VENDIDO); // Troca o status
        return ofertaRepository.save(oferta);
    }
}