package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;
import com.Projeto.manuelo.seguranca.UsuarioAdminService;

import br.com.manuelo.dados.AutenticacaoService;

/**
 * A-11 (cadastro real) e A-12 (gestão de usuários/perfil) contra o Postgres real.
 *
 * A-11 prova que {@link AutenticacaoService#cadastrar} grava PENDENTE_APROVACAO/COLABORADOR
 * com hash BCrypt e recusa e-mail repetido (índice único em lower(email)).
 * A-12 prova {@link UsuarioAdminService}: listagem com pendentes primeiro, aprovação com
 * aprovado_por/em, alteração de perfil auditada, inativação (sem auto-inativação) e o
 * efeito no UserDetailsService. O teste é vermelho enquanto o serviço não existir.
 */
@SpringBootTest
@ActiveProfiles("test")
class CadastroUsuariosTest {

	private static final String PREFIXO = "qa-cadastro-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String SENHA = "senha-1234";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UsuarioJpaRepository usuarios;

	@Autowired
	private AutenticacaoService autenticacao;

	@Autowired
	private UsuarioAdminService adminService;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private ApplicationContext contexto;

	@AfterEach
	void limpar() {
		SecurityContextHolder.clearContext();
		jdbc.update("""
				UPDATE usuarios
				   SET status = 'INATIVO', aprovado_por = NULL, aprovado_em = NULL,
				       atualizado_por = NULL, criado_por = NULL
				 WHERE email ILIKE ?
				""", PREFIXO + "%");
		jdbc.update("DELETE FROM usuarios WHERE email ILIKE ?", PREFIXO + "%");
	}

	@Test
	void cadastroRealGravaPendenteColaboradorComHashBcrypt() {
		String email = PREFIXO + "-cadastro@manuelo.local";

		assertThat(autenticacao.cadastrar("QA Cadastro", email, SENHA))
				.as("cadastro com dados válidos deve gravar")
				.isTrue();

		Usuario usuario = usuarios.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new AssertionError("o cadastro deve existir no PostgreSQL"));
		assertThat(usuario.getStatus()).isEqualTo(StatusUsuario.PENDENTE_APROVACAO);
		assertThat(usuario.getPerfil()).isEqualTo(PerfilUsuario.COLABORADOR);

