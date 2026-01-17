package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.service.OfertaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ofertas")
public class OfertaController {

    @Autowired
    private OfertaService service;

    // ROTA 1: Criar Oferta (POST)
    // Ex: http://localhost:8080/api/ofertas?vendedorId=1
    @PostMapping
    public Oferta criar(@RequestBody Oferta oferta, @RequestParam Long vendedorId) {
        return service.criar(oferta, vendedorId);
    }

    // ROTA 2: Buscar Próximas (GET)
    // Ex: http://localhost:8080/api/ofertas/proximas?lat=-23.5&lon=-46.6&raio=10
    @GetMapping("/proximas")
    public List<Oferta> buscarProximas(@RequestParam Double lat,
                                       @RequestParam Double lon,
                                       @RequestParam Double raio) {
        return service.buscarProximas(lat, lon, raio);
    }
    // ROTA 3: Aceitar/Comprar (PUT)
    // Ex: http://localhost:8080/api/ofertas/1/aceitar
    @PutMapping("/{id}/aceitar")
    public Oferta aceitar(@PathVariable Long id) {
        return service.aceitarOferta(id);
    }
}