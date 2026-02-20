package com.reciclatech.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity // Mantido para o projeto compilar enquanto migramos
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    // Este campo guardará o valor do KG ou da Unidade (ex: 20,00 para o Cobre)
    private BigDecimal precoPorKg;

    private String unidade; // "KG" ou "UN" (ex: Litro Pitú é UN, Cobre é KG)
}