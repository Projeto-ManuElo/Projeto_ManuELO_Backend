package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;
import com.Projeto.manuelo.seguranca.CadastroUsuarioService;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CadastroUsuarioPersistenciaTest {

    private static final String EMAIL = "qa-persistencia-" + UUID.randomUUID() + "@manuelo.local";
    private static final String SENHA = "senha-1234";

    private static ApplicationContext contextoDaEscrita;
    private static ApplicationContext contextoDaLeitura;

    @Autowired
    private CadastroUsuarioService cadastro;

    @Autowired
    private UsuarioJpaRepository usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void gravaCadastroAntesDeReiniciarContexto() {
        contextoDaEscrita = contexto;
        assertThat(cadastro.cadastrar("QA Persistencia", EMAIL, SENHA)).isTrue();
        assertThat(usuarios.findByEmailIgnoreCase(EMAIL)).isPresent();
    }

    @Test
    @Order(2)
    void leCadastroAposReiniciarContexto() {
        contextoDaLeitura = contexto;
        assertThat(contextoDaLeitura)
                .as("o contexto Spring precisa ser novo na leitura")
                .isNotSameAs(contextoDaEscrita);
        assertThat(usuarios.findByEmailIgnoreCase(EMAIL))
                .as("o cadastro deve continuar no PostgreSQL após reiniciar o contexto")
                .isPresent();
        jdbc.update("DELETE FROM usuarios WHERE lower(email) = lower(?)", EMAIL);
    }
}
