package com.reciclatech.backend.service;

import com.reciclatech.backend.util.Assinatura;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Antes a situação do usuário só era conferida na hora do login: um operador desativado, uma senha trocada ou um
 * armazém bloqueado continuavam funcionando até a sessão expirar (8 h). Agora, a cada poucos segundos, a sessão é
 * conferida de novo no banco:
 * <ul>
 *   <li>operador/dono desativado, apagado ou com senha trocada: a sessão é encerrada;</li>
 *   <li>armazém bloqueado, suspenso ou com teste vencido: a sessão passa a ficar "bloqueada por pagamento";
 *       se o master reativar, o bloqueio some sozinho, sem precisar sair e entrar.</li>
 * </ul>
 */
@Component
public class ValidadorSessao {

    private static final Logger log = LoggerFactory.getLogger(ValidadorSessao.class);
    static final long VALIDADE_MS = 15_000L;
    private static final String S_VALIDADO_EM = "validadoEm";

    public enum Resultado { OK, ENCERRAR }

    private final JdbcTemplate jdbc;

    public ValidadorSessao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Resultado verificar(HttpSession s) {
        if (s == null) return Resultado.OK;
        boolean operando = s.getAttribute("adminLogado") != null;
        Object donoId = s.getAttribute("parceiroId");
        if (!operando && !(donoId instanceof Long)) return Resultado.OK; // visitante ou master: nada a conferir

        Object ultimo = s.getAttribute(S_VALIDADO_EM);
        long agora = System.currentTimeMillis();
        if (ultimo instanceof Long t && agora - t < VALIDADE_MS) return Resultado.OK;

        try {
            Resultado r = operando ? verificarOperacao(s) : verificarDono(s, (Long) donoId);
            if (r == Resultado.OK) s.setAttribute(S_VALIDADO_EM, agora);
            return r;
        } catch (IllegalStateException e) {
            return Resultado.OK; // sessão já invalidada por outra requisição
        } catch (Exception e) {
            // falha momentânea do banco não derruba ninguém; a próxima requisição tenta de novo
            log.warn("Não consegui revalidar a sessão: {}", e.getMessage());
            return Resultado.OK;
        }
    }

    /** Dono que ainda não escolheu armazém (tela de armazéns). */
    private Resultado verificarDono(HttpSession s, Long parceiroId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT ativo, senha_hash FROM parceiro WHERE id = ?", parceiroId);
        if (l.isEmpty() || !Boolean.TRUE.equals(l.get(0).get("ativo"))) return Resultado.ENCERRAR;
        return impressaoConfere(s, (String) l.get(0).get("senha_hash")) ? Resultado.OK : Resultado.ENCERRAR;
    }

    private Resultado verificarOperacao(HttpSession s) {
        Long armazemId;
        try {
            armazemId = Long.valueOf(String.valueOf(s.getAttribute("idPlanilhaAtiva")));
        } catch (NumberFormatException e) {
            return Resultado.ENCERRAR;
        }
        String origem = String.valueOf(s.getAttribute("origemLogin"));
        Long credId = s.getAttribute("credId") instanceof Long c ? c : null;

        List<Map<String, Object>> l;
        String hash = null;
        switch (origem) {
            case "FUNC" -> {
                if (credId == null) return Resultado.ENCERRAR;
                l = jdbc.queryForList("SELECT a.status, a.data_cadastro, f.ativo, f.senha AS hash FROM funcionario f " +
                        "JOIN armazem a ON a.id = f.armazem_id WHERE f.id = ? AND a.id = ?", credId, armazemId);
            }
            case "DONO" -> {
                Object p = s.getAttribute("parceiroId");
                if (!(p instanceof Long parceiroId)) return Resultado.ENCERRAR;
                l = jdbc.queryForList("SELECT a.status, a.data_cadastro, p.ativo, p.senha_hash AS hash FROM armazem a " +
                        "JOIN parceiro p ON p.id = a.proprietario_id WHERE a.id = ? AND p.id = ?", armazemId, parceiroId);
            }
            case "LEGADO" -> l = jdbc.queryForList("SELECT status, data_cadastro, TRUE AS ativo, senha AS hash FROM armazem " +
                    "WHERE id = ? AND proprietario_id IS NULL", armazemId);
            default -> l = jdbc.queryForList("SELECT status, data_cadastro, TRUE AS ativo, NULL AS hash FROM armazem WHERE id = ?", armazemId);
        }
        if (l.isEmpty()) return Resultado.ENCERRAR;
        Map<String, Object> linha = l.get(0);
        if (!Boolean.TRUE.equals(linha.get("ativo"))) return Resultado.ENCERRAR;
        if (!"null".equals(origem) && !impressaoConfere(s, (String) linha.get("hash"))) return Resultado.ENCERRAR;

        String status = linha.get("status") == null ? "ATIVO" : linha.get("status").toString();
        LocalDate cadastro = linha.get("data_cadastro") instanceof java.sql.Date d ? d.toLocalDate() : null;
        boolean bloqueado = Assinatura.bloqueado(status, cadastro, LocalDate.now(ZoneId.of("America/Recife")));
        s.setAttribute("statusAssinatura", status);
        if (bloqueado) s.setAttribute("bloqueadoPagamento", true);
        else s.removeAttribute("bloqueadoPagamento");
        return Resultado.OK;
    }

    private static boolean impressaoConfere(HttpSession s, String hashAtual) {
        Object guardada = s.getAttribute("credFp");
        if (!(guardada instanceof String fp) || fp.isEmpty()) return true; // sessão sem impressão (não deveria existir): não derruba
        return fp.equals(com.reciclatech.backend.util.Impressao.de(hashAtual));
    }
}
