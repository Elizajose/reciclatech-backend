package com.reciclatech.backend.repository;
import com.reciclatech.backend.model.Armazem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ArmazemRepository extends JpaRepository<Armazem, Long> {
    Optional<Armazem> findByLoginAndSenhaAndStatus(String login, String senha, String status);
    List<Armazem> findAllByStatus(String status);

    // Fase 2: armazéns de um dono. Toda busca vinda de requisição deve usar findByIdAndProprietarioId (garante a posse).
    List<Armazem> findByProprietarioIdOrderByNomeAsc(Long proprietarioId);
    Optional<Armazem> findByIdAndProprietarioId(Long id, Long proprietarioId);
}