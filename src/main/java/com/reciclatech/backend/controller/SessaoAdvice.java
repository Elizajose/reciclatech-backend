package com.reciclatech.backend.controller;

import com.reciclatech.backend.service.SessaoInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

@ControllerAdvice
public class SessaoAdvice {

    /** Sem sessão: API recebe 401; telas voltam para o login. */
    @ExceptionHandler(SessaoInvalidaException.class)
    public void semSessao(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getRequestURI().startsWith("/api/")) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            res.sendRedirect("/login");
        }
    }
}
