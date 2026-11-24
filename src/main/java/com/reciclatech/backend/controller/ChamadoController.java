package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Chamado;
import com.reciclatech.backend.service.ChamadoService;
import com.reciclatech.backend.service.EmailService;
import com.reciclatech.backend.service.LimitadorPublico;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Fase 32: "Meus chamados" do armazém (com o sininho de respostas) e a caixa de chamados do Master.
 * Abrir o chamado continua no modal de ajuda do painel (TelaController.enviarChamado), que agora também grava aqui.
 */
@Controller
public class ChamadoController {

    private static final Logger log = LoggerFactory.getLogger(ChamadoController.class);

    @Autowired private ChamadoService chamadoService;
    @Autowired private EmailService emailService;
    @Autowired private LimitadorPublico limitador;

    @Value("${coletae.url-base:}")
    private String urlBase;

    // Fase 34: o suporte recebe e-mail quando o armazém responde um chamado
    @Value("${coletae.email-suporte:}")
    private String emailSuporte;

    private static ChamadoService.Anexo anexoDe(org.springframework.web.multipart.MultipartFile f) throws java.io.IOException {
        if (f == null || f.isEmpty()) return null;
        if (f.getSize() > ChamadoService.ANEXO_MAX_BYTES) throw new IllegalArgumentException("O anexo passa de 5 MB.");
        return new ChamadoService.Anexo(f.getOriginalFilename(), f.getBytes());
    }

    private String base() { return urlBase == null ? "" : urlBase.trim().replaceAll("/+$", ""); }

    /** Fase 34: entrega o anexo (só imagem ou PDF conferidos pelo conteúdo), sem deixar o navegador "adivinhar" o tipo. */
    private static org.springframework.http.ResponseEntity<byte[]> entregar(com.reciclatech.backend.model.ChamadoAnexo a) {
        String disp = (a.isImagem() || "application/pdf".equals(a.getTipo()) ? "inline" : "attachment") + "; filename=\"" + a.getNome() + "\"";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", a.getTipo())
                .header("Content-Disposition", disp)
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .body(a.getDados());
    }

    private static Long armazemDa(HttpSession s) {
        Object id = s.getAttribute("idPlanilhaAtiva");
        if (id == null) return null;
        try { return Long.parseLong(id.toString()); } catch (NumberFormatException e) { return null; }
    }

    static String autorDa(HttpSession s) {
        for (String k : new String[]{"atendenteNome", "nomeFuncionarioLogado", "parceiroNome"}) {
            Object v = s.getAttribute(k);
            if (v != null && !v.toString().isBlank() && !"null".equals(v.toString())) return v.toString();
        }
        return "Armazém";
    }

    private static void semCache(HttpServletResponse r) {
        r.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        r.setHeader("Pragma", "no-cache");
    }

    // ===================== ARMAZÉM =====================

    @GetMapping("/admin/chamados")
    public String meusChamados(@RequestParam(required = false) Long id, Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        Long armazemId = armazemDa(session);
        if (armazemId == null) return "redirect:/login";
        semCache(response);
        Chamado aberto = null;
        if (id != null) aberto = chamadoService.abrirParaArmazem(armazemId, id);
        List<Chamado> lista = chamadoService.doArmazem(armazemId);
        if (aberto == null && id == null && !lista.isEmpty()) aberto = chamadoService.abrirParaArmazem(armazemId, lista.get(0).getId());
        model.addAttribute("chamados", lista);
        model.addAttribute("chamado", aberto);
        model.addAttribute("mensagens", aberto == null ? List.of() : chamadoService.mensagensDo(aberto.getId()));
        model.addAttribute("anexos", aberto == null ? java.util.Map.of() : chamadoService.anexosPorMensagem(aberto.getId()));
        model.addAttribute("naoLidos", chamadoService.naoLidosDoArmazem(armazemId));
        return "admin-chamados";
    }

