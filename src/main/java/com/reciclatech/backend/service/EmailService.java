package com.reciclatech.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Envio de e-mails da conta. Nunca lança exceção: falha de e-mail não pode derrubar a tela. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.username:}")
    private String remetente;

    /** Só para testes locais: escreve o link no log. Em produção deve ficar desligado. */
    @Value("${coletae.dev-log-links:false}")
    private boolean logarLinks;

    public EmailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    /** Aviso de segurança: a senha da conta acabou de ser trocada. Nunca lança exceção. */
    public void enviarSenhaAlterada(String para, String nome) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente == null || remetente.isBlank() || para == null || para.isBlank()) {
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(remetente);
            msg.setTo(para);
            msg.setSubject("Coletaê: sua senha foi alterada");
            msg.setText("Olá, " + nome + "!\n\n"
                    + "A senha da sua conta no Coletaê acabou de ser alterada.\n\n"
                    + "Foi você? Então está tudo certo, não precisa fazer nada.\n"
                    + "Não foi você? Use agora a opção \"Esqueci minha senha\" na tela de entrada para criar uma nova senha "
                    + "e fale com a gente: " + remetente + ".");
            sender.send(msg);
        } catch (Exception e) {
            log.error("Falha ao enviar aviso de senha alterada: {}", e.getMessage());
        }
    }

    public void enviarRedefinicaoSenha(String para, String nome, String link) {
        if (logarLinks) {
            log.info("[DEV] Link de redefinição de senha para {}: {}", para, link);
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente == null || remetente.isBlank()) {
            log.warn("E-mail não configurado: link de redefinição não enviado.");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(remetente);
            msg.setTo(para);
            msg.setSubject("Coletaê: redefinição de senha");
            msg.setText("Olá, " + nome + "!\n\n"
                    + "Recebemos um pedido para redefinir a senha da sua conta no Coletaê.\n"
                    + "Use o link abaixo (vale por 1 hora e só pode ser usado uma vez):\n\n"
                    + link + "\n\n"
                    + "Se você não pediu isso, ignore este e-mail: sua senha continua a mesma.");
            sender.send(msg);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail de redefinição: {}", e.getMessage());
        }
    }

    /** Fase 32: avisa o armazém que o suporte respondeu o chamado. Nunca lança exceção. */
    public void enviarRespostaChamado(String para, String nomeArmazem, String codigo, String assunto, String resposta, String link) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente == null || remetente.isBlank() || para == null || para.isBlank()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(remetente);
            msg.setTo(para);
            msg.setSubject(("Coletaê: resposta ao chamado " + codigo + " - " + assunto).replaceAll("[\\r\\n]+", " "));
            msg.setText("Olá" + (nomeArmazem == null || nomeArmazem.isBlank() ? "" : ", " + nomeArmazem) + "!\n\n"
                    + "O suporte respondeu o seu chamado " + codigo + " (" + assunto + "):\n\n"
                    + resposta + "\n\n"
                    + (link == null || link.isBlank() ? "Veja e responda no Coletaê: menu do perfil > Meus chamados.\n"
                                                      : "Veja e responda no Coletaê: " + link + "\n")
                    + "\nPara continuar a conversa, responda pelo Coletaê (respostas a este e-mail não entram no chamado).");
            sender.send(msg);
        } catch (Exception e) {
            log.error("Falha ao enviar aviso de resposta de chamado: {}", e.getMessage());
        }
    }

    /** Fase 34: avisa o suporte que o armazém respondeu um chamado. Nunca lança exceção. */
    public void avisarSuporteChamado(String para, String codigo, String nomeArmazem, String assunto, String texto, String link) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente == null || remetente.isBlank() || para == null || para.isBlank()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(remetente);
            msg.setTo(para);
            msg.setSubject(("[CHAMADO " + codigo + "] Nova mensagem de " + (nomeArmazem == null ? "um armazém" : nomeArmazem)
                    + " - " + assunto).replaceAll("[\\r\\n]+", " "));
            msg.setText((nomeArmazem == null ? "O armazém" : nomeArmazem) + " respondeu o chamado " + codigo + " (" + assunto + "):\n\n"
                    + texto + "\n\n"
                    + (link == null ? "Responda pelo painel Master > Chamados." : "Responda pelo painel Master: " + link)
                    + "\n(Resposta dada só por este e-mail não aparece para o armazém.)");
            sender.send(msg);
        } catch (Exception e) {
            log.error("Falha ao avisar o suporte sobre resposta de chamado: {}", e.getMessage());
        }
    }
}
