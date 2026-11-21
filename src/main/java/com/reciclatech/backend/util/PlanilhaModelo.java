package com.reciclatech.backend.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fase 31: gera a planilha .xlsx de preços (que também é o modelo para importar), só com o Java.
 * Aba 1 "Preços": Material | Unidade | Preço (R$), com a lista atual; Unidade com lista KG/UN.
 * Aba 2 "Como usar": instruções curtas. Textos vão como texto puro (nunca como fórmula).
 */
public final class PlanilhaModelo {

    public record Linha(String material, String unidade, BigDecimal preco) {}

    private PlanilhaModelo() {}

    public static byte[] gerar(String nomeArmazem, List<Linha> linhas) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""");
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="Preços" sheetId="1" r:id="rId1"/><sheet name="Como usar" sheetId="2" r:id="rId2"/></sheets>
                    </workbook>""");
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""");
            // estilos: 0 normal, 1 título (negrito, fundo verde claro), 2 dinheiro #,##0.00
            put(zip, "xl/styles.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                    <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
                    <fill><patternFill patternType="solid"><fgColor rgb="FFD1E7DD"/><bgColor indexed="64"/></patternFill></fill></fills>
                    <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                    <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                    <cellXfs count="3">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                    <xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                    </cellXfs>
                    <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                    </styleSheet>""");

            StringBuilder dados = new StringBuilder();
            dados.append("<row r=\"1\">").append(texto("A1", "Material", 1)).append(texto("B1", "Unidade", 1))
                 .append(texto("C1", "Preço (R$)", 1)).append("</row>");
            int r = 2;
            for (Linha l : linhas) {
                dados.append("<row r=\"").append(r).append("\">")
                     .append(texto("A" + r, l.material(), 0))
                     .append(texto("B" + r, l.unidade() == null ? "KG" : l.unidade(), 0));
                if (l.preco() != null) {
                    dados.append("<c r=\"C").append(r).append("\" s=\"2\"><v>").append(l.preco().toPlainString()).append("</v></c>");
                }
                dados.append("</row>");
                r++;
            }
            put(zip, "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"
                    + "<cols><col min=\"1\" max=\"1\" width=\"38\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"12\" customWidth=\"1\"/>"
                    + "<col min=\"3\" max=\"3\" width=\"14\" customWidth=\"1\"/></cols>"
                    + "<sheetData>" + dados + "</sheetData>"
                    + "<dataValidations count=\"1\"><dataValidation type=\"list\" allowBlank=\"1\" showErrorMessage=\"1\" "
                    + "errorTitle=\"Unidade\" error=\"Use KG ou UN\" sqref=\"B2:B600\"><formula1>\"KG,UN\"</formula1></dataValidation></dataValidations>"
                    + "</worksheet>");

            String[] ajuda = {
                    "Como atualizar os preços pelo Excel",
                    "1. Na aba \"Preços\", mude os valores da coluna C (ex.: 4,50) e salve como .xlsx.",
                    "2. Para um material novo, escreva o nome na coluna A, KG ou UN na coluna B e o preço na coluna C.",
                    "3. No Coletaê, em Tabela de Preços > Importar planilha, envie o arquivo.",
                    "4. Confira a lista do que vai mudar e confirme. Nada é gravado antes da sua confirmação.",
                    "",
                    "Regras:",
                    "- A planilha nunca apaga materiais. Materiais que não estiverem nela continuam iguais.",
                    "- A unidade de um material já cadastrado não é trocada pela planilha.",
                    "- Preço que mais que dobra ou cai pela metade pede confirmação.",
                    "- Até 500 materiais e 1 MB por arquivo. Não use PDF.",
                    "",
                    nomeArmazem == null ? "" : "Armazém: " + nomeArmazem
            };
            StringBuilder ajudaXml = new StringBuilder();
            for (int i = 0; i < ajuda.length; i++) {
                ajudaXml.append("<row r=\"").append(i + 1).append("\">").append(texto("A" + (i + 1), ajuda[i], i == 0 ? 1 : 0)).append("</row>");
            }
            put(zip, "xl/worksheets/sheet2.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<cols><col min=\"1\" max=\"1\" width=\"110\" customWidth=\"1\"/></cols>"
                    + "<sheetData>" + ajudaXml + "</sheetData></worksheet>");
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gerar a planilha.", e);
        }
    }

    private static String texto(String ref, String valor, int estilo) {
        String v = valor == null ? "" : valor;
        return "<c r=\"" + ref + "\" t=\"inlineStr\"" + (estilo > 0 ? " s=\"" + estilo + "\"" : "") + "><is><t xml:space=\"preserve\">"
                + escapar(v) + "</t></is></c>";
    }

    static String escapar(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> { if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') sb.append(c); }
            }
        }
        return sb.toString();
    }

    private static void put(ZipOutputStream zip, String nome, String conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