    @PostMapping("/admin/chamados/{id}/responder")
    public String responderArmazem(@PathVariable Long id, @RequestParam String texto,
                                   @RequestParam(value = "arquivo", required = false) org.springframework.web.multipart.MultipartFile arquivo,
                                   HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("adminLogado") == null) return "redirect:/login";
        Long armazemId = armazemDa(session);
        if (armazemId == null) return "redirect:/login";
        if (!limitador.permitir("chamado-msg:" + armazemId, 30, 60 * 60 * 1000L)) {
            ra.addFlashAttribute("erroTela", "Muitas mensagens na última hora. Aguarde um pouco ou chame no WhatsApp.");
            return "redirect:/admin/chamados?id=" + id;
        }
        try {
            Chamado c = chamadoService.responderComoArmazem(armazemId, id, autorDa(session), texto, anexoDe(arquivo));
            ra.addFlashAttribute("sucessoTela", "Mensagem enviada ao suporte.");
            emailService.avisarSuporteChamado(emailSuporte, c.getCodigo(), c.getNomeArmazem(), c.getAssunto(), texto.strip(),
                    base().isEmpty() ? null : base() + "/master/chamados?id=" + c.getId() + "&filtro=todos");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroTela", e.getMessage());
        } catch (Exception e) {
            log.error("Falha ao responder chamado", e);
            ra.addFlashAttribute("erroTela", "Não foi possível enviar. Tente de novo.");
        }
        return "redirect:/admin/chamados?id=" + id;
    }

    @GetMapping("/admin/chamados/anexo/{anexoId}")
    public org.springframework.http.ResponseEntity<byte[]> anexoArmazem(@PathVariable Long anexoId, HttpSession session) {
        Long armazemId = session.getAttribute("adminLogado") == null ? null : armazemDa(session);
        if (armazemId == null) return org.springframework.http.ResponseEntity.status(403).build();
        return chamadoService.anexo(anexoId, armazemId).map(ChamadoController::entregar)
                .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }

    @GetMapping("/master/chamados/anexo/{anexoId}")
    public org.springframework.http.ResponseEntity<byte[]> anexoMaster(@PathVariable Long anexoId, HttpSession session) {
        if (session.getAttribute("masterLogado") == null) return org.springframework.http.ResponseEntity.status(403).build();
        return chamadoService.anexo(anexoId, null).map(ChamadoController::entregar)
                .orElse(org.springframework.http.ResponseEntity.notFound().build());
    }

    /** Número do sininho, para o painel atualizar sem recarregar (leve: uma contagem). */
    @GetMapping("/admin/chamados/nao-lidos")
    @ResponseBody
    public java.util.Map<String, Object> naoLidos(HttpSession session, HttpServletResponse response) {
        semCache(response);
        Long armazemId = session.getAttribute("adminLogado") == null ? null : armazemDa(session);
        return java.util.Map.of("naoLidos", armazemId == null ? 0 : chamadoService.naoLidosDoArmazem(armazemId));
    }

    // ===================== MASTER =====================

    @GetMapping("/master/chamados")
    public String caixaMaster(@RequestParam(required = false) Long id, @RequestParam(required = false) String filtro,
                              Model model, HttpSession session, HttpServletResponse response) {
        if (session.getAttribute("masterLogado") == null) return "redirect:/login";
        semCache(response);
        Chamado aberto = id == null ? null : chamadoService.abrirParaSuporte(id);
        List<Chamado> lista = chamadoService.todos();
        String f = filtro == null ? "pendentes" : filtro;
        if ("pendentes".equals(f)) lista = lista.stream().filter(c -> Chamado.ABERTO.equals(c.getSituacao())).toList();
        else if ("respondidos".equals(f)) lista = lista.stream().filter(c -> Chamado.RESPONDIDO.equals(c.getSituacao())).toList();
        else if ("fechados".equals(f)) lista = lista.stream().filter(c -> Chamado.FECHADO.equals(c.getSituacao())).toList();
        else f = "todos";
        model.addAttribute("chamados", lista);
        model.addAttribute("filtro", f);
        model.addAttribute("chamado", aberto);
        model.addAttribute("mensagens", aberto == null ? List.of() : chamadoService.mensagensDo(aberto.getId()));
        model.addAttribute("anexos", aberto == null ? java.util.Map.of() : chamadoService.anexosPorMensagem(aberto.getId()));
        model.addAttribute("naoLidosSuporte", chamadoService.naoLidosDoSuporte());
        return "master-chamados";
    }

    @PostMapping("/master/chamados/{id}/responder")
    public String responderMaster(@PathVariable Long id, @RequestParam(required = false) String texto,
                                  @RequestParam(required = false) String fechar,
                                  @RequestParam(value = "arquivo", required = false) org.springframework.web.multipart.MultipartFile arquivo,
                                  HttpSession session, RedirectAttributes ra) {
        if (session.getAttribute("masterLogado") == null) return "redirect:/login";
        try {
            boolean temTexto = texto != null && !texto.isBlank();
            Chamado c = chamadoService.responderComoSuporte(id, texto, fechar != null, anexoDe(arquivo));
            if (temTexto && c.getEmailRetorno() != null) {
                String base = base();
                emailService.enviarRespostaChamado(c.getEmailRetorno(), c.getNomeArmazem(), c.getCodigo(), c.getAssunto(),
                        texto.strip(), base.isEmpty() ? null : base + "/admin/chamados?id=" + c.getId());
            }
            ra.addFlashAttribute("sucessoTela", temTexto
                    ? "Resposta enviada. O armazém vê o aviso no sininho" + (c.getEmailRetorno() != null ? " e recebe um e-mail." : ".")
                    : "Chamado marcado como resolvido.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("erroTela", e.getMessage());
        } catch (Exception e) {
            log.error("Falha ao responder chamado (master)", e);
            ra.addFlashAttribute("erroTela", "Não foi possível salvar a resposta.");
        }
        return "redirect:/master/chamados?id=" + id + "&filtro=todos";
    }
}
