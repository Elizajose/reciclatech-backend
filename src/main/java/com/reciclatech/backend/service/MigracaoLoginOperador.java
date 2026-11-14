package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.repository.ArmazemRepository;
import com.reciclatech.backend.util.CodigoArmazem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fase 14, ao iniciar (idempotente, pode rodar quantas vezes for):
 * 1) remove a trava antiga "login de funcionário único no sistema todo" (agora é único por armazém);
 * 2) dá um código a cada armazém que ainda não tem.
 * Qualquer falha é registrada no log e NÃO impede a aplicação de subir.
 */
@Component
public class MigracaoLoginOperador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracaoLoginOperador.class);

    static final String SQL_REMOVER_UNIQUE_LOGIN_ANTIGO =
            "DO $$ DECLARE r record; BEGIN " +
            "FOR r IN SELECT con.conname FROM pg_constraint con " +
            "  JOIN pg_class rel ON rel.oid = con.conrelid " +
            "  WHERE rel.relname = 'funcionario' AND con.contype = 'u' AND array_length(con.conkey, 1) = 1 " +
            "  AND (SELECT a.attname FROM pg_attribute a WHERE a.attrelid = rel.oid AND a.attnum = con.conkey[1]) = 'login' " +
            "LOOP EXECUTE format('ALTER TABLE funcionario DROP CONSTRAINT %I', r.conname); END LOOP; END $$;";

    private final JdbcTemplate jdbc;
    private final ArmazemRepository armazemRepository;

    public MigracaoLoginOperador(JdbcTemplate jdbc, ArmazemRepository armazemRepository) {
        this.jdbc = jdbc;
        this.armazemRepository = armazemRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute(SQL_REMOVER_UNIQUE_LOGIN_ANTIGO);
        } catch (Exception e) {
            log.warn("Fase 14: não consegui remover a trava antiga de login de funcionário (rode scripts/fase14-login-operador.sql): {}", e.getMessage());
        }
        try {
            List<Armazem> semCodigo = armazemRepository.findByCodigoIsNull();
            for (Armazem a : semCodigo) {
                a.setCodigo(novoCodigoLivre());
                armazemRepository.save(a);
            }
            if (!semCodigo.isEmpty()) log.info("Fase 14: {} armazém(ns) receberam código de operador.", semCodigo.size());
        } catch (Exception e) {
            log.warn("Fase 14: não consegui preencher os códigos dos armazéns: {}", e.getMessage());
        }
    }

    private String novoCodigoLivre() {
        for (int i = 0; i < 20; i++) {
            String c = CodigoArmazem.gerar();
            if (!armazemRepository.existsByCodigo(c)) return c;
        }
        throw new IllegalStateException("Não foi possível gerar um código livre.");
    }
}
