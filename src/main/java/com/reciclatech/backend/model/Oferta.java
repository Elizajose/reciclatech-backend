package com.reciclatech.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity // Mantido para suporte ao projeto atual enquanto migramos
public class Oferta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String material;

    private Double peso;
    private String endereco;

    // Latitude e Longitude (Mantemos para o futuro mapa)
    private Double latitude;
    private Double longitude;

    private BigDecimal precoEstimado;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    private StatusOferta status = StatusOferta.DISPONIVEL;

    private LocalDate dataCriacao = LocalDate.now();

    public enum StatusOferta {
        DISPONIVEL, // Vendedor solicitou coleta
        VENDIDO     // Comprador pesou e pagou
    }
}