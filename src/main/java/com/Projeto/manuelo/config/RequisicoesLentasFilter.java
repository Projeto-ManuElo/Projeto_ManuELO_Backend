package com.Projeto.manuelo.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A-91/DEC-I16: mede o tempo das requisições dinâmicas para comprovar em produção
 * a meta de 1-3 s por navegação. Não altera a resposta e ignora estáticos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequisicoesLentasFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequisicoesLentasFilter.class);
    private static final long LIMITE_MS = 1_500L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long inicio = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000L;
            if (duracaoMs >= LIMITE_MS && !estatico(request.getRequestURI())) {
                LOG.warn("Requisição lenta: {} {} -> {} em {} ms",
                        request.getMethod(), request.getRequestURI(), response.getStatus(),
                        duracaoMs);
            }
        }
    }

    private static boolean estatico(String uri) {
        if (uri == null) {
            return false;
        }
        if (uri.startsWith("/VAADIN/") || uri.startsWith("/frontend/")
                || uri.startsWith("/aura/") || uri.startsWith("/icons/")
                || uri.startsWith("/images/")) {
            return true;
        }
        return uri.endsWith(".js") || uri.endsWith(".css") || uri.endsWith(".png")
                || uri.endsWith(".jpg") || uri.endsWith(".jpeg") || uri.endsWith(".svg")
                || uri.endsWith(".ico") || uri.endsWith(".woff") || uri.endsWith(".woff2")
                || uri.endsWith(".map");
    }
}
