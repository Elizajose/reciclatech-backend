package com.reciclatech.backend.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.model.Material; // Adicionado import
import com.reciclatech.backend.controller.TelaController.RankingDTO; // Adicionado import
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

    // --- USUÁRIOS ---

    public void salvarUsuario(Usuario usuario) throws IOException {
        usuario.prePersist();
        List<Object> row = Arrays.asList(
                System.currentTimeMillis(),
                usuario.getNome(),
                usuario.getTelefone(),
                usuario.getCpf() != null ? usuario.getCpf() : "",
                usuario.getEmail() != null ? usuario.getEmail() : "",
                usuario.getEndereco(),
                usuario.getDataColeta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                usuario.getStatus().toString(),
                usuario.getTipo() != null ? usuario.getTipo().toString() : "CATADOR"
        );

        ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Usuarios!A1", body)
                .setValueInputOption("RAW")
                .execute();
    }

    public List<Usuario> listarUsuarios() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Usuarios!A2:I")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        return values.stream().map(row -> {
            Usuario u = new Usuario();
            u.setId(Long.parseLong(row.get(0).toString()));
            u.setNome(row.get(1).toString());
            u.setTelefone(row.get(2).toString());
            return u;
        }).collect(Collectors.toList());
    }

    // --- GESTÃO DE MATERIAIS ---

    public List<Material> listarMateriais() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Materiais!A2:D")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        return values.stream().map(row -> {
            Material m = new Material();
            m.setId(Long.parseLong(row.get(0).toString()));
            m.setNome(row.get(1).toString());
            m.setPrecoPorKg(new BigDecimal(row.get(2).toString().replace(",", ".")));
            m.setUnidade(row.size() > 3 ? row.get(3).toString() : "KG");
            return m;
        }).collect(Collectors.toList());
    }

    public void atualizarPrecoMaterial(Long id, BigDecimal novoPreco) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Materiais!A:A")
                .execute();

        List<List<Object>> values = response.getValues();
        int rowIndex = -1;

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (!values.get(i).isEmpty() && values.get(i).get(0).toString().equals(id.toString())) {
                    rowIndex = i + 1;
                    break;
                }
            }
        }

        if (rowIndex != -1) {
            // Formata para 2 casas decimais com vírgula (ex: 0,50)
            String precoFormatado = String.format("%.2f", novoPreco).replace(".", ",");

            List<Object> row = Collections.singletonList(precoFormatado);
            ValueRange body = new ValueRange().setValues(Collections.singletonList(row));

            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, "Materiais!C" + rowIndex, body)
                    .setValueInputOption("USER_ENTERED") // USER_ENTERED respeita a formatação da célula
                    .execute();
        }
    }

    public void salvarMaterial(Material material) throws IOException {
        // Formata para 2 casas decimais com vírgula (ex: 0,50)
        String precoFormatado = String.format("%.2f", material.getPrecoPorKg()).replace(".", ",");

        List<Object> row = Arrays.asList(
                System.currentTimeMillis(),
                material.getNome(),
                precoFormatado,
                material.getUnidade()
        );

        ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Materiais!A1", body)
                .setValueInputOption("USER_ENTERED") // USER_ENTERED respeita a formatação da célula
                .execute();
    }

    public void deletarMaterial(Long id) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Materiais!A:A")
                .execute();
        List<List<Object>> values = response.getValues();
        int rowIndex = -1;

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (!values.get(i).isEmpty() && values.get(i).get(0).toString().equals(id.toString())) {
                    rowIndex = i;
                    break;
                }
            }
        }

        if (rowIndex != -1) {
            sheetsService.spreadsheets().values()
                    .clear(spreadsheetId, "Materiais!A" + (rowIndex + 1) + ":D" + (rowIndex + 1), new ClearValuesRequest())
                    .execute();
        }
    }

    // --- COLETAS E RANKING ---

    public void salvarSolicitacaoInicial(Usuario usuario, String endereco) throws IOException {
        salvarUsuario(usuario);
        List<Object> rowOferta = Arrays.asList(
                System.currentTimeMillis(), "Solicitação de Coleta", 0.0, endereco, "", "", "0.00", usuario.getTelefone(), "DISPONIVEL", LocalDate.now().toString()
        );
        ValueRange body = new ValueRange().setValues(Collections.singletonList(rowOferta));
        sheetsService.spreadsheets().values().append(spreadsheetId, "Ofertas!A1", body).setValueInputOption("RAW").execute();
    }

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        ValueRange responseOfertas = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!A2:J")
                .execute();

        List<List<Object>> ofertasValues = responseOfertas.getValues();
        if (ofertasValues == null) return Collections.emptyList();

        Set<String> telefonesPendentes = ofertasValues.stream()
                .filter(row -> row.size() > 8 && "DISPONIVEL".equals(row.get(8).toString()))
                .map(row -> row.get(7).toString())
                .collect(Collectors.toSet());

        return listarUsuarios().stream()
                .filter(u -> telefonesPendentes.contains(u.getTelefone()))
                .collect(Collectors.toList());
    }

    public Material buscarMaterialPorId(Long id) throws IOException {
        return listarMateriais().stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Material não encontrado na planilha"));
    }

    public Double calcularTotalReciclado() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!C2:I")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return 0.0;

        return values.stream()
                .filter(row -> row.size() > 6 && "VENDIDO".equals(row.get(6).toString()))
                .mapToDouble(row -> Double.parseDouble(row.get(0).toString().replace(",", ".")))
                .sum();
    }

    public List<RankingDTO> buscarRankingMateriais() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!B2:I")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return new ArrayList<>();

        Map<String, Double> somaPorMaterial = new HashMap<>();
        for (List<Object> row : values) {
            if (row.size() > 7 && "VENDIDO".equals(row.get(7).toString())) {
                String nome = row.get(0).toString();
                Double peso = Double.parseDouble(row.get(1).toString().replace(",", "."));
                somaPorMaterial.put(nome, somaPorMaterial.getOrDefault(nome, 0.0) + peso);
            }
        }

        List<Material> todosMateriais = listarMateriais();

        return somaPorMaterial.entrySet().stream()
                .map(entry -> {
                    String unidade = todosMateriais.stream()
                            .filter(m -> m.getNome().equalsIgnoreCase(entry.getKey()))
                            .map(Material::getUnidade)
                            .findFirst().orElse("kg");
                    return new RankingDTO(entry.getKey(), entry.getValue(), unidade);
                })
                .sorted((a, b) -> b.peso.compareTo(a.peso))
                .limit(3)
                .collect(Collectors.toList());
    }
    // Adicione estes dois métodos no seu GoogleSheetsService.java

    public void registrarVendaFinal(String telefone, String material, Double peso, BigDecimal precoUn, BigDecimal total) throws IOException {
        // Agora salvamos cada informação na sua respectiva coluna (A até I)
        List<Object> row = Arrays.asList(
                System.currentTimeMillis(),             // A: ID
                material,                               // B: Nome real do material
                peso.toString().replace(".", ","),      // C: Peso
                "ENTREGA NO LOCAL",                     // D: Endereco
                precoUn.toString().replace(".", ","),   // E: Preco Unitário
                total.toString().replace(".", ","),     // F: Preco Total do Item
                java.time.LocalDate.now().toString(),   // G: Data
                telefone,                               // H: Usuario_ID (Telefone)
                "VENDIDO"                               // I: Status (ESSENCIAL PARA O EXTRATO)
        );

        ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Ofertas!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    public List<Oferta> buscarVendasPorUsuario(String telefone) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!A2:I")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return Collections.emptyList();

        return values.stream()
                .filter(row -> row.size() > 8 &&
                        telefone.equals(row.get(7).toString()) &&
                        "VENDIDO".equals(row.get(8).toString()))
                .map(row -> {
                    Oferta o = new Oferta();
                    o.setMaterial(row.get(1).toString());
                    o.setPeso(Double.parseDouble(row.get(2).toString().replace(",", ".")));
                    // Busca o Preço Total na Coluna F (índice 5)
                    o.setPrecoEstimado(new BigDecimal(row.get(5).toString().replace(",", ".")));
                    return o;
                }).collect(Collectors.toList());
    }

    // Lógica de Limpeza: Só mostra coletas das últimas 24 horas
    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        ValueRange responseOfertas = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!A2:J")
                .execute();

        List<List<Object>> ofertasValues = responseOfertas.getValues();
        if (ofertasValues == null) return Collections.emptyList();

        LocalDate hoje = LocalDate.now();

        Set<String> telefonesPendentes = ofertasValues.stream()
                .filter(row -> row.size() > 9)
                .filter(row -> "DISPONIVEL".equals(row.get(8).toString()))
                .filter(row -> {
                    // Se a data na coluna J for anterior a hoje, ignoramos (Limpeza automática de 24h)
                    LocalDate dataOferta = LocalDate.parse(row.get(9).toString());
                    return dataOferta.isEqual(hoje);
                })
                .map(row -> row.get(7).toString())
                .collect(Collectors.toSet());

        return listarUsuarios().stream()
                .filter(u -> telefonesPendentes.contains(u.getTelefone()))
                .toList();
    }
}