package com.reciclatech.backend;

import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.repository.MaterialRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;

@Configuration
public class CargaInicial {

    @Bean
    CommandLineRunner iniciarBanco(MaterialRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                // PESO (KG)
                salvar(repository, "Cobre", 38.50, "KG");
                salvar(repository, "Alumínio (Lata)", 6.80, "KG");
                salvar(repository, "Alumínio (Bloco)", 5.50, "KG");
                salvar(repository, "Ferro", 0.90, "KG");
                salvar(repository, "Papelão", 0.45, "KG");
                salvar(repository, "Plástico Filme", 0.80, "KG");

                // UNIDADE (UN) - GARRAFAS E LITROS
                salvar(repository, "Garrafa Cerveja (Vidro)", 0.25, "UN"); // Preço unitário
                salvar(repository, "Litro (Pitu/51)", 0.30, "UN");
                salvar(repository, "Litro (Whisky)", 0.50, "UN");
                salvar(repository, "Garrafão Água (Vencido)", 2.00, "UN");

                System.out.println("✅ Materiais carregados com unidades corretas!");
            }
        };
    }

    private void salvar(MaterialRepository repo, String nome, double preco, String unidade) {
        Material m = new Material();
        m.setNome(nome);
        m.setPrecoPorKg(BigDecimal.valueOf(preco));
        m.setUnidade(unidade); // Salva se é KG ou UN
        repo.save(m);
    }
}