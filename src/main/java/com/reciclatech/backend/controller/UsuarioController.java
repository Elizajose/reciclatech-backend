package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.service.DatabaseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/** API interna: exige login do armazém (antes qualquer pessoa podia listar e criar). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UsuarioController.class);

    @Autowired
    private DatabaseService googleSheetsService;

    @PostMapping
    public ResponseEntity<String> criar(@RequestBody Usuario usuario, HttpSession session) {
        if (session.getAttribute("adminLogado") == null) {
            return ResponseEntity.status(401).body("Não autenticado");
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return ResponseEntity.status(403).body("Assinatura pendente.");
        try {
            usuario.setId(null); // sempre cria um novo; impede sobrescrever registro existente pelo id
            googleSheetsService.salvarUsuario(usuario);
            return ResponseEntity.ok("Agendamento realizado com sucesso!");
        } catch (IOException e) {
            // o detalhe técnico vai só para o log do servidor, nunca para quem chamou a API
            log.error("Erro ao salvar usuário pela API", e);
            return ResponseEntity.status(500).body("Não foi possível salvar. Tente novamente.");
        }
    }

    @GetMapping
    public ResponseEntity<List<Usuario>> listar(HttpSession session) {
        if (session.getAttribute("adminLogado") == null) {
            return ResponseEntity.status(401).build();
        }
        if (session.getAttribute("bloqueadoPagamento") != null) return ResponseEntity.status(403).build();
        try {
            List<Usuario> usuarios = googleSheetsService.listarUsuarios();
            return ResponseEntity.ok(usuarios);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }
}
