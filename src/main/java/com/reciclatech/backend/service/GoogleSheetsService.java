package com.reciclatech.backend.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.controller.TelaController.RankingDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoogleSheetsService {

    private final Sheets sheetsService;

    @Value("${google.sheets.id}")
    private String spreadsheetId;

    public GoogleSheetsService(Sheets sheetsService) {
        this.sheetsService = sheetsService;
    }

    // --- AUXILIARES DE TRATAMENTO (Para evitar Erro 500) ---
    private String getCol(List<Object> row, int index) {
        if (row == null || index >= row.size() || row.get(index) == null) return "";
        return row.get(index).toString().trim();
    }

    private BigDecimal parseMoeda(String valor) {
        try {
            if (valor == null || valor.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(valor.replace(",", "."));
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    // --- USUÁRIOS ---
    public void salvarUsuario(Usuario u) throws IOException {
        u.prePersist();
        List<Object> row = Arrays.asList(
                u.getId() != null ? u.getId() : System.currentTimeMillis(),
                u.getNome(), u.getTelefone(), u.getCpf() != null ? u.getCpf() : "",
                "", u.getEndereco() != null ? u.getEndereco() : "",
                u.getDataColeta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                "AGUARDANDO", "CATADOR"
        );
        sheetsService.spreadsheets().values().append(spreadsheetId, "Usuarios!A1",
                        new ValueRange().setValues(Collections.singletonList(row)))
                .setValueInputOption("USER_ENTERED").execute();
    }

    public List<Usuario> listarUsuarios() throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Usuarios!A2:I").execute();
        List<List<Object>> values = res.getValues();
        if (values == null) return Collections.emptyList();

        return values.stream().map(row -> {
            Usuario u = new Usuario();
            u.setId(Long.parseLong(getCol(row, 0)));
            u.setNome(getCol(row, 1));
            u.setTelefone(getCol(row, 2));
            u.setCpf(getCol(row, 3));
            u.setEndereco(getCol(row, 5)); // Coluna F
            return u;
        }).collect(Collectors.toList());
    }

    // --- MATERIAIS ---
    public List<Material> listarMateriais() throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Materiais!A2:D").execute();
        List<List<Object>> values = res.getValues();
        if (values == null) return Collections.emptyList();

        return values.stream().map(row -> {
            Material m = new Material();
            m.setId(Long.parseLong(getCol(row, 0)));
            m.setNome(getCol(row, 1));
            m.setPrecoPorKg(parseMoeda(getCol(row, 2))); // Coluna C
            m.setUnidade(getCol(row, 3).isEmpty() ? "KG" : getCol(row, 3));
            return m;
        }).collect(Collectors.toList());
    }

    // --- VENDAS ---
    public void registrarVendaFinal(String tel, String mat, Double peso, BigDecimal pr, BigDecimal tot, String cpf) throws IOException {
        List<Object> row = Arrays.asList(
                System.currentTimeMillis(), mat, peso.toString().replace(".", ","),
                "ENTREGA LOCAL", pr.toString().replace(".", ","), tot.toString().replace(".", ","),
                LocalDate.now().toString(), tel, "VENDIDO", cpf != null ? cpf : ""
        );
        sheetsService.spreadsheets().values().append(spreadsheetId, "Ofertas!A1",
                        new ValueRange().setValues(Collections.singletonList(row)))
                .setValueInputOption("USER_ENTERED").execute();
    }

    public List<Oferta> buscarVendasPorUsuario(String telefone) throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!A2:J").execute();
        List<List<Object>> values = res.getValues();
        if (values == null) return Collections.emptyList();

        return values.stream()
                .filter(row -> row.size() > 8 && telefone.equals(getCol(row, 7)) && "VENDIDO".equals(getCol(row, 8)))
                .map(row -> {
                    Oferta o = new Oferta();
                    o.setMaterial(getCol(row, 1));
                    String pStr = getCol(row, 2).replace(",", ".");
                    o.setPeso(pStr.isEmpty() ? 0.0 : Double.parseDouble(pStr));
                    o.setPrecoEstimado(parseMoeda(getCol(row, 5))); // Coluna F (Total)
                    return o;
                }).collect(Collectors.toList());
    }

    // --- MÉTODOS DE SUPORTE ---
    public void salvarSolicitacaoInicial(Usuario u, String end) throws IOException {
        salvarUsuario(u);
        List<Object> row = Arrays.asList(System.currentTimeMillis(), "Solicitação", "0", end, "0", "0",
                LocalDate.now().toString(), u.getTelefone(), "DISPONIVEL", LocalDate.now().toString());
        sheetsService.spreadsheets().values().append(spreadsheetId, "Ofertas!A1",
                        new ValueRange().setValues(Collections.singletonList(row)))
                .setValueInputOption("USER_ENTERED").execute();
    }

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!A2:J").execute();
        List<List<Object>> values = res.getValues();
        if (values == null) return Collections.emptyList();
        LocalDate hoje = LocalDate.now();
        Set<String> tels = values.stream()
                .filter(r -> getCol(r, 8).equals("DISPONIVEL") && getCol(r, 9).equals(hoje.toString()))
                .map(r -> getCol(r, 7)).collect(Collectors.toSet());
        return listarUsuarios().stream().filter(u -> tels.contains(u.getTelefone())).collect(Collectors.toList());
    }

    public Double calcularTotalReciclado() throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!C2:I").execute();
        List<List<Object>> values = res.getValues();
        if (values == null) return 0.0;
        return values.stream().filter(r -> getCol(r, 6).equals("VENDIDO"))
                .mapToDouble(r -> {
                    String s = getCol(r, 0).replace(",", ".");
                    return s.isEmpty() ? 0.0 : Double.parseDouble(s);
                }).sum();
    }

    public List<RankingDTO> buscarRankingMateriais() throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!B2:I").execute();
        List<List<Object>> values = res.getValues();
        if (values == null) return new ArrayList<>();
        Map<String, Double> soma = new HashMap<>();
        for (List<Object> r : values) {
            if (getCol(r, 7).equals("VENDIDO")) {
                String n = getCol(r, 0);
                String pStr = getCol(r, 1).replace(",", ".");
                Double p = pStr.isEmpty() ? 0.0 : Double.parseDouble(pStr);
                soma.put(n, soma.getOrDefault(n, 0.0) + p);
            }
        }
        List<Material> todos = listarMateriais();
        return soma.entrySet().stream().map(e -> {
            String un = todos.stream().filter(m -> m.getNome().equalsIgnoreCase(e.getKey())).map(Material::getUnidade).findFirst().orElse("kg");
            return new RankingDTO(e.getKey(), e.getValue(), un);
        }).sorted((a, b) -> b.peso.compareTo(a.peso)).limit(3).collect(Collectors.toList());
    }

    public Material buscarMaterialPorId(Long id) throws IOException {
        return listarMateriais().stream().filter(m -> m.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Material não encontrado"));
    }

    public void salvarMaterial(Material m) throws IOException {
        String p = String.format("%.2f", m.getPrecoPorKg()).replace(".", ",");
        List<Object> r = Arrays.asList(System.currentTimeMillis(), m.getNome(), p, m.getUnidade());
        sheetsService.spreadsheets().values().append(spreadsheetId, "Materiais!A1", new ValueRange().setValues(Collections.singletonList(r))).setValueInputOption("USER_ENTERED").execute();
    }

    public void atualizarPrecoMaterial(Long id, BigDecimal np) throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Materiais!A:A").execute();
        List<List<Object>> vals = res.getValues();
        int idx = -1;
        if (vals != null) {
            for (int i = 0; i < vals.size(); i++) { if (!vals.get(i).isEmpty() && vals.get(i).get(0).toString().equals(id.toString())) { idx = i + 1; break; } }
        }
        if (idx != -1) {
            String p = String.format("%.2f", np).replace(".", ",");
            sheetsService.spreadsheets().values().update(spreadsheetId, "Materiais!C" + idx, new ValueRange().setValues(Collections.singletonList(Collections.singletonList(p)))).setValueInputOption("USER_ENTERED").execute();
        }
    }

    public void deletarMaterial(Long id) throws IOException {
        ValueRange res = sheetsService.spreadsheets().values().get(spreadsheetId, "Materiais!A:A").execute();
        List<List<Object>> vals = res.getValues();
        int idx = -1;
        if (vals != null) { for (int i = 0; i < vals.size(); i++) { if (!vals.get(i).isEmpty() && vals.get(i).get(0).toString().equals(id.toString())) { idx = i; break; } } }
        if (idx != -1) sheetsService.spreadsheets().values().clear(spreadsheetId, "Materiais!A" + (idx + 1) + ":D" + (idx + 1), new ClearValuesRequest()).execute();
    }
}