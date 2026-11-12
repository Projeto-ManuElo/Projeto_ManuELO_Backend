package com.Projeto.manuelo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o administrador inicial na primeira subida. A senha vem de
 * MANUELO_ADMIN_SENHA (padrão de desenvolvimento: admin123) e nunca é gravada no código.
 * O seed fictício mantém hashes inválidos de propósito; este usuário é o acesso real de dev.
 */
@Component
public class AdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final String email;
    private final String senha;

    public AdminInicial(JdbcTemplate jdbcTemplate, PasswordEncoder encoder,
            @Value("${manuelo.admin.email}") String email,
            @Value("${manuelo.admin.senha}") String senha) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.encoder = encoder;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || senha == null || senha.length() < 8) {
            log.warn("Administrador inicial não criado: informe MANUELO_ADMIN_EMAIL e "
                    + "MANUELO_ADMIN_SENHA (mínimo 8 caracteres).");
            return;
        }
        Integer existentes = jdbc.sql("SELECT count(*) FROM usuarios WHERE lower(email) = lower(:email)")
                .param("email", email)
                .query(Integer.class)
                .single();
        if (existentes != null && existentes > 0) {
            return;
        }
        jdbc.sql("""
                WITH novo AS (SELECT gen_random_uuid() AS id)
                INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status,
                                      aprovado_por, aprovado_em)
                SELECT id, 'Administrador', :email, :senha, 'ADMINISTRADOR', 'ATIVO', id, now()
                  FROM novo
                """)
                .param("email", email.trim())
                .param("senha", encoder.encode(senha))
                .update();
        log.info("Administrador inicial criado: {}", email);
    }
}
