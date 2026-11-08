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
}
