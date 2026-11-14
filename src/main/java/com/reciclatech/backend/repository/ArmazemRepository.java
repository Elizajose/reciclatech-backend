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
    boolean existsByCnpj(String cnpj);

    /** Compara só letras e números (ignora ponto, barra e traço): "11.222.333/0001-81" = "11222333000181". */
    @org.springframework.data.jpa.repository.Query(value =
            "SELECT COUNT(*) > 0 FROM armazem WHERE UPPER(regexp_replace(COALESCE(cnpj, ''), '[^A-Za-z0-9]', '', 'g')) = :documento",
            nativeQuery = true)
    boolean existeDocumentoNormalizado(@org.springframework.data.repository.query.Param("documento") String documento);

    /** Trava a linha do armazém até o fim da transação: serializa saídas e ajustes de estoque. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM Armazem a WHERE a.id = :id")
    Optional<Armazem> travarPorId(@org.springframework.data.repository.query.Param("id") Long id);

    // Fase 14: código do armazém usado no login do operador
    boolean existsByCodigo(String codigo);
    List<Armazem> findByCodigoIsNull();
}