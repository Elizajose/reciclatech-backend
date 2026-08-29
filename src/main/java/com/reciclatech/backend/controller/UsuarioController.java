package com.reciclatech.backend.controller;

import com.reciclatech.backend.model.Usuario;
import com.reciclatech.backend.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private DatabaseService googleSheetsService;

    @PostMapping
    public ResponseEntity<String> criar(@RequestBody Usuario usuario) {
        try {
            googleSheetsService.salvarUsuario(usuario);
            return ResponseEntity.ok("Agendamento realizado com sucesso!");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Erro ao salvar no banco de dados: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Usuario>> listar() {
        try {
            List<Usuario> usuarios = googleSheetsService.listarUsuarios();
            return ResponseEntity.ok(usuarios);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }
}