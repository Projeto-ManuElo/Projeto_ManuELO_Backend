package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.seguranca.EnvioTokenSenha;
import com.Projeto.manuelo.seguranca.RecuperacaoSenhaService;

import jakarta.persistence.EntityManager;

/**
 * A-13 (DEC-I05): recuperação/redefinição de senha por token.
 *
 * Prova que {@link RecuperacaoSenhaService#solicitar(String)} grava apenas o SHA-256
 * (64 hex minúsculos) de um token aleatório, com validade de ~1 hora e uso único, e
 * entrega o token cru via {@link EnvioTokenSenha} somente para e-mail existente.
 * Também prova que {@link RecuperacaoSenhaService#redefinir(String, String)} troca o
 * hash BCrypt uma única vez e recusa token inexistente, expirado, já usado, senha
 * curta e usuário não ATIVO.
 *
 * O teste é vermelho enquanto o serviço e a interface de entrega não existirem. O
 * bean de captura abaixo substitui a entrega real (SMTP/log) e guarda os tokens em
 * memória para as asserções. A transação de teste é revertida ao final, de modo que
 * os tokens criados não precisam (nem podem) ser apagados por causa do trigger.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(RecuperacaoSenhaTest.CapturaEnvioConfig.class)
class RecuperacaoSenhaTest {

	private static final String SENHA_ATUAL = "senha-atual-123";
	private static final String SENHA_NOVA = "nova-senha-123";

	@Autowired
	private RecuperacaoSenhaService recuperacao;

	@Autowired
	private CapturaEnvio capturaEnvio;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void limparEntregas() {
		capturaEnvio.limpar();
	}

	@Test
	void solicitarEmailExistenteGravaUmTokenSha256ComValidadeDeUmaHoraEEntrega() {
		UsuarioCriado usuario = criarUsuario("ATIVO");
		int tokensAntes = contarTokens(usuario.id());

		recuperacao.solicitar(usuario.email());
		sincronizar();

		assertThat(contarTokens(usuario.id()))
				.as("solicitar deve gravar exatamente 1 token novo")
				.isEqualTo(tokensAntes + 1);

		List<Map<String, Object>> linhas = jdbc.queryForList("""
				SELECT token_hash, expira_em, usado_em
				  FROM tokens_redefinicao_senha
				 WHERE usuario_id = ?
				 ORDER BY criado_em DESC
				""", usuario.id());
		assertThat(linhas).hasSize(1);

		String tokenHash = (String) linhas.get(0).get("token_hash");
		assertThat(tokenHash)
				.as("token_hash deve ser SHA-256 em 64 hex minúsculos")
				.matches("[0-9a-f]{64}");

		assertThat(capturaEnvio.totalPara(usuario.email()))
				.as("a entrega deve ser chamada uma vez para o e-mail solicitado")
				.isEqualTo(1);
		String token = capturaEnvio.ultimoToken(usuario.email());
		assertThat(token).as("o token cru não pode ser vazio").isNotBlank();
		assertThat(tokenHash)
				.as("o banco não pode guardar o token cru")
				.isNotEqualTo(token);
		assertThat(tokenHash)
				.as("token_hash deve ser o SHA-256 do token entregue")
				.isEqualTo(sha256Hex(token));

		assertThat(linhas.get(0).get("usado_em"))
				.as("token recém-criado não pode estar marcado como usado")
				.isNull();

		Double segundosAteExpirar = jdbc.queryForObject("""
				SELECT extract(epoch FROM (expira_em - now()))
				  FROM tokens_redefinicao_senha
				 WHERE token_hash = ?
				""", Double.class, tokenHash);
		assertThat(segundosAteExpirar)
				.as("expira_em deve ficar em ~1 hora")
				.isBetween(3500.0, 3700.0);
	}

	@Test
	void solicitarEmailInexistenteRespondeNeutroSemGravarTokenNemEntregar() {
		long tokensAntes = contarTodosTokens();
		String emailInexistente = "qa-rec-inexistente-" + UUID.randomUUID() + "@manuelo.local";

		assertThatCode(() -> recuperacao.solicitar(emailInexistente))
				.as("e-mail inexistente não pode lançar (resposta neutra)")
				.doesNotThrowAnyException();
		sincronizar();

		assertThat(capturaEnvio.entregas())
				.as("não pode haver tentativa de entrega para e-mail inexistente")
				.isEmpty();
		assertThat(contarTodosTokens())
				.as("não pode ser gravado token para e-mail inexistente")
				.isEqualTo(tokensAntes);
	}

	@Test
	void redefinirComTokenValidoTrocaSenhaEMarcaTokenComoUsado() {
		UsuarioCriado usuario = criarUsuario("ATIVO");
		recuperacao.solicitar(usuario.email());
		sincronizar();
		String token = capturaEnvio.ultimoToken(usuario.email());
		String hashAntes = senhaHash(usuario.id());

		assertThat(recuperacao.redefinir(token, SENHA_NOVA))
				.as("token válido com senha válida deve redefinir")
				.isEqualTo(RecuperacaoSenhaService.Resultado.OK);
		sincronizar();

		String hashDepois = senhaHash(usuario.id());
		assertThat(hashDepois)
				.as("senha_hash deve mudar")
				.isNotEqualTo(hashAntes);
		assertThat(passwordEncoder.matches(SENHA_NOVA, hashDepois))
				.as("a nova senha deve conferir com o hash BCrypt gravado")
				.isTrue();
		assertThat(passwordEncoder.matches(SENHA_ATUAL, hashDepois))
				.as("a senha antiga não pode continuar valendo")
				.isFalse();

		assertThat(tokenRow(token).get("usado_em"))
				.as("o token utilizado deve ter usado_em preenchido")
				.isNotNull();
	}

	@Test
	void novaSolicitacaoInvalidaOTokenAnteriorEMantemApenasONovoValido() {
		UsuarioCriado usuario = criarUsuario("ATIVO");

		recuperacao.solicitar(usuario.email());
		sincronizar();
		String tokenAntigo = capturaEnvio.ultimoToken(usuario.email());

		recuperacao.solicitar(usuario.email());
		sincronizar();
		String tokenNovo = capturaEnvio.ultimoToken(usuario.email());

		assertThat(tokenNovo)
				.as("cada solicitação deve gerar um token cru diferente")
				.isNotEqualTo(tokenAntigo);
		assertThat(tokenRow(tokenAntigo).get("usado_em"))
				.as("ao solicitar de novo, o token anterior deve ser marcado como usado")
				.isNotNull();
		assertThat(tokenRow(tokenNovo).get("usado_em"))
				.as("o token mais novo deve continuar pendente")
				.isNull();

		assertThat(recuperacao.redefinir(tokenAntigo, SENHA_NOVA))
				.as("o token invalidado pela nova solicitação não pode redefinir a senha")
				.isEqualTo(RecuperacaoSenhaService.Resultado.INVALIDO);
		sincronizar();
		assertThat(recuperacao.redefinir(tokenNovo, SENHA_NOVA))
				.as("o token mais novo deve continuar funcionando")
				.isEqualTo(RecuperacaoSenhaService.Resultado.OK);
		sincronizar();
	}

	@Test
	void aposRedefinirAutenticaComSenhaNovaENaoComASenhaAntiga() {
		UsuarioCriado usuario = criarUsuario("ATIVO");
		recuperacao.solicitar(usuario.email());
		sincronizar();
		String token = capturaEnvio.ultimoToken(usuario.email());

		assertThat(recuperacao.redefinir(token, SENHA_NOVA))
				.isEqualTo(RecuperacaoSenhaService.Resultado.OK);
		sincronizar();

		Authentication autenticado = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(usuario.email(), SENHA_NOVA));
		assertThat(autenticado.isAuthenticated())
				.as("login com a senha nova deve autenticar")
				.isTrue();
		assertThat(autenticado.getName())
				.as("o e-mail deve ser o username autenticado")
				.isEqualToIgnoringCase(usuario.email());

		assertThatThrownBy(() -> authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(usuario.email(), SENHA_ATUAL)))
				.as("login com a senha antiga deve falhar")
				.isInstanceOf(AuthenticationException.class);
	}

	@Test
	void solicitarUsuarioNaoAtivoNaoGravaTokenNemEntrega() {
		for (String status : List.of("PENDENTE_APROVACAO", "INATIVO")) {
			UsuarioCriado usuario = criarUsuario(status);

			assertThatCode(() -> recuperacao.solicitar(usuario.email()))
					.as("solicitar para usuário %s não pode lançar", status)
					.doesNotThrowAnyException();
			sincronizar();

			assertThat(contarTokens(usuario.id()))
					.as("usuário %s não pode receber token de redefinição", status)
					.isZero();
			assertThat(capturaEnvio.totalPara(usuario.email()))
					.as("usuário %s não pode ter token entregue", status)
					.isZero();
		}
	}

	@Test
	void redefinirDuasVezesOMesmoTokenEhInvalidoENaoTrocaSenhaDeNovo() {
		UsuarioCriado usuario = criarUsuario("ATIVO");
		recuperacao.solicitar(usuario.email());
		sincronizar();
		String token = capturaEnvio.ultimoToken(usuario.email());

		assertThat(recuperacao.redefinir(token, "primeira-senha-123"))
				.isEqualTo(RecuperacaoSenhaService.Resultado.OK);
		sincronizar();
		String hashAposPrimeiroUso = senhaHash(usuario.id());

		assertThat(recuperacao.redefinir(token, "segunda-senha-456"))
				.as("token de uso único não pode ser reutilizado")
				.isEqualTo(RecuperacaoSenhaService.Resultado.INVALIDO);
		sincronizar();

		String hashAposSegundaTentativa = senhaHash(usuario.id());
		assertThat(hashAposSegundaTentativa)
				.as("a tentativa recusada não pode alterar a senha")
				.isEqualTo(hashAposPrimeiroUso);
		assertThat(passwordEncoder.matches("segunda-senha-456", hashAposSegundaTentativa))
				.isFalse();
	}

	@Test
	void tokenExpiradoEhInvalidoENaoTrocaSenha() {
		UsuarioCriado usuario = criarUsuario("ATIVO");
		String token = "token-expirado-" + UUID.randomUUID();
		inserirToken(usuario.id(), token, "-1 minute");
		String hashAntes = senhaHash(usuario.id());

		assertThat(recuperacao.redefinir(token, SENHA_NOVA))
				.as("token com expira_em no passado deve ser inválido")
				.isEqualTo(RecuperacaoSenhaService.Resultado.INVALIDO);
		sincronizar();

		assertThat(senhaHash(usuario.id()))
				.as("token expirado não pode trocar a senha")
				.isEqualTo(hashAntes);
	}

	@Test
	void tokenInexistenteESenhaCurtaSaoInvalidos() {
		UsuarioCriado usuario = criarUsuario("ATIVO");
		String hashAntes = senhaHash(usuario.id());

		assertThat(recuperacao.redefinir("token-aleatorio-" + UUID.randomUUID(), SENHA_NOVA))
				.as("token inexistente deve ser inválido")
				.isEqualTo(RecuperacaoSenhaService.Resultado.INVALIDO);

		recuperacao.solicitar(usuario.email());
		sincronizar();
		String token = capturaEnvio.ultimoToken(usuario.email());

		assertThat(recuperacao.redefinir(token, "curta"))
				.as("senha com menos de 8 caracteres deve ser inválida")
				.isEqualTo(RecuperacaoSenhaService.Resultado.INVALIDO);
		sincronizar();

		assertThat(senhaHash(usuario.id()))
				.as("tentativas inválidas não podem trocar a senha")
				.isEqualTo(hashAntes);
	}

	@Test
	void tokenDeUsuarioInativoOuPendenteEhInvalido() {
		for (String status : List.of("INATIVO", "PENDENTE_APROVACAO")) {
			UsuarioCriado usuario = criarUsuario(status);
			String token = "token-" + status.toLowerCase() + "-" + UUID.randomUUID();
			inserirToken(usuario.id(), token, "1 hour");
			String hashAntes = senhaHash(usuario.id());

			assertThat(recuperacao.redefinir(token, SENHA_NOVA))
					.as("token de usuário %s não pode redefinir a senha", status)
					.isEqualTo(RecuperacaoSenhaService.Resultado.INVALIDO);
			sincronizar();

			assertThat(senhaHash(usuario.id()))
					.as("usuário %s não pode ter a senha alterada", status)
					.isEqualTo(hashAntes);
		}
	}

	private UsuarioCriado criarUsuario(String status) {
		UUID id = UUID.randomUUID();
		String email = "qa-rec-" + id + "@manuelo.local";
		String hash = passwordEncoder.encode(SENHA_ATUAL);
		if ("ATIVO".equals(status)) {
			jdbc.update("""
					INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status,
					                      aprovado_por, aprovado_em)
					VALUES (?, 'QA Recuperação', ?, ?, 'COLABORADOR'::perfil_usuario,
					        ?::status_usuario, ?, now())
					""", id, email, hash, status, id);
		} else {
			jdbc.update("""
					INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status)
					VALUES (?, 'QA Recuperação', ?, ?, 'COLABORADOR'::perfil_usuario,
					        ?::status_usuario)
					""", id, email, hash, status);
		}
		return new UsuarioCriado(id, email);
	}

	private void inserirToken(UUID usuarioId, String token, String expiraEmIntervalo) {
		jdbc.update("""
				INSERT INTO tokens_redefinicao_senha (usuario_id, token_hash, expira_em, criado_em)
				VALUES (?, ?, now() + CAST(? AS interval), now() - interval '2 hours')
				""", usuarioId, sha256Hex(token), expiraEmIntervalo);
	}

	private int contarTokens(UUID usuarioId) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM tokens_redefinicao_senha WHERE usuario_id = ?",
				Integer.class, usuarioId);
	}

	private long contarTodosTokens() {
		return jdbc.queryForObject("SELECT count(*) FROM tokens_redefinicao_senha", Long.class);
	}

	private String senhaHash(UUID usuarioId) {
		return jdbc.queryForObject(
				"SELECT senha_hash FROM usuarios WHERE id = ?", String.class, usuarioId);
	}

	private Map<String, Object> tokenRow(String token) {
		return jdbc.queryForMap("""
				SELECT token_hash, expira_em, usado_em
				  FROM tokens_redefinicao_senha
				 WHERE token_hash = ?
				""", sha256Hex(token));
	}

	/**
	 * Garante que escritas via JPA (se o serviço usar isso) estejam visíveis para as
	 * leituras JDBC de asserção dentro da mesma transação de teste.
	 */
	private void sincronizar() {
		entityManager.flush();
	}

	private static String sha256Hex(String valor) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(valor.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível", ex);
		}
	}

	private record UsuarioCriado(UUID id, String email) {
	}

	static class CapturaEnvio implements EnvioTokenSenha {

		private final List<Entrega> entregas = new CopyOnWriteArrayList<>();

		@Override
		public void enviar(String email, String token) {
			entregas.add(new Entrega(email, token));
		}

		void limpar() {
			entregas.clear();
		}

		List<Entrega> entregas() {
			return List.copyOf(entregas);
		}

		long totalPara(String email) {
			return entregas.stream().filter(entrega -> email.equals(entrega.email())).count();
		}

		String ultimoToken(String email) {
			for (int i = entregas.size() - 1; i >= 0; i--) {
				if (email.equals(entregas.get(i).email())) {
					return entregas.get(i).token();
				}
			}
			throw new AssertionError("nenhum token foi entregue para " + email);
		}

		record Entrega(String email, String token) {
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class CapturaEnvioConfig {

		@Bean
		@Primary
		CapturaEnvio capturaEnvio() {
			return new CapturaEnvio();
		}
	}
}
