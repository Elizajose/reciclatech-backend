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
    // --- Troca de BD remover esse comentario Eli foi só um teste ---

    public void salvarUsuario(Usuario usuario) throws IOException {
        usuario.prePersist();
        // Colunas: A:ID, B:Nome, C:Telefone, D:CPF, E:Email, F:Endereco, G:Data, H:Status, I:Tipo
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
        // Agora lemos até a coluna F (índice 5) para pegar o endereço
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
            // Se houver dado na Coluna F (índice 5), salvamos o endereço
            if (row.size() > 5) {
                u.setEndereco(row.get(5).toString());
            }
            return u;
        }).collect(Collectors.toList());
    }

    // --- GESTÃO DE MATERIAIS (Aba: Materiais) ---

    public List<Material> listarMateriais() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Materiais!A2:D")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return Collections.emptyList();

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
        // Colunas: A:ID, B:Materiais, C:Peso, D:Endereço, E:Preço_Un, F:Preço_Total, G:Data, H:Usuario_ID, I:Status, J:CPF
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
                .filter(row -> row.size() > 8 && telefone.equals(row.get(7).toString()) && "VENDIDO".equals(row.get(8).toString()))
                .map(row -> {
                    Oferta o = new Oferta();
                    o.setMaterial(row.get(1).toString());
                    o.setPeso(Double.parseDouble(row.get(2).toString().replace(",", ".")));
                    o.setPrecoEstimado(new BigDecimal(row.get(5).toString().replace(",", "."))); // Coluna F
                    return o;
                }).collect(Collectors.toList());
    }

    public void salvarSolicitacaoInicial(Usuario usuario, String endereco) throws IOException {
        salvarUsuario(usuario);
        // Garante que a linha de solicitação inicial também siga o padrão de 10 colunas
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
        Set<String> telefones = values.stream()
                .filter(row -> row.size() > 9 && "DISPONIVEL".equals(row.get(8).toString()) && LocalDate.parse(row.get(9).toString()).isEqual(hoje))
                .map(row -> row.get(7).toString()).collect(Collectors.toSet());

        return listarUsuarios().stream().filter(u -> telefones.contains(u.getTelefone())).collect(Collectors.toList());
    }

    public Double calcularTotalReciclado() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!C2:I").execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return 0.0;

        return values.stream()
                .filter(row -> row.size() > 6 && "VENDIDO".equals(row.get(6).toString()))
                .mapToDouble(row -> Double.parseDouble(row.get(0).toString().replace(",", ".")))
                .sum();
    }

    public List<RankingDTO> buscarRankingMateriais() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!B2:I").execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return new ArrayList<>();

        Map<String, Double> soma = new HashMap<>();
        for (List<Object> row : values) {
            if (row.size() > 7 && "VENDIDO".equals(row.get(7).toString())) {
                String nome = row.get(0).toString();
                Double peso = Double.parseDouble(row.get(1).toString().replace(",", "."));
                soma.put(nome, soma.getOrDefault(nome, 0.0) + peso);
            }
        }

        List<Material> todos = listarMateriais();
        return soma.entrySet().stream().map(e -> {
            String un = "kg";
            for (Material m : todos) {
                if (m.getNome() != null && m.getNome().equalsIgnoreCase(e.getKey())) {
                    un = m.getUnidade();
                    break;
                }
            }
            return new RankingDTO(e.getKey(), e.getValue(), un);
        }).sorted((a, b) -> b.peso.compareTo(a.peso)).limit(3).collect(Collectors.toList());
    }

    public Material buscarMaterialPorId(Long id) throws IOException {
        return listarMateriais().stream().filter(m -> m.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RuntimeException("Material não encontrado"));
    }
    // Novo método para alimentar a tela de extratos (meus-extratos.html)
    public List<Usuario> buscarUsuariosComVendasHoje() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Ofertas!A2:J")
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null) return Collections.emptyList();

        String hoje = LocalDate.now().toString();

        // Pegamos os identificadores da coluna H (índice 7). Pode ser ID ou Telefone.
        Set<String> identificadores = values.stream()
                .filter(row -> row.size() > 8 && "VENDIDO".equals(row.get(8).toString()) && hoje.equals(row.get(6).toString()))
                .map(row -> row.get(7).toString())
                .collect(Collectors.toSet());

        // Filtramos cruzando com o Telefone OU com o ID do usuário
        return listarUsuarios().stream()
                .filter(u -> identificadores.contains(u.getTelefone()) || identificadores.contains(u.getId().toString()))
                .collect(Collectors.toList());
    }
    // Novo método para salvar várias vendas de uma vez só!
    public void registrarVendasEmLote(List<List<Object>> linhasParaSalvar) throws IOException {
        if (linhasParaSalvar == null || linhasParaSalvar.isEmpty()) return;

        ValueRange body = new ValueRange().setValues(linhasParaSalvar);
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Ofertas!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }
    // Método para tirar o cliente da fila de espera após o atendimento
    public void marcarSolicitacaoComoConcluida(String idVendedor) throws IOException {
        // 1. Busca o usuário para garantir que temos tanto o ID quanto o Telefone em mãos
        Usuario usuario = listarUsuarios().stream()
                .filter(u -> u.getId().toString().equals(idVendedor) || u.getTelefone().equals(idVendedor))
                .findFirst()
                .orElse(null);

        String telefone = usuario != null ? usuario.getTelefone() : idVendedor;
        String idStr = usuario != null ? usuario.getId().toString() : idVendedor;

        // 2. Vasculha a planilha nas colunas H (Identificador) e I (Status)
        ValueRange response = sheetsService.spreadsheets().values().get(spreadsheetId, "Ofertas!H:I").execute();
        List<List<Object>> values = response.getValues();

        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);

                if (row.size() >= 2) {
                    String identificadorPlanilha = row.get(0).toString();
                    String statusPlanilha = row.get(1).toString();

                    // 3. A MÁGICA: Se a planilha tiver o Telefone OU o ID, ele dá baixa!
                    if ((identificadorPlanilha.equals(telefone) || identificadorPlanilha.equals(idStr)) && statusPlanilha.equals("DISPONIVEL")) {
                        int numeroDaLinha = i + 1; // +1 porque a planilha começa na linha 1

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
    // --- ROBÔ FAXINEIRO (AGENDAMENTO AUTOMÁTICO) ---

    // Roda todo dia às 03:00 da manhã (Cron: Segundos Minutos Horas Dia Mês DiaDaSemana)
    @Scheduled(cron = "0 0 3 * * *")
    public void limparOfertasAntigasAutomaticamente() {
        try {
            System.out.println("🤖 Robô Faxineiro: Iniciando limpeza de registros com mais de 30 dias...");

            // 1. Pega todas as linhas atuais da aba Ofertas
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, "Ofertas!A2:J")
                    .execute();
            List<List<Object>> todasAsLinhas = response.getValues();

            if (todasAsLinhas == null || todasAsLinhas.isEmpty()) return;

            // Define o limite de tempo (Hoje menos 30 dias)
            LocalDate dataLimite = LocalDate.now().minusDays(2);

            // 2. Filtra a lista: Guarda APENAS as coisas RECENTES (que vão sobreviver à limpeza)
            List<List<Object>> linhasParaManter = todasAsLinhas.stream()
                    .filter(row -> {
                        try {
                            // A data fica na coluna G (índice 6)
                            if (row.size() > 6) {
                                LocalDate dataRow = LocalDate.parse(row.get(6).toString());
                                // Mantém a linha se a data dela for DEPOIS (mais nova) ou igual ao limite
                                return dataRow.isAfter(dataLimite) || dataRow.isEqual(dataLimite);
                            }
                            return true; // Se a linha não tiver data por algum erro, não exclui por segurança
                        } catch (Exception e) {
                            return true;
                        }
                    })
                    .collect(Collectors.toList());

            // Se o tamanho for igual, não tem nada velho para apagar
            if (todasAsLinhas.size() == linhasParaManter.size()) {
                System.out.println("🤖 Robô Faxineiro: Nenhuma oferta antiga para limpar hoje.");
                return;
            }

            // 3. APAGA TUDO da planilha (Da linha 2 pra baixo)
            sheetsService.spreadsheets().values()
                    .clear(spreadsheetId, "Ofertas!A2:J", new ClearValuesRequest())
                    .execute();

            // 4. ESCREVE DE VOLTA só as linhas recentes que separamos
            if (!linhasParaManter.isEmpty()) {
                ValueRange body = new ValueRange().setValues(linhasParaManter);
                sheetsService.spreadsheets().values()
                        .update(spreadsheetId, "Ofertas!A2", body)
                        .setValueInputOption("USER_ENTERED")
                        .execute();
            }

            int apagados = todasAsLinhas.size() - linhasParaManter.size();
            System.out.println("🤖 Robô Faxineiro: Limpeza concluída! " + apagados + " registros antigos apagados da planilha.");

        } catch (Exception e) {
            System.err.println("Erro no Robô Faxineiro: " + e.getMessage());
        }
    }
}