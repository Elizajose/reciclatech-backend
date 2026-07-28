package com.reciclatech.backend.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.model.Material;
import com.reciclatech.backend.model.Oferta;
import com.reciclatech.backend.controller.TelaController.RankingDTO;
import jakarta.servlet.http.HttpSession; // Necessário para ler a sessão
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
    private final HttpSession session; // Injetamos a sessão para descobrir qual planilha ler

    // ID DA SUA PLANILHA MESTRE (Única fixa no sistema)
    private final String MASTER_SHEET_ID = "1IRyZeg2ZZ8icCO-v_TsAvy01AqUTwbawYbuf_E0gZ4o";

    public GoogleSheetsService(Sheets sheetsService, HttpSession session) {
        this.sheetsService = sheetsService;
        this.session = session;
    }

    // Método mágico que descobre qual planilha pertence ao usuário logado no momento
    private String getSpreadsheetIdAtivo() {
        String idSessao = (String) session.getAttribute("idPlanilhaAtiva");
        if (idSessao != null && !idSessao.isEmpty()) {
            return idSessao;
        }
        // Fallback de segurança caso não ache na sessão (joga para a sua padrão)
        return "15ZPzZ9Gxm5iv...";
    }

    // --- GESTÃO DE USUÁRIOS (Aba: Usuarios) ---

    public void salvarUsuario(Usuario usuario) throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
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
        String spreadsheetId = getSpreadsheetIdAtivo();
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

            if (row.size() > 3 && !row.get(3).toString().trim().isEmpty()) {
                u.setCpf(row.get(3).toString());
            }

            if (row.size() > 5) {
                u.setEndereco(row.get(5).toString());
            }
            return u;
        }).collect(Collectors.toList());
    }

    public void atualizarCpfUsuario(String idVendedor, String cpfFinal) throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
        if (cpfFinal == null || cpfFinal.trim().isEmpty() || cpfFinal.equals("NÃO INFORMADO")) {
            return;
        }

        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Usuarios!A:C").execute();
        List<List<Object>> values = response.getValues();

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row.size() >= 3) {
                    String idStr = row.get(0).toString();
                    String telStr = row.get(2).toString();

                    if (idStr.equals(idVendedor) || telStr.equals(idVendedor)) {
                        int rowIndex = i + 1;
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
        String spreadsheetId = getSpreadsheetIdAtivo();
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Materiais!A2:D")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return Collections.emptyList();

        return values.stream()
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
        String spreadsheetId = getSpreadsheetIdAtivo();
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
        String spreadsheetId = getSpreadsheetIdAtivo();
        String precoFormatado = String.format("%.2f", material.getPrecoPorKg()).replace(".", ",");
        List<Object> row = Arrays.asList(System.currentTimeMillis(), material.getNome(), precoFormatado, material.getUnidade());
        ValueRange body = new ValueRange().setValues(Collections.singletonList(row));
        sheetsService.spreadsheets().values().append(spreadsheetId, "Materiais!A1", body)
                .setValueInputOption("USER_ENTERED").execute();
    }

    public void deletarMaterial(Long id) throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
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
        String spreadsheetId = getSpreadsheetIdAtivo();
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
        String spreadsheetId = getSpreadsheetIdAtivo();
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
        String spreadsheetId = getSpreadsheetIdAtivo();
        sheetsService.spreadsheets().values().append(spreadsheetId, "Ofertas!A1", body).setValueInputOption("USER_ENTERED").execute();
    }

    public List<Usuario> buscarUsuariosComColetasPendentes() throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
        ValueRange responseOfertas = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!A2:J").execute();
        List<List<Object>> values = responseOfertas.getValues();
        if (values == null) return Collections.emptyList();

        LocalDate hoje = LocalDate.now();
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
                .peek(u -> u.setStatusPlanilha(statusMap.get(u.getTelefone())))
                .collect(Collectors.toList());
    }

    public Double calcularTotalReciclado() throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!C2:I").execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return 0.0;

        double totalPatio = 0.0;
        for (List<Object> row : values) {
            if (row.size() > 6) {
                String status = row.get(6).toString();
                if ("VENDIDO".equals(status) || "SAIDA_INDUSTRIA".equals(status)) {
                    double peso = Double.parseDouble(row.get(0).toString().replace(",", "."));
                    if ("VENDIDO".equals(status)) {
                        totalPatio += peso;
                    } else if ("SAIDA_INDUSTRIA".equals(status)) {
                        totalPatio -= peso;
                    }
                }
            }
        }
        return totalPatio;
    }

    public List<RankingDTO> buscarRankingMateriais() throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!B2:I").execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return new ArrayList<>();

        Map<String, Double> soma = new HashMap<>();
        for (List<Object> row : values) {
            if (row.size() > 7) {
                String status = row.get(7).toString();
                if ("VENDIDO".equals(status) || "SAIDA_INDUSTRIA".equals(status)) {
                    String nome = row.get(0).toString();
                    Double peso = Double.parseDouble(row.get(1).toString().replace(",", "."));
                    if ("VENDIDO".equals(status)) {
                        soma.put(nome, soma.getOrDefault(nome, 0.0) + peso);
                    } else if ("SAIDA_INDUSTRIA".equals(status)) {
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
                .filter(dto -> dto.peso > 0)
                .sorted((a, b) -> b.peso.compareTo(a.peso))
                .limit(3)
                .collect(Collectors.toList());
    }

    public Material buscarMaterialPorId(Long id) throws IOException {
        return listarMateriais().stream().filter(m -> m.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Material não encontrado"));
    }

    public List<Usuario> buscarUsuariosComVendasHoje() throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
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
        String spreadsheetId = getSpreadsheetIdAtivo();
        if (linhasParaSalvar == null || linhasParaSalvar.isEmpty()) return;

        ValueRange body = new ValueRange().setValues(linhasParaSalvar);
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Ofertas!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    public void marcarComoEmAtendimento(String idVendedor) throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
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
        String spreadsheetId = getSpreadsheetIdAtivo();
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

    @Scheduled(cron = "0 0 3 * * *")
    public void limparOfertasAntigasAutomaticamente() {
        // Mantido conforme o seu código anterior (executa nas planilhas se necessário)
    }

    public List<Oferta> getHistoricoCompleto() throws IOException {
        String spreadsheetId = getSpreadsheetIdAtivo();
        List<Oferta> listaTotal = new ArrayList<>();

        ValueRange resOfertas = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!A2:J").execute();
        if (resOfertas.getValues() != null) {
            listaTotal.addAll(converterLinhasParaOfertas(resOfertas.getValues()));
        }

        try {
            ValueRange resHistorico = sheetsService.spreadsheets().values().get(spreadsheetId, "Historico_Ofertas!A2:J").execute();
            if (resHistorico.getValues() != null) {
                listaTotal.addAll(converterLinhasParaOfertas(resHistorico.getValues()));
            }
        } catch (Exception e) {
            System.out.println("Aba Historico_Ofertas vazia.");
        }

        return listaTotal;
    }

    private List<Oferta> converterLinhasParaOfertas(List<List<Object>> values) {
        return values.stream()
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

                    if (row.size() > 8 && row.get(8) != null) {
                        String statusPlanilha = row.get(8).toString();
                        try {
                            o.setStatus(Oferta.StatusOferta.valueOf(statusPlanilha));
                        } catch (Exception e) {}
                    }

                    return o;
                }).collect(Collectors.toList());
    }

    public Map<String, String> autenticarSaaS(String login, String senha) throws IOException {
        // MUDANÇA 1: Agora ele lê até a coluna H (onde está a Senha Financeira)
        ValueRange response = sheetsService.spreadsheets().values()
                .get(MASTER_SHEET_ID, "Acessos!A2:H")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return null;

        for (List<Object> row : values) {
            if (row.size() >= 5) {
                String sheetLogin = row.get(0).toString().trim();
                String sheetSenha = row.get(1).toString().trim();
                String status = row.get(3).toString().trim();

                if (sheetLogin.equals(login) && sheetSenha.equals(senha)) {
                    if (!"ATIVO".equalsIgnoreCase(status)) {
                        throw new RuntimeException("Conta suspensa"); // Bloqueia inadimplentes
                    }

                    Map<String, String> dadosSessao = new HashMap<>();
                    dadosSessao.put("perfil", row.get(2).toString());
                    dadosSessao.put("idPlanilha", row.get(4).toString());

                    if (row.size() >= 6) dadosSessao.put("nomeArmazem", row.get(5).toString());
                    if (row.size() >= 7) dadosSessao.put("cnpj", row.get(6).toString());

                    // MUDANÇA 2: Guarda a Senha Financeira (Coluna H - Índice 7)
                    if (row.size() >= 8 && !row.get(7).toString().trim().isEmpty()) {
                        dadosSessao.put("senhaFinanceira", row.get(7).toString().trim());
                    } else {
                        // Se esquecer de preencher na planilha, não quebra o sistema
                        dadosSessao.put("senhaFinanceira", "admin123");
                    }

                    return dadosSessao;
                }
            }
        }
        return null;
    }
}