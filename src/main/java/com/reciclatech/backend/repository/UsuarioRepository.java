package com.reciclatech.backend.repository;

import com.reciclatech.backend.model.Usuario; // <--- Importante: Importar sua Entidade
import org.springframework.data.jpa.repository.JpaRepository; // <--- Importante
import java.util.Optional; // <--- Importante para evitar erros se não achar ninguém

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // O Spring lê "findByCpf" e cria automaticamente o SQL:
    // SELECT * FROM usuario WHERE cpf = ?
    Optional<Usuario> findByTelefone(String telefone);
}