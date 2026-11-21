package com.reciclatech.backend.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Fase 31: lê a primeira aba de uma planilha .xlsx (Excel, LibreOffice, Google Planilhas) ou um .csv,
 * só com o Java (sem biblioteca nova). Devolve as linhas como textos.
 *
 * Proteções: arquivo até {@link #TAMANHO_MAX} bytes; no .xlsx, no máximo {@link #ENTRADAS_MAX} arquivos internos,
 * cada um até {@link #XML_MAX} bytes descompactados (contra "zip-bomba"); XML sem DOCTYPE/entidades externas (contra XXE);
 * no máximo {@link #LINHAS_MAX} linhas e {@link #COLUNAS_MAX} colunas lidas. Fórmulas não são executadas: vale o último valor salvo.
 */
public final class PlanilhaLeitura {

    public static final int TAMANHO_MAX = 1024 * 1024;           // 1 MB
    static final int XML_MAX = 8 * 1024 * 1024;                  // 8 MB descompactado por arquivo interno
    static final int ENTRADAS_MAX = 300;
    public static final int LINHAS_MAX = 501;               // título + 500 materiais
    static final int COLUNAS_MAX = 12;

    private PlanilhaLeitura() {}

    /** Detecta pelo conteúdo: .xlsx começa com "PK" (zip); o resto é tratado como CSV. */
    public static List<List<String>> ler(byte[] arquivo) {
        if (arquivo == null || arquivo.length == 0) throw new IllegalArgumentException("O arquivo está vazio.");
        if (arquivo.length > TAMANHO_MAX) throw new IllegalArgumentException("O arquivo passa de 1 MB. Envie só a lista de preços.");
        if (arquivo.length >= 2 && arquivo[0] == 'P' && arquivo[1] == 'K') return lerXlsx(arquivo);
        if (arquivo.length >= 8 && (arquivo[0] & 0xFF) == 0xD0 && (arquivo[1] & 0xFF) == 0xCF) {
            throw new IllegalArgumentException("Esse é um arquivo .xls antigo. No Excel, use \"Salvar como\" > Pasta de Trabalho do Excel (.xlsx).");
        }
        if (arquivo.length >= 4 && arquivo[0] == '%' && arquivo[1] == 'P' && arquivo[2] == 'D' && arquivo[3] == 'F') {
            throw new IllegalArgumentException("PDF não é aceito. Envie a planilha em .xlsx ou .csv (baixe o modelo nesta tela).");
        }
        return lerCsv(arquivo);
    }

    // ------------------------------------------------------------------ CSV

    public static List<List<String>> lerCsv(byte[] arquivo) {
        String texto = decodificar(arquivo);
        if (texto.startsWith("﻿")) texto = texto.substring(1);
        if (texto.indexOf('\0') >= 0) throw new IllegalArgumentException("O arquivo não parece ser uma planilha (.xlsx ou .csv).");
        String primeira = texto.lines().findFirst().orElse("");
        char sep = primeira.indexOf(';') >= 0 ? ';' : (primeira.indexOf('\t') >= 0 ? '\t' : ',');
        List<List<String>> linhas = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (aspas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') { campo.append('"'); i++; }
                    else aspas = false;
                } else campo.append(c);
            } else if (c == '"') {
                aspas = true;
            } else if (c == sep) {
                if (atual.size() < COLUNAS_MAX) atual.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') i++;
                if (atual.size() < COLUNAS_MAX) atual.add(campo.toString());
                campo.setLength(0);
                linhas.add(atual);
                atual = new ArrayList<>();
                if (linhas.size() > LINHAS_MAX) throw new IllegalArgumentException("A planilha tem linhas demais (máximo " + (LINHAS_MAX - 1) + " materiais).");
            } else {
                campo.append(c);
            }
        }
        if (campo.length() > 0 || !atual.isEmpty()) {
            if (atual.size() < COLUNAS_MAX) atual.add(campo.toString());
            linhas.add(atual);
        }
        return linhas;
    }

    /** UTF-8 quando válido; senão Windows-1252 (o "CSV" do Excel em português costuma vir assim). */
    static String decodificar(byte[] b) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, Charset.forName("windows-1252"));
        }
    }

    // ------------------------------------------------------------------ XLSX

    public static List<List<String>> lerXlsx(byte[] arquivo) {
        Map<String, byte[]> partes = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(arquivo))) {
            ZipEntry e;
            int entradas = 0;
            while ((e = zip.getNextEntry()) != null) {
                if (++entradas > ENTRADAS_MAX) throw new IllegalArgumentException("Planilha com estrutura inesperada.");
                String nome = e.getName();
                boolean interessa = nome.equals("xl/workbook.xml") || nome.equals("xl/_rels/workbook.xml.rels")
                        || nome.equals("xl/sharedStrings.xml") || (nome.startsWith("xl/worksheets/") && nome.endsWith(".xml"));
                if (interessa && !e.isDirectory()) partes.put(nome, lerLimitado(zip));
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não consegui abrir a planilha. Salve de novo como .xlsx e tente outra vez.");
        }
        String aba = primeiraAba(partes);
        byte[] folha = partes.get(aba);
        if (folha == null) throw new IllegalArgumentException("Não encontrei a primeira aba da planilha.");
        List<String> compartilhados = textosCompartilhados(partes.get("xl/sharedStrings.xml"));
        return lerFolha(folha, compartilhados);
    }

    private static byte[] lerLimitado(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > XML_MAX) throw new IllegalArgumentException("Planilha grande demais depois de aberta. Envie só a lista de preços.");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static Document xml(byte[] bytes) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(true); // aceita "x:row" (prefixo) além de "row"
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Planilha com conteúdo inválido. Salve de novo como .xlsx e tente outra vez.");
        }
    }

    /** Caminho da primeira aba pela ordem do workbook (a aba que aparece primeiro no Excel). */
    static String primeiraAba(Map<String, byte[]> partes) {
        try {
            byte[] wb = partes.get("xl/workbook.xml");
            byte[] rels = partes.get("xl/_rels/workbook.xml.rels");
            if (wb != null && rels != null) {
                NodeList sheets = xml(wb).getElementsByTagNameNS("*", "sheet");
                if (sheets.getLength() > 0) {
                    Element primeira = (Element) sheets.item(0);
                    String rid = primeira.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                    if (rid == null || rid.isEmpty()) rid = primeira.getAttribute("r:id");
                    NodeList rs = xml(rels).getElementsByTagNameNS("*", "Relationship");
                    for (int i = 0; i < rs.getLength(); i++) {
                        Element r = (Element) rs.item(i);
                        if (rid.equals(r.getAttribute("Id"))) {
                            String alvo = r.getAttribute("Target");
                            if (alvo.startsWith("/")) return alvo.substring(1);
                            return "xl/" + alvo;
                        }
                    }
                }
            }
        } catch (IllegalArgumentException ignorado) { /* cai no padrão abaixo */ }
        if (partes.containsKey("xl/worksheets/sheet1.xml")) return "xl/worksheets/sheet1.xml";
        return partes.keySet().stream().filter(k -> k.startsWith("xl/worksheets/")).sorted().findFirst().orElse(null);
    }

    static List<String> textosCompartilhados(byte[] bytes) {
        List<String> lista = new ArrayList<>();
        if (bytes == null) return lista;
        NodeList si = xml(bytes).getElementsByTagNameNS("*", "si");
        for (int i = 0; i < si.getLength(); i++) lista.add(textoDe((Element) si.item(i)));
        return lista;
    }

    /** Junta todos os <t> (texto simples ou com formatação em partes). */
    private static String textoDe(Element el) {
        StringBuilder sb = new StringBuilder();
        NodeList ts = el.getElementsByTagNameNS("*", "t");
        for (int i = 0; i < ts.getLength(); i++) {
            Node t = ts.item(i);
            // ignora o "texto fonético" (<rPh>) que alguns arquivos japoneses/chineses trazem
            if (t.getParentNode() != null && "rPh".equals(t.getParentNode().getLocalName())) continue;
            sb.append(t.getTextContent());
        }
        return sb.toString();
    }

    static List<List<String>> lerFolha(byte[] bytes, List<String> compartilhados) {
        Document d = xml(bytes);
        NodeList rows = d.getElementsByTagNameNS("*", "row");
        List<List<String>> linhas = new ArrayList<>();
        int esperado = 1;
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            int numero = parseIntOu(row.getAttribute("r"), esperado);
            while (esperado < numero && linhas.size() <= LINHAS_MAX) { linhas.add(new ArrayList<>()); esperado++; } // linhas vazias puladas pelo Excel
            if (linhas.size() > LINHAS_MAX) throw new IllegalArgumentException("A planilha tem linhas demais (máximo " + (LINHAS_MAX - 1) + " materiais).");
            String[] valores = new String[COLUNAS_MAX];
            NodeList cs = row.getElementsByTagNameNS("*", "c");
            int proxima = 0;
            for (int j = 0; j < cs.getLength(); j++) {
                Element c = (Element) cs.item(j);
                int col = coluna(c.getAttribute("r"), proxima);
                proxima = col + 1;
                if (col < 0 || col >= COLUNAS_MAX) continue;
                valores[col] = valorDaCelula(c, compartilhados);
            }
            List<String> linha = new ArrayList<>();
            int ultima = -1;
            for (int k = 0; k < COLUNAS_MAX; k++) if (valores[k] != null && !valores[k].isEmpty()) ultima = k;
            for (int k = 0; k <= ultima; k++) linha.add(valores[k] == null ? "" : valores[k]);
            linhas.add(linha);
            esperado = numero + 1;
        }
        return linhas;
    }

    private static String valorDaCelula(Element c, List<String> compartilhados) {
        String tipo = c.getAttribute("t");
        if ("inlineStr".equals(tipo)) {
            NodeList is = c.getElementsByTagNameNS("*", "is");
            return is.getLength() > 0 ? textoDe((Element) is.item(0)) : "";
        }
        NodeList vs = c.getElementsByTagNameNS("*", "v");
        String v = vs.getLength() > 0 ? vs.item(0).getTextContent() : "";
        if ("s".equals(tipo)) {
            int idx = parseIntOu(v.trim(), -1);
            return idx >= 0 && idx < compartilhados.size() ? compartilhados.get(idx) : "";
        }
        if ("b".equals(tipo)) return "1".equals(v.trim()) ? "VERDADEIRO" : "FALSO";
        return v; // número (ponto decimal), "str" (resultado de fórmula) ou erro
    }

    /** "C7" -> 2 (A=0). Sem referência: a próxima coluna. */
    static int coluna(String ref, int padrao) {
        if (ref == null || ref.isEmpty()) return padrao;
        int col = 0, i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(ref.charAt(i)) - 'A' + 1);
            i++;
            if (col > 16384) return -1;
        }
        return i == 0 ? padrao : col - 1;
    }

    private static int parseIntOu(String s, int padrao) {
        try {
            String num = s == null ? "" : s.replaceAll("[^0-9]", "");
            return num.isEmpty() ? padrao : Integer.parseInt(num.length() > 7 ? num.substring(0, 7) : num);
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}
