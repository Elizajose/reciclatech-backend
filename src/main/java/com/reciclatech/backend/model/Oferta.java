package com.reciclatech.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
public class Oferta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String material;
    private Double peso; // Agora começa zerado e o comprador preenche depois
    private String endereco; // <--- NOVO CAMPO

    // Latitude e Longitude (Mantemos para o futuro mapa)
    private Double latitude;
    private Double longitude;

    private BigDecimal precoEstimado;

    @ManyToOne
    private Usuario vendedor;

    @Enumerated(EnumType.STRING)
    private StatusOferta status = StatusOferta.DISPONIVEL; // Começa Disponível

    private LocalDate dataCriacao = LocalDate.now();

    public enum StatusOferta {
        DISPONIVEL, // Vendedor solicitou coleta
        VENDIDO     // Comprador pesou e pagou
    }
}