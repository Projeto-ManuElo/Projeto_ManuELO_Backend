package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.Projeto.manuelo.config.AdminInicial;

/**
 * A-14: o administrador inicial de {@code manuelo.admin.email/senha} deve existir
 * no Postgres após a subida do contexto, com perfil/status/aprovação coerentes e
 * senha BCrypt; reexecutar o bootstrap não pode duplicar o usuário.
 *
 * Usa o admin já criado pelo {@link AdminInicial} no boot (não apaga: outros testes
 * dependem dele) e o {@code qa@manuelo.local / qa-admin-123} do perfil de teste.
 */
@SpringBootTest
@ActiveProfiles("test")
class BootstrapAdminTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AdminInicial adminInicial;

	@Value("${manuelo.admin.email}")
	private String email;

	@Value("${manuelo.admin.senha}")
	private String senha;

	@Test
	void adminConfiguradoExisteComPerfilStatusEAprovacao() {
		Map<String, Object> admin = buscarAdmin();

		assertThat(admin)
				.as("o admin de %s deve existir após o AdminInicial rodar no boot", email)
				.isNotNull();
		assertThat(admin.get("perfil")).isEqualTo("ADMINISTRADOR");
		assertThat(admin.get("status")).isEqualTo("ATIVO");
		assertThat(admin.get("aprovado_em"))
				.as("usuário ATIVO precisa de aprovado_em (V002)")
				.isNotNull();
		assertThat(admin.get("aprovado_por"))
				.as("usuário ATIVO precisa de aprovado_por (V002)")
				.isNotNull();
	}

	@Test
	void senhaGravadaEhBCryptENaoGuardaTextoPuro() {
		String hash = (String) buscarAdmin().get("senha_hash");

		assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
		assertThat(hash)
				.as("senha_hash deve ser um hash BCrypt")
				.startsWith("$2");
		assertThat(hash)
				.as("a senha em texto puro não pode aparecer na coluna")
				.isNotEqualTo(senha)
				.doesNotContain(senha);
		assertThat(passwordEncoder.matches(senha, hash))
				.as("o hash gravado deve conferir com a senha configurada")
				.isTrue();
		assertThat(passwordEncoder.matches("senha-incorreta", hash)).isFalse();
	}

	@Test
	void reexecutarAdminInicialNaoDuplica() {
		assertThat(contarAdmin()).isEqualTo(1);

		adminInicial.run(null);

		assertThat(contarAdmin())
				.as("rodar o bootstrap de novo deve ser idempotente")
				.isEqualTo(1);
	}

	private Map<String, Object> buscarAdmin() {
		return jdbc.queryForList("""
				SELECT CAST(perfil AS text) AS perfil,
				       CAST(status AS text) AS status,
				       aprovado_em,
				       aprovado_por,
				       senha_hash
				  FROM usuarios
				 WHERE lower(email) = lower(?)
				""", email).stream().findFirst().orElse(null);
	}

	private int contarAdmin() {
		Integer total = jdbc.queryForObject(
				"SELECT count(*) FROM usuarios WHERE lower(email) = lower(?)", Integer.class, email);
		return total == null ? 0 : total;
	}

}
