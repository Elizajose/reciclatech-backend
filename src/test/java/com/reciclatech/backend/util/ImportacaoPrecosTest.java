package com.reciclatech.backend.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 31: importação de preços por planilha (.xlsx gerado pelo próprio sistema e .csv). */
class ImportacaoPrecosTest {

    private static final BigDecimal MAX = new BigDecimal("100000");
    private static final List<ImportacaoPrecos.Atual> ATUAIS = List.of(
            new ImportacaoPrecos.Atual(1L, "Latinhas", "KG", new BigDecimal("4.50")),
            new ImportacaoPrecos.Atual(2L, "Litro Pitú", "UN", new BigDecimal("0.65")),
            new ImportacaoPrecos.Atual(3L, "Cobre", "KG", new BigDecimal("10.00")),
            new ImportacaoPrecos.Atual(4L, "Ferro", "KG", new BigDecimal("0.90")));

    private static ImportacaoPrecos.Item item(ImportacaoPrecos.Previa p, String nome) {
        return p.itens.stream().filter(i -> ImportacaoPrecos.chave(i.material).equals(ImportacaoPrecos.chave(nome))).findFirst().orElse(null);
    }

    @Test void precoEmVariosFormatos() {
        assertEquals(new BigDecimal("4.50"), ImportacaoPrecos.preco("4,5"));
        assertEquals(new BigDecimal("4.50"), ImportacaoPrecos.preco("4.5"));
        assertEquals(new BigDecimal("1234.50"), ImportacaoPrecos.preco("R$ 1.234,50"));
        assertEquals(new BigDecimal("0.70"), ImportacaoPrecos.preco("0.699999999999"));
        assertNull(ImportacaoPrecos.preco("abc"));
        assertNull(ImportacaoPrecos.preco("1.234.567"));
        assertNull(ImportacaoPrecos.preco(""));
    }

    @Test void unidades() {
        assertEquals("KG", ImportacaoPrecos.unidade("kg"));
        assertEquals("KG", ImportacaoPrecos.unidade(""));
        assertEquals("KG", ImportacaoPrecos.unidade("Quilo"));
        assertEquals("UN", ImportacaoPrecos.unidade("Unidade"));
        assertEquals("UN", ImportacaoPrecos.unidade("und."));
        assertNull(ImportacaoPrecos.unidade("litro"));
    }

    @Test void modeloGeradoVoltaIgualNaLeitura() {
        byte[] xlsx = PlanilhaModelo.gerar("Teste & Cia", List.of(
                new PlanilhaModelo.Linha("Latinhas", "KG", new BigDecimal("4.50")),
                new PlanilhaModelo.Linha("Litro Pitú", "UN", new BigDecimal("0.65"))));
        List<List<String>> t = PlanilhaLeitura.ler(xlsx);
        assertEquals("Material", t.get(0).get(0));
        assertEquals("Litro Pitú", t.get(2).get(0));
        ImportacaoPrecos.Previa p = ImportacaoPrecos.montar(t, ATUAIS, MAX);
        assertEquals(2, p.iguais);
        assertFalse(p.temMudanca());
        assertEquals(2, p.foraDaPlanilha);
    }

    @Test void csvDoExcelEmPortugues() {
        byte[] csv = "Material;Unidade;Preço\r\nlatinhas;kg;4,80\r\nPapelão;KG;0,55\r\nCobre;KG;40\r\n;;\r\nLitro Pitú;KG;0,70\r\n"
                .getBytes(Charset.forName("windows-1252"));
        ImportacaoPrecos.Previa p = ImportacaoPrecos.montar(PlanilhaLeitura.ler(csv), ATUAIS, MAX);
        assertEquals(ImportacaoPrecos.Tipo.ALTERAR, item(p, "Latinhas").tipo);
        assertFalse(item(p, "Latinhas").variacaoGrande);
        assertEquals(ImportacaoPrecos.Tipo.NOVO, item(p, "Papelão").tipo);
        assertTrue(item(p, "Cobre").variacaoGrande, "10 -> 40 é variação grande");
        assertEquals(ImportacaoPrecos.Tipo.ERRO, item(p, "Litro Pitú").tipo, "não troca a unidade (UN -> KG)");
        assertEquals(1, p.novos);
        assertEquals(2, p.alterados);
        assertEquals(1, p.erros);
        assertEquals(1, p.variacoesGrandes);
    }

    @Test void semTituloUsaColunasABC() {
        byte[] csv = "Latinhas,KG,5.00\nCobre,KG,11\n".getBytes(StandardCharsets.UTF_8);
        ImportacaoPrecos.Previa p = ImportacaoPrecos.montar(PlanilhaLeitura.ler(csv), ATUAIS, MAX);
        assertEquals(2, p.alterados);
    }

    @Test void errosPorLinhaNaoDerrubamAsOutras() {
        byte[] csv = ("Material;Unidade;Preço\nX;KG;-1\nY;litro;2\n=CMD();KG;1\nLatinhas;KG;4,60\nLatinhas;KG;9\nZ;KG;999999\nW;KG;\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportacaoPrecos.Previa p = ImportacaoPrecos.montar(PlanilhaLeitura.ler(csv), ATUAIS, MAX);
        assertEquals(1, p.alterados);
        assertEquals(6, p.erros);
        assertEquals(5, p.itens.get(3).linha, "o número da linha é o mesmo que o Excel mostra");
    }

    @Test void recusaArquivosQueNaoSaoPlanilha() {
        assertThrows(IllegalArgumentException.class, () -> PlanilhaLeitura.ler(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> PlanilhaLeitura.ler("%PDF-1.4".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IllegalArgumentException.class, () -> PlanilhaLeitura.ler(new byte[PlanilhaLeitura.TAMANHO_MAX + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> ImportacaoPrecos.montar(PlanilhaLeitura.ler("Material;Unidade;Preço\n".getBytes(StandardCharsets.UTF_8)), ATUAIS, MAX));
    }

    @Test void xlsxComDoctypeEhRecusado() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            z.write(("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                    + "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>&e;</t></is></c></row></sheetData></worksheet>")
                    .getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> PlanilhaLeitura.ler(b.toByteArray()));
    }

    @Test void xlsxComTextosCompartilhadosEPrefixo() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            z.write("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><si><t>Material</t></si><si><r><t>Pre</t></r><r><t>ço</t></r></si><si><t>Cobre</t></si></sst>"
                    .getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            z.write(("<x:worksheet xmlns:x=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><x:sheetData>"
                    + "<x:row r=\"1\"><x:c r=\"A1\" t=\"s\"><x:v>0</x:v></x:c><x:c r=\"C1\" t=\"s\"><x:v>1</x:v></x:c></x:row>"
                    + "<x:row r=\"3\"><x:c r=\"A3\" t=\"s\"><x:v>2</x:v></x:c><x:c r=\"C3\"><x:v>12.5</x:v></x:c></x:row>"
                    + "</x:sheetData></x:worksheet>").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        List<List<String>> t = PlanilhaLeitura.ler(b.toByteArray());
        assertEquals(List.of("Material", "", "Preço"), t.get(0));
        assertEquals(0, t.get(1).size(), "linha 2 vazia é mantida para a numeração bater");
        ImportacaoPrecos.Previa p = ImportacaoPrecos.montar(t, ATUAIS, MAX);
        assertEquals(ImportacaoPrecos.Tipo.ALTERAR, item(p, "Cobre").tipo);
        assertEquals(3, item(p, "Cobre").linha);
        assertEquals(new BigDecimal("12.50"), item(p, "Cobre").precoNovo);
    }
}
