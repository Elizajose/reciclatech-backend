package com.reciclatech.backend.config;

import com.reciclatech.backend.service.ValidadorSessao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** O plano Básico (START) não abre recursos Pro nem digitando o endereço; Pro e período de teste abrem. */
class FiltroPlanoTest {

    private String redirecionou;
    private boolean passou;

    private void chamar(String uri, Map<String, Object> sessaoAttrs) throws Exception {
        redirecionou = null;
        passou = false;
        FiltroSeguranca filtro = new FiltroSeguranca();
        Field f = FiltroSeguranca.class.getDeclaredField("validadorSessao");
        f.setAccessible(true);
        f.set(filtro, new ValidadorSessao(null) {
            @Override public Resultado verificar(HttpSession s) { return Resultado.OK; }
        });
        HttpSession sessao = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class},
                (p, m, a) -> m.getName().equals("getAttribute") ? sessaoAttrs.get(a[0]) : null);
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getRequestURI" -> uri;
                    case "getMethod" -> "GET";
                    case "getContextPath" -> "";
                    case "getSession" -> sessao;
                    default -> null;
                });
        HttpServletResponse res = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> { if (m.getName().equals("sendRedirect")) redirecionou = (String) a[0]; return null; });
        FilterChain chain = (rq, rs) -> passou = true;
        filtro.doFilterInternal(req, res, chain);
    }

    private static Map<String, Object> sessao(String plano, String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("adminLogado", true);
        m.put("planoAssinatura", plano);
        m.put("statusAssinatura", status);
        return m;
    }

    @Test void startAtivoEhMandadoParaAssinaturaNosRecursosPro() throws Exception {
        for (String uri : new String[]{"/admin/analises", "/admin/saida", "/admin/ajuste-estoque", "/admin/despesas",
                "/admin/historico", "/admin/fornecedores-vip", "/admin/relatorio-gerencial"}) {
            chamar(uri, sessao("START", "ATIVO"));
            assertEquals("/admin/assinatura?aviso=pro", redirecionou, uri);
            assertFalse(passou, uri);
        }
    }

    @Test void startAtivoUsaNormalmenteOQueEhDoBasico() throws Exception {
        for (String uri : new String[]{"/admin/coletas", "/admin/precos", "/admin/operadores", "/admin/assinatura", "/admin/historico-alteracoes"}) {
            chamar(uri, sessao("START", "ATIVO"));
            assertNull(redirecionou, uri);
            assertTrue(passou, uri);
        }
    }

    @Test void proAbreTudoInclusiveNoTeste() throws Exception {
        chamar("/admin/analises", sessao("PRO", "ATIVO"));
        assertTrue(passou);
        chamar("/admin/saida", sessao("PRO", "TRIAL"));
        assertTrue(passou);
        assertNull(redirecionou);
    }

    @Test void startEmTesteTambemNaoAbreRecursosPro() throws Exception {
        chamar("/admin/saida", sessao("START", "TRIAL"));
        assertEquals("/admin/assinatura?aviso=pro", redirecionou);
        assertFalse(passou);
        chamar("/admin/coletas", sessao("START", "TRIAL"));
        assertTrue(passou);
    }

    @Test void visitanteSemLoginNaoEhRedirecionadoPeloFiltroDePlano() throws Exception {
        Map<String, Object> semLogin = new HashMap<>();
        chamar("/admin/analises", semLogin);
        assertTrue(passou); // quem manda para /login é a própria tela
    }
}
