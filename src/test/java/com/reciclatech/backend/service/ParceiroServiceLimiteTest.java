package com.reciclatech.backend.service;

import com.reciclatech.backend.model.Armazem;
import com.reciclatech.backend.model.Parceiro;
import com.reciclatech.backend.repository.ArmazemRepository;
import com.reciclatech.backend.repository.ParceiroRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Regra comercial: START e PRO têm 1 armazém por dono; vários só no plano Personalizado. */
class ParceiroServiceLimiteTest {

    private final List<Armazem> banco = new ArrayList<>();
    private final List<String[]> outros = new ArrayList<>();   // {documento, idDono} de armazéns de outros donos
    private final Parceiro dono = new Parceiro("Dono", "dono@x.com", "hash");

    private ParceiroService servico() {
        ParceiroRepository pr = (ParceiroRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ParceiroRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "travarPorId", "findById" -> Optional.of(dono);
                    default -> null;
                });
        ArmazemRepository ar = (ArmazemRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ArmazemRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findByProprietarioIdOrderByNomeAsc" -> new ArrayList<>(banco);
                    case "save" -> { banco.add((Armazem) a[0]); yield a[0]; }
                    case "existeDocumentoDeOutroDono" -> {
                        String doc = (String) a[0]; Long donoId = (Long) a[1];
                        yield outros.stream().anyMatch(x -> doc.equals(com.reciclatech.backend.util.DocumentoValidator.normalizar(x[0])) && !donoId.equals(Long.valueOf(x[1])));
                    }
                    default -> null;
                });
        return new ParceiroService(pr, ar, null);
    }

    private Armazem cadastrar(ParceiroService s, String plano) {
        return s.cadastrarArmazem(1L, "Armazém " + (banco.size() + 1), null, "8199999999", "Rua A, 1", "PE", "Recife", plano);
    }

    @Test void primeiroArmazemPodeSerStartOuPro() {
        ParceiroService s = servico();
        assertFalse(s.somentePersonalizado(1L));
        assertEquals("PRO", cadastrar(s, "PRO").getPlano());
    }

    @Test void segundoArmazemEmStartOuProERecusado() {
        ParceiroService s = servico();
        cadastrar(s, "START");
        assertTrue(s.somentePersonalizado(1L));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> cadastrar(s, "PRO"));
        assertTrue(e.getMessage().contains("Personalizado"));
        assertEquals(1, banco.size());
    }

    @Test void quemTemPersonalizadoCadastraVariosJaNoPersonalizado() {
        ParceiroService s = servico();
        Armazem primeiro = cadastrar(s, "PRO");
        primeiro.setPlano("PERSONALIZADO"); // o suporte (master) muda o plano
        assertTrue(s.temPersonalizado(1L));
        assertFalse(s.somentePersonalizado(1L));
        assertEquals("PERSONALIZADO", cadastrar(s, "START").getPlano());
        assertEquals("PERSONALIZADO", cadastrar(s, "PRO").getPlano());
        assertEquals(3, banco.size());
    }

    @Test void escolherPersonalizadoNoFormularioLiberaOSegundoArmazem() {
        ParceiroService s = servico();
        Armazem primeiro = cadastrar(s, "START");
        assertTrue(s.somentePersonalizado(1L));
        Armazem segundo = cadastrar(s, "PERSONALIZADO");
        assertEquals("PERSONALIZADO", segundo.getPlano());
        assertEquals("START", primeiro.getPlano());   // o antigo NÃO muda sozinho: o suporte unifica ao ativar
        assertEquals(2, banco.size());
        assertFalse(s.somentePersonalizado(1L));
        assertEquals("PERSONALIZADO", cadastrar(s, "START").getPlano());   // já é Personalizado: o terceiro entra nele
    }

    @Test void primeiroArmazemPodeJaSerPersonalizado() {
        ParceiroService s = servico();
        assertFalse(s.somentePersonalizado(1L));
        assertEquals("PERSONALIZADO", cadastrar(s, "personalizado").getPlano());
    }

    @Test void planoDesconhecidoVaiParaStart() {
        ParceiroService s = servico();
        assertEquals("START", cadastrar(s, "XYZ").getPlano());
        assertThrows(IllegalArgumentException.class, () -> cadastrar(s, "XYZ"));
    }

    private Armazem comDocumento(ParceiroService s, String doc) {
        return s.cadastrarArmazem(1L, "Armazém " + (banco.size() + 1), doc, "8199999999", "Rua A, 1", "PE", "Recife", "START");
    }

    @Test void mesmoDonoPodeRepetirOProprioDocumentoNoPersonalizado() {
        ParceiroService s = servico();
        Armazem a = comDocumento(s, "11.222.333/0001-81");
        a.setPlano("PERSONALIZADO");
        a.setProprietario(dono);
        assertNotNull(comDocumento(s, "11222333000181"));
        assertEquals(2, banco.size());
    }

    @Test void documentoDeOutroDonoContinuaBarrado() {
        ParceiroService s = servico();
        outros.add(new String[]{"11.222.333/0001-81", "99"});
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> comDocumento(s, "11222333000181"));
        assertTrue(e.getMessage().contains("já está cadastrado"));
        assertEquals(0, banco.size());
    }

    @Test void documentoInvalidoContinuaRecusado() {
        ParceiroService s = servico();
        assertThrows(IllegalArgumentException.class, () -> comDocumento(s, "123"));
    }
}
