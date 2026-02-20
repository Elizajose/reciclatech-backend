package com.reciclatech.backend.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.reciclatech.backend.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleSheetsService {

    private final Sheets sheetsService;

    @Value("${google.sheets.id}")
    private String spreadsheetId;

    public GoogleSheetsService(Sheets sheetsService) {
        this.sheetsService = sheetsService;
    }

    // Método para salvar o Usuário (Agendamento da tela inicial)
    public void salvarUsuario(Usuario usuario) throws IOException {
        // Aciona o @PrePersist manualmente para garantir data e status
        usuario.prePersist();

        // Prepara os dados para as colunas: ID, Nome, Telefone, CPF, Email, Endereco, Data, Status, Tipo
        List<Object> row = Arrays.asList(
                System.currentTimeMillis(), // Gera um ID baseado no tempo atual
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

        // Grava na aba "Usuarios" na próxima linha vazia
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Usuarios!A1", body)
                .setValueInputOption("RAW")
                .execute();
    }
    // Adicione este método dentro do seu GoogleSheetsService.java
    public List<Usuario> listarUsuarios() throws IOException {
        // Busca todos os dados da aba "Usuarios"
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Usuarios!A2:I") // Pula a primeira linha (cabeçalho)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        // Transforma as linhas da planilha de volta em objetos Usuario
        return values.stream().map(row -> {
            Usuario u = new Usuario();
            u.setId(Long.parseLong(row.get(0).toString()));
            u.setNome(row.get(1).toString());
            u.setTelefone(row.get(2).toString());
            // ... você pode mapear os outros campos aqui se precisar exibir na tela
            return u;
        }).toList();
    }
    // --- MÉTODOS PARA GESTÃO DE MATERIAIS ---

    public List<Material> listarMateriais() throws IOException {
        // Busca os dados na aba "Materiais"
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
            m.setPrecoPorKg(new java.math.BigDecimal(row.get(2).toString().replace(",", ".")));
            m.setUnidade(row.size() > 3 ? row.get(3).toString() : "KG");
            return m;
        }).toList();
    }

    public void atualizarPrecoMaterial(Long id, java.math.BigDecimal novoPreco) throws IOException {
        // 1. Primeiro localizamos a linha correta baseada no ID
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Materiais!A:A")
                .execute();

        List<List<Object>> values = response.getValues();
        int rowIndex = -1;

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (!values.get(i).isEmpty() && values.get(i).get(0).toString().equals(id.toString())) {
                    rowIndex = i + 1; // +1 porque a planilha começa em 1
                    break;
                }
            }
        }

        if (rowIndex != -1) {
            // 2. Atualiza apenas a célula de preço (Coluna C)
            List<Object> row = Collections.singletonList(novoPreco.toString());
            ValueRange body = new ValueRange().setValues(Collections.singletonList(row));

            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, "Materiais!C" + rowIndex, body)
                    .setValueInputOption("RAW")
                    .execute();
        }
        // Adicione no GoogleSheetsService.java

        public void salvarMaterial(Material material) throws IOException {
            List<Object> row = Arrays.asList(
                    System.currentTimeMillis(), // ID simples
                    material.getNome(),
                    material.getPrecoPorKg().toString(),
                    material.getUnidade()
            );

            ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, "Materiais!A1", body)
                    .setValueInputOption("RAW")
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
                com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest content = new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest();
                // Lógica para deletar a linha via API do Google (um pouco mais complexa, mas necessária)
                // Por enquanto, podemos apenas limpar os dados daquela linha
                sheetsService.spreadsheets().values()
                        .clear(spreadsheetId, "Materiais!A" + (rowIndex + 1) + ":D" + (rowIndex + 1), new com.google.api.services.sheets.v4.model.ClearValuesRequest())
                        .execute();
            }
        }
    }
    // Dentro de GoogleSheetsConfig.java

    @Bean
    public Sheets googleSheetsService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        // Agora ele busca o caminho que definimos na variável (seja local ou no Render)
        Resource resource = resourceLoader.getResource(credentialsPath.startsWith("/") ? "file:" + credentialsPath : "classpath:" + credentialsPath);

        GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream())
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
    // Adicione no seu GoogleSheetsService.java

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        // 1. Busca todas as ofertas para ver quem está pendente
        ValueRange responseOfertas = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!A2:J")
                .execute();

        List<List<Object>> ofertasValues = responseOfertas.getValues();
        if (ofertasValues == null) return java.util.Collections.emptyList();

        // Filtra os IDs (telefones) de usuários que têm status "DISPONIVEL"
        java.util.Set<String> telefonesPendentes = ofertasValues.stream()
                .filter(row -> row.size() > 8 && "DISPONIVEL".equals(row.get(8).toString()))
                .map(row -> row.get(7).toString()) // Coluna H: Usuario_ID (Telefone)
                .collect(java.util.stream.Collectors.toSet());

        // 2. Busca todos os usuários e filtra apenas os que estão pendentes
        return listarUsuarios().stream()
                .filter(u -> telefonesPendentes.contains(u.getTelefone()))
                .toList();
    }
    // Adicione no seu GoogleSheetsService.java

    public Material buscarMaterialPorId(Long id) throws IOException {
        return listarMateriais().stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Material não encontrado na planilha"));
    }

    public Double calcularTotalReciclado() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!C2:I") // Coluna C é o Peso, I é o Status
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return 0.0;

        return values.stream()
                .filter(row -> row.size() > 6 && "VENDIDO".equals(row.get(6).toString()))
                .mapToDouble(row -> Double.parseDouble(row.get(0).toString().replace(",", ".")))
                .sum();
    }
    // Adicione no GoogleSheetsService.java

    public List<TelaController.RankingDTO> buscarRankingMateriais() throws IOException {
        // Busca as colunas de Material (B), Peso (C) e Status (I) na aba Ofertas
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!B2:I")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null) return new java.util.ArrayList<>();

        // Agrupa e soma os pesos por material
        java.util.Map<String, Double> somaPorMaterial = new java.util.HashMap<>();

        for (List<Object> row : values) {
            if (row.size() > 7 && "VENDIDO".equals(row.get(7).toString())) {
                String nome = row.get(0).toString();
                Double peso = Double.parseDouble(row.get(1).toString().replace(",", "."));
                somaPorMaterial.put(nome, somaPorMaterial.getOrDefault(nome, 0.0) + peso);
            }
        }

        // Busca as unidades dos materiais para o DTO
        List<Material> todosMateriais = listarMateriais();

        // Transforma o mapa em uma lista de RankingDTO e pega os 3 maiores
        return somaPorMaterial.entrySet().stream()
                .map(entry -> {
                    String unidade = todosMateriais.stream()
                            .filter(m -> m.getNome().equalsIgnoreCase(entry.getKey()))
                            .map(Material::getUnidade)
                            .findFirst().orElse("kg");
                    return new TelaController.RankingDTO(entry.getKey(), entry.getValue(), unidade);
                })
                .sorted((a, b) -> b.peso.compareTo(a.peso)) // Ordem decrescente
                .limit(3)
                .collect(java.util.stream.Collectors.toList());
    }
}