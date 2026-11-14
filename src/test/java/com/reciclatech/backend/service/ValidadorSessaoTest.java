package com.reciclatech.backend.service;

import com.reciclatech.backend.util.Impressao;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** A sessão é conferida no banco a cada poucos segundos (antes só no login e valia por 8 h). */
class ValidadorSessaoTest {

    /** Banco falso: devolve a linha programada e conta as consultas. */
    static class BancoFalso extends JdbcTemplate {
        List<Map<String, Object>> linhas = new ArrayList<>();
        int consultas = 0;
        boolean cai = false;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            consultas++;
            if (cai) throw new RuntimeException("banco fora do ar");
            return linhas;
        }
    }

    private static Map<String, Object> linha(String status, LocalDate cadastro, boolean ativo, String hash) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("data_cadastro", cadastro == null ? null : java.sql.Date.valueOf(cadastro));
        m.put("ativo", ativo);
        m.put("hash", hash);
        return m;
    }

    private static HttpSession sessao(Map<String, Object> attrs) {
        return (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "getAttribute": return attrs.get(a[0]);
                case "setAttribute": attrs.put((String) a[0], a[1]); return null;
                case "removeAttribute": attrs.remove(a[0]); return null;
                default: return null;
            }
        });
    }

    private static Map<String, Object> sessaoDeOperador(String hash) {
        Map<String, Object> s = new HashMap<>();
        s.put("adminLogado", true);
        s.put("idPlanilhaAtiva", "1");
        s.put("origemLogin", "FUNC");
        s.put("credId", 7L);
        s.put("credFp", Impressao.de(hash));
        return s;
    }

    private static LocalDate hoje() { return LocalDate.now(ZoneId.of("America/Recife")); }

    @Test void visitanteSemLoginNaoConsultaOBanco() {
        BancoFalso db = new BancoFalso();
        assertEquals(ValidadorSessao.Resultado.OK, new ValidadorSessao(db).verificar(sessao(new HashMap<>())));
        assertEquals(0, db.consultas);
    }

    @Test void operadorNormalContinuaEConsultaUmaVezPorJanela() {
        BancoFalso db = new BancoFalso();
        db.linhas.add(linha("ATIVO", hoje(), true, "h1"));
        Map<String, Object> s = sessaoDeOperador("h1");
        ValidadorSessao v = new ValidadorSessao(db);
        assertEquals(ValidadorSessao.Resultado.OK, v.verificar(sessao(s)));
        assertEquals(ValidadorSessao.Resultado.OK, v.verificar(sessao(s)));
        assertEquals(1, db.consultas, "a segunda chamada usa a conferência recente");
        assertNull(s.get("bloqueadoPagamento"));
    }

    @Test void operadorDesativadoTemASessaoEncerrada() {
        BancoFalso db = new BancoFalso();
        db.linhas.add(linha("ATIVO", hoje(), false, "h1"));
        assertEquals(ValidadorSessao.Resultado.ENCERRAR, new ValidadorSessao(db).verificar(sessao(sessaoDeOperador("h1"))));
    }

    @Test void operadorApagadoTemASessaoEncerrada() {
        BancoFalso db = new BancoFalso(); // nenhuma linha
        assertEquals(ValidadorSessao.Resultado.ENCERRAR, new ValidadorSessao(db).verificar(sessao(sessaoDeOperador("h1"))));
    }

    @Test void senhaTrocadaEncerraAsSessoesAntigas() {
        BancoFalso db = new BancoFalso();
        db.linhas.add(linha("ATIVO", hoje(), true, "hash-novo"));
        assertEquals(ValidadorSessao.Resultado.ENCERRAR, new ValidadorSessao(db).verificar(sessao(sessaoDeOperador("hash-antigo"))));
    }

    @Test void trialVencidoBloqueiaESegueSemDerrubar() {
        BancoFalso db = new BancoFalso();
        db.linhas.add(linha("TRIAL", hoje().minusDays(30), true, "h1"));
        Map<String, Object> s = sessaoDeOperador("h1");
        assertEquals(ValidadorSessao.Resultado.OK, new ValidadorSessao(db).verificar(sessao(s)));
        assertEquals(true, s.get("bloqueadoPagamento"));
    }

    @Test void armazemBloqueadoELiberadoSemPrecisarSairEEntrar() {
        BancoFalso db = new BancoFalso();
        db.linhas.add(linha("BLOQUEADO", hoje(), true, "h1"));
        Map<String, Object> s = sessaoDeOperador("h1");
        ValidadorSessao v = new ValidadorSessao(db);
        v.verificar(sessao(s));
        assertEquals(true, s.get("bloqueadoPagamento"));

        db.linhas.clear();
        db.linhas.add(linha("ATIVO", hoje(), true, "h1"));
        s.remove("validadoEm"); // passou a janela de conferência
        v.verificar(sessao(s));
        assertNull(s.get("bloqueadoPagamento"), "master reativou: o bloqueio some sozinho");
        assertEquals("ATIVO", s.get("statusAssinatura"));
    }

    @Test void falhaDoBancoNaoDerrubaNinguem() {
        BancoFalso db = new BancoFalso();
        db.cai = true;
        Map<String, Object> s = sessaoDeOperador("h1");
        assertEquals(ValidadorSessao.Resultado.OK, new ValidadorSessao(db).verificar(sessao(s)));
        assertNull(s.get("validadoEm"), "sem conferência válida: a próxima requisição tenta de novo");
    }

    @Test void donoSemArmazemEscolhidoTambemEConferido() {
        BancoFalso db = new BancoFalso();
        Map<String, Object> linha = new HashMap<>();
        linha.put("ativo", false);
        linha.put("senha_hash", "h1");
        db.linhas.add(linha);
        Map<String, Object> s = new HashMap<>();
        s.put("parceiroId", 3L);
        s.put("credFp", Impressao.de("h1"));
        assertEquals(ValidadorSessao.Resultado.ENCERRAR, new ValidadorSessao(db).verificar(sessao(s)));
    }

    @Test void planoTrocadoPeloMasterChegaNaSessaoSemSairEEntrar() {
        BancoFalso db = new BancoFalso();
        Map<String, Object> linha = linha("ATIVO", hoje(), true, "h1");
        linha.put("plano", "PRO");
        db.linhas.add(linha);
        Map<String, Object> s = sessaoDeOperador("h1");
        s.put("planoAssinatura", "START");
        assertEquals(ValidadorSessao.Resultado.OK, new ValidadorSessao(db).verificar(sessao(s)));
        assertEquals("PRO", s.get("planoAssinatura"));
    }

    @Test void semPlanoNoBancoMantemOQueJaEstavaNaSessao() {
        BancoFalso db = new BancoFalso();
        db.linhas.add(linha("ATIVO", hoje(), true, "h1"));
        Map<String, Object> s = sessaoDeOperador("h1");
        s.put("planoAssinatura", "PRO");
        new ValidadorSessao(db).verificar(sessao(s));
        assertEquals("PRO", s.get("planoAssinatura"));
    }
}
