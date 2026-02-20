package com.reciclatech.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data // Gera getTelefone, getNome automaticamente
@NoArgsConstructor
@AllArgsConstructor
@Entity // Mantido para não quebrar a compilação atual
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Column(unique = true)
    private String telefone; // ID principal (WhatsApp)

    private String cpf;
    private String email;
    private String endereco;

    // 1. Data da Coleta
    private LocalDateTime dataColeta;

    // 2. Status (AGUARDANDO, CONCLUIDO...)
    @Enumerated(EnumType.STRING)
    private StatusColeta status;

    // 3. Lista de itens (No Sheets, isso será gerenciado pelo ID na aba de Ofertas)
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL)
    private List<Oferta> ofertas = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private TipoUsuario tipo;

    public enum TipoUsuario {
        CATADOR,
        COMPRADOR
    }

    // --- IMPORTANTE PARA O GOOGLE SHEETS ---
    // Como a planilha não executa o @PrePersist sozinha,
    // chamaremos este método manualmente no Service antes de salvar.
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