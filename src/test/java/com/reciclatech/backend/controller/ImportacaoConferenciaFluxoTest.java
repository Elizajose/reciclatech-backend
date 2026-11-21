package com.reciclatech.backend.controller;

import com.reciclatech.backend.service.DatabaseService;
import com.reciclatech.backend.util.ImportacaoPrecos;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 32: conferência editável da planilha, do envio à gravação, sem Spring e sem banco.
 * O caso real: "Litro Dreher" com unidade em branco não pode virar KG; o dono corrige na tela.
 */
class ImportacaoConferenciaFluxoTest {

    private final Map<String, Object> sessao = new HashMap<>();
    private final Map<String, Object> modelo = new HashMap<>();
    private final Map<String, Object> flash = new HashMap<>();
    private final List<String[]> gravadosNovos = new ArrayList<>();
    private final List<Object[]> gravadasAlteracoes = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> c, java.util.function.BiFunction<String, Object[], Object> f) {
        return (T) Proxy.newProxyInstance(c.getClassLoader(), new Class<?>[]{c}, (p, m, a) -> {
            Object r = f.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == null && m.getReturnType() == boolean.class) return false;
            if (r == null && m.getReturnType() == long.class) return 0L;
            return r;
        });
    }

    private TelaController controller() throws Exception {
        sessao.put("adminLogado", true);
        sessao.put("perfilUser", "GESTOR");
        sessao.put("idPlanilhaAtiva", "1");
        DatabaseService db = new DatabaseService() {
            @Override public List<ImportacaoPrecos.Atual> materiaisParaImportacao() {
                return List.of(new ImportacaoPrecos.Atual(1L, "Latinhas", "KG", new BigDecimal("4.50")));
            }
            @Override public int[] aplicarImportacaoPrecos(List<String[]> novos, List<Object[]> alteracoes) {
                gravadosNovos.addAll(novos); gravadasAlteracoes.addAll(alteracoes);
                return new int[]{novos.size(), alteracoes.size()};
            }
        };
        TelaController t = new TelaController();
        Field f = TelaController.class.getDeclaredField("googleSheetsService");
        f.setAccessible(true);
        f.set(t, db);
        return t;
    }

    private HttpSession sessao() {
        return proxy(HttpSession.class, (n, a) -> switch (n) {
            case "getAttribute" -> sessao.get((String) a[0]);
            case "setAttribute" -> { sessao.put((String) a[0], a[1]); yield null; }
            case "removeAttribute" -> { sessao.remove((String) a[0]); yield null; }
            default -> null;
        });
    }

    private Model modelo() {
        return proxy(Model.class, (n, a) -> { if (n.equals("addAttribute") && a.length == 2) modelo.put((String) a[0], a[1]); return null; });
    }

    private RedirectAttributes ra() {
        return proxy(RedirectAttributes.class, (n, a) -> { if (n.equals("addFlashAttribute") && a.length == 2) flash.put((String) a[0], a[1]); return null; });
    }

    private static MultipartFile arquivo(String csv) {
        byte[] b = csv.getBytes(StandardCharsets.UTF_8);
        return proxy(MultipartFile.class, (n, a) -> switch (n) {
            case "isEmpty" -> b.length == 0;
            case "getSize" -> (long) b.length;
            case "getBytes" -> b;
            case "getOriginalFilename" -> "precos.csv";
            default -> null;
        });
    }

    private ImportacaoPrecos.Previa previa() { return (ImportacaoPrecos.Previa) modelo.get("previa"); }

    @Test void unidadeEmBrancoCorrigidaNaTelaGravaComoUN() throws Exception {
        TelaController t = controller();
        String v = t.conferirImportacao(arquivo("Material;Unidade;Preço\nLitro Dreher;;0,10\nLatinhas;KG;4,80\n"), sessao(), modelo(), ra());
        assertEquals("admin-precos-importar", v);
        assertEquals(1, previa().erros, "Litro Dreher sem unidade é erro, não KG");
        assertEquals(1, previa().alterados);
        String token = (String) modelo.get("token");

        // o dono escolhe UN e clica direto em Confirmar: o sistema mostra a conferência nova antes de gravar
        modelo.clear();
        v = t.confirmarImportacao(token, "confirmar", null, List.of("2", "3"), List.of("Litro Dreher", "Latinhas"),
                List.of("UN", "KG"), List.of("0,10", "4,80"), List.of("2", "3"), sessao(), modelo(), ra());
        assertEquals("admin-precos-importar", v);
        assertTrue(String.valueOf(modelo.get("avisoConferencia")).contains("mudou"));
        assertTrue(gravadosNovos.isEmpty(), "nada gravado ainda");
        assertEquals(1, previa().novos);

        // confirma de novo, sem mexer: grava
        v = t.confirmarImportacao(token, "confirmar", null, List.of("2", "3"), List.of("Litro Dreher", "Latinhas"),
                List.of("UN", "KG"), List.of("0,10", "4,80"), List.of("2", "3"), sessao(), modelo(), ra());
        assertEquals("redirect:/admin/precos", v);
        assertEquals(1, gravadosNovos.size());
        assertEquals(List.of("Litro Dreher", "UN", "0.10"), Arrays.asList(gravadosNovos.get(0)));
        assertEquals(1, gravadasAlteracoes.size());
        assertNull(sessao.get("importacaoPrecos"), "token usado não serve de novo");
    }

    @Test void confirmarSemMexerGravaSoOQueEstaCertoEDesmarcadaFicaDeFora() throws Exception {
        TelaController t = controller();
        t.conferirImportacao(arquivo("Material;Unidade;Preço\nLitro Dreher;;0,10\nLatinhas;KG;4,8\nOuro;KG;9\n"), sessao(), modelo(), ra());
        String token = (String) modelo.get("token");
        // tela exibe os valores normalizados (4,80); "Ouro" desmarcado na primeira revisão
        modelo.clear();
        t.confirmarImportacao(token, "revisar", null, List.of("2", "3", "4"), List.of("Litro Dreher", "Latinhas", "Ouro"),
                List.of("", "KG", "KG"), List.of("0,10", "4,80", "9,00"), List.of("2", "3"), sessao(), modelo(), ra());
        assertEquals(1, previa().ignoradas);
        assertEquals(0, previa().novos);
        String v = t.confirmarImportacao(token, "confirmar", null, List.of("2", "3", "4"), List.of("Litro Dreher", "Latinhas", "Ouro"),
                List.of("", "KG", "KG"), List.of("0,10", "4,80", "9,00"), List.of("2", "3"), sessao(), modelo(), ra());
        assertEquals("redirect:/admin/precos", v);
        assertTrue(gravadosNovos.isEmpty(), "Dreher (erro) e Ouro (desmarcado) não entram");
        assertEquals(1, gravadasAlteracoes.size());
    }

    @Test void tokenErradoOuDeOutroArmazemNaoGrava() throws Exception {
        TelaController t = controller();
        t.conferirImportacao(arquivo("Material;Unidade;Preço\nLatinhas;KG;4,80\n"), sessao(), modelo(), ra());
        String token = (String) modelo.get("token");
        String v = t.confirmarImportacao("outro", "confirmar", null, null, null, null, null, null, sessao(), modelo(), ra());
        assertEquals("redirect:/admin/precos", v);
        assertTrue(gravadasAlteracoes.isEmpty());

        t.conferirImportacao(arquivo("Material;Unidade;Preço\nLatinhas;KG;4,80\n"), sessao(), modelo(), ra());
        token = (String) modelo.get("token");
        sessao.put("idPlanilhaAtiva", "2");   // trocou de armazém no meio
        v = t.confirmarImportacao(token, "confirmar", null, List.of("2"), List.of("Latinhas"), List.of("KG"), List.of("4,80"), List.of("2"), sessao(), modelo(), ra());
        assertEquals("redirect:/admin/precos", v);
        assertTrue(gravadasAlteracoes.isEmpty());
    }

    @Test void linhaInventadaNaTelaEIgnorada() throws Exception {
        TelaController t = controller();
        t.conferirImportacao(arquivo("Material;Unidade;Preço\nLatinhas;KG;4,80\n"), sessao(), modelo(), ra());
        String token = (String) modelo.get("token");
        t.confirmarImportacao(token, "revisar", null, List.of("2", "99"), List.of("Latinhas", "Hack"), List.of("KG", "KG"),
                List.of("4,80", "1"), List.of("2", "99"), sessao(), modelo(), ra());
        assertEquals(1, previa().itens.size());
    }
}
