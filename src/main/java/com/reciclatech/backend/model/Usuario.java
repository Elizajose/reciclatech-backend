package com.reciclatech.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Data // O Lombok cria o setTelefone automaticamente aqui
@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    // --- A MUDANÇA ESTÁ AQUI ---
    @Column(unique = true)
    private String telefone; // Novo ID principal (WhatsApp)

    private String cpf; // Agora virou opcional
    // ---------------------------

    private String email;

    @Enumerated(EnumType.STRING)
    private TipoUsuario tipo;

    public enum TipoUsuario {
        CATADOR,
        COMPRADOR
    }
}