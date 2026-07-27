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
import org.springframework.scheduling.annotation.Scheduled;

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

    // --- GESTÃO DE USUÁRIOS (Aba: Usuarios) ---

    public void salvarUsuario(Usuario usuario) throws IOException {
        usuario.prePersist();
        List<Object> row = Arrays.asList(
                usuario.getId() != null ? usuario.getId() : System.currentTimeMillis(),
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
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    public List<Usuario> listarUsuarios() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Usuarios!A2:F")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return Collections.emptyList();

        return values.stream().map(row -> {
            Usuario u = new Usuario();
            u.setId(Long.parseLong(row.get(0).toString()));
            u.setNome(row.get(1).toString());
            u.setTelefone(row.get(2).toString());

            // LÊ O CPF (Coluna D - Índice 3)
            if (row.size() > 3 && !row.get(3).toString().trim().isEmpty()) {
                u.setCpf(row.get(3).toString());
            }

            if (row.size() > 5) {
                u.setEndereco(row.get(5).toString());
            }
            return u;
        }).collect(Collectors.toList());
    }

    // --- NOVO METODO: ATUALIZA O CPF DO CLIENTE NO CADASTRO ---
    public void atualizarCpfUsuario(String idVendedor, String cpfFinal) throws IOException {
        if (cpfFinal == null || cpfFinal.trim().isEmpty() || cpfFinal.equals("NÃO INFORMADO")) {
            return; // Se não digitou CPF, não faz nada
        }

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Usuarios!A:C").execute();
        List<List<Object>> values = response.getValues();

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row.size() >= 3) {
                    String idStr = row.get(0).toString();
                    String telStr = row.get(2).toString();

                    // Acha o cliente pelo ID ou Telefone
                    if (idStr.equals(idVendedor) || telStr.equals(idVendedor)) {
                        int rowIndex = i + 1; // +1 porque a planilha começa na linha 1

                        // Grava o CPF na coluna D
                        ValueRange body = new ValueRange().setValues(Collections.singletonList(Collections.singletonList(cpfFinal)));
                        sheetsService.spreadsheets().values()
                                .update(spreadsheetId, "Usuarios!D" + rowIndex, body)
                                .setValueInputOption("USER_ENTERED")
                                .execute();
                        break;
                    }
                }
            }
        }
    }

    // --- GESTÃO DE MATERIAIS (Aba: Materiais) ---

    public List<Material> listarMateriais() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Materiais!A2:D")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return Collections.emptyList();

        return values.stream()
                // 👇 AQUI: Ignora linhas nulas, vazias ou que não tenham ID
                .filter(row -> row != null && !row.isEmpty() && row.size() >= 3 && !row.get(0).toString().trim().isEmpty())
                .map(row -> {
                    Material m = new Material();
                    m.setId(Long.parseLong(row.get(0).toString()));
                    m.setNome(row.get(1).toString());
                    m.setPrecoPorKg(new BigDecimal(row.get(2).toString().replace(",", ".")));
                    m.setUnidade(row.size() > 3 ? row.get(3).toString() : "KG");
                    return m;
                })
                .collect(Collectors.toList());
    }

    public void atualizarPrecoMaterial(Long id, BigDecimal novoPreco) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Materiais!A:A").execute();
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
            String precoFormatado = String.format("%.2f", novoPreco).replace(".", ",");
            ValueRange body = new ValueRange().setValues(Collections.singletonList(Collections.singletonList(precoFormatado)));
            sheetsService.spreadsheets().values().update(spreadsheetId, "Materiais!C" + rowIndex, body)
                    .setValueInputOption("USER_ENTERED").execute();
        }
    }

    public void salvarMaterial(Material material) throws IOException {
        String precoFormatado = String.format("%.2f", material.getPrecoPorKg()).replace(".", ",");
        List<Object> row = Arrays.asList(System.currentTimeMillis(), material.getNome(), precoFormatado, material.getUnidade());
        ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values().append(spreadsheetId, "Materiais!A1", body)
                .setValueInputOption("USER_ENTERED").execute();
    }

    public void deletarMaterial(Long id) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Materiais!A:A").execute();
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
            sheetsService.spreadsheets().values().clear(spreadsheetId, "Materiais!A" + (rowIndex + 1) + ":D" + (rowIndex + 1), new ClearValuesRequest()).execute();
        }
    }

    // --- OPERAÇÕES DE VENDA E RELATÓRIOS (Aba: Ofertas) ---

    public void registrarVendaFinal(String telefone, String material, Double peso, BigDecimal precoUn, BigDecimal total, String cpf) throws IOException {
        List<Object> row = Arrays.asList(
                System.currentTimeMillis(),
                material,
                peso.toString().replace(".", ","),
                "ENTREGA NO LOCAL",
                precoUn.toString().replace(".", ","),
                total.toString().replace(".", ","),
                LocalDate.now().toString(),
                telefone,
                "VENDIDO",
                cpf != null ? cpf : "NÃO INFORMADO"
        );

        ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Ofertas!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    public List<Oferta> buscarVendasPorUsuario(String telefone) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!A2:J").execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return Collections.emptyList();

        return values.stream()
                .filter(row -> row.size() > 8 &&
                        telefone.equals(row.get(7).toString()) &&
                        ("VENDIDO".equals(row.get(8).toString()) || "SAIDA_INDUSTRIA".equals(row.get(8).toString())))
                .map(row -> {
                    Oferta o = new Oferta();
                    o.setMaterial(row.get(1).toString());
                    o.setPeso(Double.parseDouble(row.get(2).toString().replace(",", ".")));
                    o.setPrecoEstimado(new BigDecimal(row.get(5).toString().replace(",", ".")));
                    return o;
                }).collect(Collectors.toList());
    }

    public void salvarSolicitacaoInicial(Usuario usuario, String endereco) throws IOException {
        salvarUsuario(usuario);
        List<Object> rowOferta = Arrays.asList(
                System.currentTimeMillis(), "Solicitação de Coleta", "0", endereco, "0", "0",
                LocalDate.now().toString(), usuario.getTelefone(), "DISPONIVEL", LocalDate.now().toString()
        );
        ValueRange body = new ValueRange().setValues(Collections.singletonList(rowOferta));
        sheetsService.spreadsheets().values().append(spreadsheetId, "Ofertas!A1", body).setValueInputOption("USER_ENTERED").execute();
    }

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        ValueRange responseOfertas = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!A2:J").execute();
        List<List<Object>> values = responseOfertas.getValues();
        if (values == null) return Collections.emptyList();

        LocalDate hoje = LocalDate.now();

        // 🌟 NOVO: Mapeia o Telefone para o Status ("DISPONIVEL" ou "EM_ATENDIMENTO")
        Map<String, String> statusMap = new HashMap<>();
        for (List<Object> row : values) {
            if (row.size() > 9) {
                String status = row.get(8).toString();
                String dataStr = row.get(9).toString();
                String telefone = row.get(7).toString();

                if (("DISPONIVEL".equals(status) || "EM_ATENDIMENTO".equals(status)) && LocalDate.parse(dataStr).isEqual(hoje)) {
                    statusMap.put(telefone, status);
                }
            }
        }

        return listarUsuarios().stream()
                .filter(u -> statusMap.containsKey(u.getTelefone()))
                .peek(u -> u.setStatusPlanilha(statusMap.get(u.getTelefone()))) // Injeta o status no objeto!
                .collect(Collectors.toList());
    }

    public Double calcularTotalReciclado() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!C2:I").execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return 0.0;

        double totalPatio = 0.0;

        for (List<Object> row : values) {
            if (row.size() > 6) {
                String status = row.get(6).toString();

                if ("VENDIDO".equals(status) || "SAIDA_INDUSTRIA".equals(status)) {
                    // Pega o peso (que está na primeira coluna desse bloco C2:I)
                    double peso = Double.parseDouble(row.get(0).toString().replace(",", "."));

                    if ("VENDIDO".equals(status)) {
                        totalPatio += peso; // Comprou do catador: SOMA
                    } else if ("SAIDA_INDUSTRIA".equals(status)) {
                        totalPatio -= peso; // Vendeu pra indústria: SUBTRAI
                    }
                }
            }
        }
        return totalPatio;
    }

    public List<RankingDTO> buscarRankingMateriais() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!B2:I").execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return new ArrayList<>();

        Map<String, Double> soma = new HashMap<>();

        for (List<Object> row : values) {
            // Verifica se a linha tem colunas suficientes para ler o Status (coluna I -> índice 7 neste bloco)
            if (row.size() > 7) {
                String status = row.get(7).toString();

                if ("VENDIDO".equals(status) || "SAIDA_INDUSTRIA".equals(status)) {
                    String nome = row.get(0).toString(); // Coluna B
                    Double peso = Double.parseDouble(row.get(1).toString().replace(",", ".")); // Coluna C

                    if ("VENDIDO".equals(status)) {
                        // Entrada: Adiciona ao estoque
                        soma.put(nome, soma.getOrDefault(nome, 0.0) + peso);
                    } else if ("SAIDA_INDUSTRIA".equals(status)) {
                        // Saída: Diminui do estoque
                        soma.put(nome, soma.getOrDefault(nome, 0.0) - peso);
                    }
                }
            }
        }

        List<Material> todos = listarMateriais();
        return soma.entrySet().stream()
                .map(e -> {
                    String un = "kg";
                    for (Material m : todos) {
                        if (m.getNome() != null && m.getNome().equalsIgnoreCase(e.getKey())) {
                            un = m.getUnidade();
                            break;
                        }
                    }
                    return new RankingDTO(e.getKey(), e.getValue(), un);
                })
                // Se quiser, essa linha abaixo esconde do painel os materiais que zeraram o estoque:
                .filter(dto -> dto.peso > 0)
                .sorted((a, b) -> b.peso.compareTo(a.peso))
                .limit(3) // <-- NOTA: Se você quiser que o painel mostre TODOS os materiais, e não só o Top 3, basta apagar essa linha ".limit(3)"
                .collect(Collectors.toList());
    }

    public Material buscarMaterialPorId(Long id) throws IOException {
        return listarMateriais().stream().filter(m -> m.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Material não encontrado"));
    }

    public List<Usuario> buscarUsuariosComVendasHoje() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!A2:J")
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return Collections.emptyList();

        String hoje = LocalDate.now().toString();

        Set<String> identificadores = values.stream()
                .filter(row -> row.size() > 8 && "VENDIDO".equals(row.get(8).toString()) && hoje.equals(row.get(6).toString()))
                .map(row -> row.get(7).toString())
                .collect(Collectors.toSet());

        return listarUsuarios().stream()
                .filter(u -> identificadores.contains(u.getTelefone()) || identificadores.contains(u.getId().toString()))
                .collect(Collectors.toList());
    }

    public void registrarVendasEmLote(List<List<Object>> linhasParaSalvar) throws IOException {
        if (linhasParaSalvar == null || linhasParaSalvar.isEmpty()) return;

        ValueRange body = new ValueRange().setValues(linhasParaSalvar);
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Ofertas!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    // 🌟 NOVO: Método que o TelaController chama logo ao abrir a tela de Check-list
    public void marcarComoEmAtendimento(String idVendedor) throws IOException {
        Usuario usuario = listarUsuarios().stream()
                .filter(u -> u.getId().toString().equals(idVendedor) || u.getTelefone().equals(idVendedor))
                .findFirst()
                .orElse(null);

        String telefone = usuario != null ? usuario.getTelefone() : idVendedor;
        String idStr = usuario != null ? usuario.getId().toString() : idVendedor;

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!H:I").execute();
        List<List<Object>> values = response.getValues();

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);

                if (row.size() >= 2) {
                    String identificadorPlanilha = row.get(0).toString();
                    String statusPlanilha = row.get(1).toString();

                    if ((identificadorPlanilha.equals(telefone) || identificadorPlanilha.equals(idStr)) && statusPlanilha.equals("DISPONIVEL")) {
                        int numeroDaLinha = i + 1;

                        ValueRange body = new ValueRange().setValues(Collections.singletonList(Collections.singletonList("EM_ATENDIMENTO")));

                        sheetsService.spreadsheets().values()
                                .update(spreadsheetId, "Ofertas!I" + numeroDaLinha, body)
                                .setValueInputOption("USER_ENTERED")
                                .execute();
                    }
                }
            }
        }
    }

    public void marcarSolicitacaoComoConcluida(String idVendedor) throws IOException {
        Usuario usuario = listarUsuarios().stream()
                .filter(u -> u.getId().toString().equals(idVendedor) || u.getTelefone().equals(idVendedor))
                .findFirst()
                .orElse(null);

        String telefone = usuario != null ? usuario.getTelefone() : idVendedor;
        String idStr = usuario != null ? usuario.getId().toString() : idVendedor;

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!H:I").execute();
        List<List<Object>> values = response.getValues();

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);

                if (row.size() >= 2) {
                    String identificadorPlanilha = row.get(0).toString();
                    String statusPlanilha = row.get(1).toString();

                    // 🌟 AJUSTE: Limpa o card da tela tanto se estava Disponível quanto Em Atendimento
                    if ((identificadorPlanilha.equals(telefone) || identificadorPlanilha.equals(idStr)) &&
                            (statusPlanilha.equals("DISPONIVEL") || statusPlanilha.equals("EM_ATENDIMENTO"))) {
                        int numeroDaLinha = i + 1;

                        ValueRange body = new ValueRange().setValues(Collections.singletonList(Collections.singletonList("FINALIZADO")));

                        sheetsService.spreadsheets().values()
                                .update(spreadsheetId, "Ofertas!I" + numeroDaLinha, body)
                                .setValueInputOption("USER_ENTERED")
                                .execute();
                    }
                }
            }
        }
    }


    @Scheduled(cron = "0 0 3 * * *") // Roda às 3h da manhã
    public void limparOfertasAntigasAutomaticamente() {
        try {
            System.out.println("🤖 Robô Faxineiro: Iniciando arquivamento de registros com mais de 365 dias...");

            // 1. Puxa os dados da aba Ofertas (da linha 2 até a coluna J)
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "Ofertas!A2:J")
                    .execute();
            List<List<Object>> todasAsLinhas = response.getValues();

            if (todasAsLinhas == null || todasAsLinhas.isEmpty()) {
                System.out.println("🤖 Robô Faxineiro: Nenhuma oferta encontrada para processar.");
                return;
            }

            LocalDate dataLimite = LocalDate.now().minusDays(365);
            List<List<Object>> linhasParaManter = new ArrayList<>();
            List<List<Object>> linhasParaArquivar = new ArrayList<>();

            for (List<Object> row : todasAsLinhas) {
                try {
                    // A data está na coluna G (índice 6)
                    if (row.size() > 6 && row.get(6) != null) {
                        LocalDate dataRow = LocalDate.parse(row.get(6).toString());

                        if (dataRow.isBefore(dataLimite)) {
                            linhasParaArquivar.add(row);
                        } else {
                            linhasParaManter.add(row);
                        }
                    } else {
                        // Se a linha estiver incompleta ou sem data, mantemos na principal para não perder informação
                        linhasParaManter.add(row);
                    }
                } catch (Exception e) {
                    // Em caso de erro de formatação na data, mantém a linha na aba ativa
                    linhasParaManter.add(row);
                }
            }

            // 2. Se houver o que arquivar, manda para a aba Historico_Ofertas
            if (!linhasParaArquivar.isEmpty()) {
                ValueRange bodyArquivo = new ValueRange().setValues(linhasParaArquivar);
                sheetsService.spreadsheets().values()
                        .append(spreadsheetId, "Historico_Ofertas!A1", bodyArquivo)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
                System.out.println("✅ " + linhasParaArquivar.size() + " registros movidos para o Histórico.");
            } else {
                System.out.println("ℹ️ Nenhuma oferta com mais de 365 dias para arquivar.");
                return; // Interrompe aqui para não limpar a aba principal sem necessidade
            }

            // 3. Limpa a aba Ofertas e recoloca apenas o que é recente (manter)
            sheetsService.spreadsheets().values()
                    .clear(spreadsheetId, "Ofertas!A2:J", new ClearValuesRequest())
                    .execute();

            if (!linhasParaManter.isEmpty()) {
                ValueRange bodyManter = new ValueRange().setValues(linhasParaManter);
                sheetsService.spreadsheets().values()
                        .update(spreadsheetId, "Ofertas!A2", bodyManter)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            }

            System.out.println("🤖 Robô Faxineiro: Operação finalizada com sucesso!");

        } catch (Exception e) {
            System.err.println("❌ Erro crítico no Robô Faxineiro: " + e.getMessage());
        }
    }


    // --- MÉTODO PARA DASHBOARD: BUSCA TUDO (OFERTAS ATUAIS + HISTÓRICO) ---
    public List<Oferta> getHistoricoCompleto() throws IOException {
        List<Oferta> listaTotal = new ArrayList<>();

        // 1. LER ABA "Ofertas" (Movimentação Recente)
        ValueRange resOfertas = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!A2:J").execute();
        if (resOfertas.getValues() != null) {
            listaTotal.addAll(converterLinhasParaOfertas(resOfertas.getValues()));
        }

        // 2. LER ABA "Historico_Ofertas" (Movimentação Antiga)
        try {
            ValueRange resHistorico = sheetsService.spreadsheets().values().get(spreadsheetId, "Historico_Ofertas!A2:J").execute();
            if (resHistorico.getValues() != null) {
                listaTotal.addAll(converterLinhasParaOfertas(resHistorico.getValues()));
            }
        } catch (Exception e) {
            System.out.println("Aba Historico_Ofertas ainda não existe ou está vazia.");
        }

        return listaTotal;
    }

    // Metodo auxiliar para não repetir código
    // Metodo auxiliar para não repetir código
    private List<Oferta> converterLinhasParaOfertas(List<List<Object>> values) {
        return values.stream()
                // 1. O FILTRO: Agora ele deixa passar tanto as Entradas quanto as Saídas
                .filter(row -> row.size() > 8 &&
                        ("VENDIDO".equals(row.get(8).toString()) || "SAIDA_INDUSTRIA".equals(row.get(8).toString())))
                .map(row -> {
                    Oferta o = new Oferta();
                    o.setMaterial(row.get(1).toString());
                    o.setPeso(Double.parseDouble(row.get(2).toString().replace(",", ".")));
                    o.setPrecoEstimado(new BigDecimal(row.get(5).toString().replace(",", ".")));
                    o.setData(row.size() > 6 ? row.get(6).toString() : "Sem Data");

                    if (row.size() > 7 && row.get(7) != null) {
                        o.setIdUsuario(row.get(7).toString());
                    }

                    // 2. A ETIQUETA: Salva o status no objeto para o Dashboard saber que tem que subtrair do pátio!
                    if (row.size() > 8 && row.get(8) != null) {
                        String statusPlanilha = row.get(8).toString();
                        try {
                            o.setStatus(Oferta.StatusOferta.valueOf(statusPlanilha));
                        } catch (Exception e) {
                            // Se o Enum der erro, ignora silenciosamente para não quebrar a tela
                        }
                    }

                    return o;
                }).collect(Collectors.toList());
    }


}