package com.reciclatech.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private BigDecimal precoPorKg; // Ou Preço por Unidade

    private String unidade; // <--- NOVO: "KG" ou "UN"
}