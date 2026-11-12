package com.Projeto.manuelo.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Auditoria JPA (DEC-I11): criado_por/atualizado_por vêm do SecurityContext.
 * Sem autenticação o auditor é vazio; nenhum usuário é inventado.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

    private final JdbcClient jdbc;

    public JpaAuditingConfig(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Bean
    DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Bean
    AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof UUID uuid) {
                return Optional.of(uuid);
            }

            String identificador = null;
            if (principal instanceof UserDetails userDetails) {
                identificador = userDetails.getUsername();
            } else if (principal instanceof String texto) {
                identificador = texto;
            }

            if (identificador == null || identificador.isBlank()
                    || "anonymousUser".equals(identificador)) {
                return Optional.empty();
            }

            try {
                return Optional.of(UUID.fromString(identificador));
            } catch (IllegalArgumentException ignorado) {
                // Não é UUID: pode ser o e-mail do usuário autenticado.
            }

            if (identificador.contains("@")) {
                return jdbc.sql("SELECT id FROM usuarios WHERE lower(email) = lower(:email)")
                        .param("email", identificador)
                        .query(UUID.class)
                        .optional();
            }

            return Optional.empty();
        };
    }
}
