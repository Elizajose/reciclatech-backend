package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.model.StatusColeta;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.util.RestricaoEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Fase 30, ao iniciar (idempotente): atualiza as travas CHECK das colunas "enum" que o banco criou com valores antigos.
 * Motivo: em bancos criados antes da Fase 8, cancelar um agendamento falhava ("Não foi possível cancelar"),
 * porque a trava da coluna oferta.status não aceitava CANCELADO.
 * Qualquer falha é registrada no log e NÃO impede a aplicação de subir.
 */
@Component
public class MigracaoRestricoesEnum implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigracaoRestricoesEnum.class);

    private final JdbcTemplate jdbc;

    public MigracaoRestricoesEnum(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static List<String> nomes(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        atualizar("oferta", "status", nomes(Oferta.StatusOferta.values()));
        atualizar("usuario", "status", nomes(StatusColeta.values()));
        atualizar("usuario", "tipo", nomes(Usuario.TipoUsuario.values()));
    }

    private void atualizar(String tabela, String coluna, List<String> valores) {
        try {
            jdbc.execute(RestricaoEnum.sqlAtualizar(tabela, coluna, valores));
        } catch (Exception e) {
            log.warn("Fase 30: não consegui conferir a trava de {}.{} (veja RELATORIO-VPS-fase30.md): {}", tabela, coluna, e.getMessage());
        }
    }
}
