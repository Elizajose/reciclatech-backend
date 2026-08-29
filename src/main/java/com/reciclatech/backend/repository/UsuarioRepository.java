package com.reciclatech.backend.repository;
import com.reciclatech.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByTelefoneAndArmazemId(String telefone, Long armazemId);
    List<Usuario> findAllByArmazemId(Long armazemId);
}