		String hash = jdbc.queryForObject(
				"SELECT senha_hash FROM usuarios WHERE lower(email) = lower(?)", String.class, email);
		assertThat(hash).as("senha deve ser gravada como BCrypt").startsWith("$2");
		assertThat(passwordEncoder.matches(SENHA, hash)).isTrue();
		assertThat(passwordEncoder.matches("senha-errada", hash)).isFalse();
	}

	@Test
	void cadastroRecusaEmailDuplicadoIgnorandoCaixa() {
		String email = PREFIXO + "-duplicado@manuelo.local";

		assertThat(autenticacao.cadastrar("QA Primeiro", email, SENHA)).isTrue();
		assertThat(autenticacao.cadastrar("QA Segundo", email.toUpperCase(Locale.ROOT), SENHA))
				.as("índice único lower(email) deve recusar o mesmo e-mail em outra caixa")
				.isFalse();

		Integer total = jdbc.queryForObject(
				"SELECT count(*) FROM usuarios WHERE lower(email) = lower(?)", Integer.class, email);
		assertThat(total).isEqualTo(1);
	}

	@Test
	void listarIncluiCadastrosEOrdenaPendentesPrimeiro() {
		Cenario cenario = prepararCenario();

		var lista = adminService.listar();

		int indicePendente = -1;
		int indiceAdmin = -1;
		for (int i = 0; i < lista.size(); i++) {
			if (cenario.pendenteEmail().equals(lista.get(i).email())) {
				indicePendente = i;
			}
			if (cenario.adminEmail().equals(lista.get(i).email())) {
				indiceAdmin = i;
			}
		}
		assertThat(indicePendente).as("o pendente deve aparecer na listagem").isGreaterThanOrEqualTo(0);
		assertThat(indiceAdmin).as("o admin ativo deve aparecer na listagem").isGreaterThanOrEqualTo(0);
		assertThat(indicePendente)
				.as("pendentes devem ser listados antes dos ativos")
				.isLessThan(indiceAdmin);

		for (int i = 1; i < lista.size(); i++) {
			assertThat(peso(lista.get(i).status()))
					.as("a listagem deve seguir a ordem PENDENTE < ATIVO < INATIVO")
					.isGreaterThanOrEqualTo(peso(lista.get(i - 1).status()));
		}
	}

	@Test
	void aprovarAtivaPendenteRegistrandoAprovador() {
		Cenario cenario = prepararCenario();

		adminService.aprovar(cenario.pendenteId());

		var linha = jdbc.queryForMap("""
				SELECT status::text AS status, aprovado_por, aprovado_em
				  FROM usuarios WHERE id = ?
				""", cenario.pendenteId());
		assertThat(linha.get("status")).isEqualTo("ATIVO");
		assertThat(linha.get("aprovado_por")).isEqualTo(cenario.adminId());
		assertThat(linha.get("aprovado_em")).isNotNull();
	}

	@Test
	void alterarPerfilPersisteEAuditaAtor() {
		Cenario cenario = prepararCenario();

		adminService.alterarPerfil(cenario.pendenteId(), PerfilUsuario.ADMINISTRADOR);

		var linha = jdbc.queryForMap("""
				SELECT perfil::text AS perfil, atualizado_por
				  FROM usuarios WHERE id = ?
				""", cenario.pendenteId());
		assertThat(linha.get("perfil")).isEqualTo("ADMINISTRADOR");
		assertThat(linha.get("atualizado_por"))
				.as("alterarPerfil deve auditar o ator autenticado em atualizado_por")
				.isEqualTo(cenario.adminId());
	}

	@Test
	void inativarNaoPermiteOProprioAtorMasInativaOutro() {
		Cenario cenario = prepararCenario();

		assertThatThrownBy(() -> adminService.inativar(cenario.adminId()))
				.as("um administrador não pode inativar a si mesmo")
				.isInstanceOf(RuntimeException.class);
		assertThat(statusDe(cenario.adminId()))
				.as("a tentativa recusada não pode alterar o status do ator")
				.isEqualTo("ATIVO");

		adminService.inativar(cenario.pendenteId());
		assertThat(statusDe(cenario.pendenteId())).isEqualTo("INATIVO");
	}

	@Test
	void userDetailsServiceCarregaAprovadoComoEnabled() {
		Cenario cenario = prepararCenario();

		adminService.aprovar(cenario.pendenteId());

		UserDetails aprovado = userDetailsService.loadUserByUsername(cenario.pendenteEmail());
		assertThat(aprovado.isEnabled())
				.as("usuário aprovado deve poder autenticar (enabled)")
				.isTrue();
		assertThat(aprovado.isAccountNonLocked()).isTrue();
	}

	@Test
	void colaboradorAtivoNaoPodeListarNemAlterarPerfil() {
		Cenario cenario = prepararCenario();
		String colabEmail = PREFIXO + "-" + UUID.randomUUID().toString().substring(0, 8)
				+ "-colab@manuelo.local";
		UUID colabId = UUID.randomUUID();
		inserirUsuario(colabId, "QA Colaborador", colabEmail, "COLABORADOR", "ATIVO");

		Authentication colab = gerenciadorDeAutenticacao().authenticate(
				new UsernamePasswordAuthenticationToken(colabEmail, SENHA));
		SecurityContextHolder.getContext().setAuthentication(colab);

		assertThatThrownBy(() -> adminService.listar())
				.as("COLABORADOR autenticado não pode listar usuários")
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> adminService.alterarPerfil(cenario.pendenteId(), PerfilUsuario.ADMINISTRADOR))
				.as("COLABORADOR autenticado não pode alterar perfil")
				.isInstanceOf(AccessDeniedException.class);
		String perfilAlvo = jdbc.queryForObject(
				"SELECT perfil::text FROM usuarios WHERE id = ?", String.class, cenario.pendenteId());
		assertThat(perfilAlvo)
				.as("a tentativa recusada não pode alterar o perfil do alvo")
				.isEqualTo("COLABORADOR");
	}

	@Test
	void semAutenticacaoServicoDeAdminExigeUsuarioAutenticado() {
		Cenario cenario = prepararCenario();
		SecurityContextHolder.clearContext();

		assertThatThrownBy(() -> adminService.listar())
				.as("sem SecurityContext o serviço deve exigir autenticação")
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> adminService.alterarPerfil(cenario.pendenteId(), PerfilUsuario.COLABORADOR))
				.as("sem SecurityContext o serviço deve exigir autenticação")
				.isInstanceOf(IllegalStateException.class);
	}

	private Cenario prepararCenario() {
		String sufixo = UUID.randomUUID().toString().substring(0, 8);
		String adminEmail = PREFIXO + "-" + sufixo + "-admin@manuelo.local";
		String pendenteEmail = PREFIXO + "-" + sufixo + "-pendente@manuelo.local";
		UUID adminId = UUID.randomUUID();
		UUID pendenteId = UUID.randomUUID();

		inserirUsuario(adminId, "QA Admin", adminEmail, "ADMINISTRADOR", "ATIVO");
		inserirUsuario(pendenteId, "QA Pendente", pendenteEmail, "COLABORADOR",
				"PENDENTE_APROVACAO");

		Authentication autenticacao = gerenciadorDeAutenticacao().authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, SENHA));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);

		return new Cenario(adminId, adminEmail, pendenteId, pendenteEmail);
	}

	private void inserirUsuario(UUID id, String nome, String email, String perfil, String status) {
		String hash = passwordEncoder.encode(SENHA);
		if ("ATIVO".equals(status)) {
			jdbc.update("""
					INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status,
					                      aprovado_por, aprovado_em)
					VALUES (?, ?, ?, ?, ?::perfil_usuario, ?::status_usuario, ?, now())
					""", id, nome, email, hash, perfil, status, id);
		} else {
			jdbc.update("""
					INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status)
					VALUES (?, ?, ?, ?, ?::perfil_usuario, ?::status_usuario)
					""", id, nome, email, hash, perfil, status);
		}
	}

	private String statusDe(UUID id) {
		return jdbc.queryForObject(
				"SELECT status::text FROM usuarios WHERE id = ?", String.class, id);
	}

	private AuthenticationManager gerenciadorDeAutenticacao() {
		AuthenticationManager gerenciador = contexto.getBeanProvider(AuthenticationManager.class)
				.getIfAvailable();
		if (gerenciador != null) {
			return gerenciador;
		}
		return contexto.getBean(AuthenticationConfiguration.class).getAuthenticationManager();
	}

	private static int peso(String status) {
		return switch (status) {
			case "PENDENTE_APROVACAO" -> 0;
			case "ATIVO" -> 1;
			case "INATIVO" -> 2;
			default -> 3;
		};
	}

	private record Cenario(UUID adminId, String adminEmail, UUID pendenteId, String pendenteEmail) {
	}
}
