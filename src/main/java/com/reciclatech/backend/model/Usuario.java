package com.reciclatech.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime; // Importante para a data
import java.util.ArrayList;
import java.util.List;

@Data // O Lombok gera getDataColeta(), getStatus(), etc automaticamente
@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Column(unique = true)
    private String telefone; // ID principal (WhatsApp)

    private String cpf;
    private String email;
    private String endereco; // Adicionei pois o formulário do site envia "endereco"

    // --- CAMPOS NOVOS (ESSENCIAIS PARA O ERRO SUMIR) ---

    // 1. Data da Coleta (O Controller precisa disso para o filtro de 24h)
    private LocalDateTime dataColeta;

    // 2. Status (AGUARDANDO, CONCLUIDO...)
    @Enumerated(EnumType.STRING)
    private StatusColeta status;

    // 3. Lista de itens que ele está vendendo
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL)
    private List<Oferta> ofertas = new ArrayList<>();

    // --- SEU ENUM ANTIGO (Mantive para não quebrar nada) ---
    @Enumerated(EnumType.STRING)
    private TipoUsuario tipo;

    public enum TipoUsuario {
        CATADOR,
        COMPRADOR
    }

    // --- AUTOMATIZAÇÃO ---
    // Isso garante que todo usuário novo já nasça com Data de Hoje e Status Aguardando
    @PrePersist
    public void prePersist() {
        if (this.dataColeta == null) {
            this.dataColeta = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = StatusColeta.AGUARDANDO;
        }
    }
}