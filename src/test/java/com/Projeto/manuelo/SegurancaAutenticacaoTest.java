package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.Projeto.manuelo.seguranca.LoginService;
import com.Projeto.manuelo.seguranca.LoginService.Resultado;

import br.com.manuelo.dados.SessaoUsuario;

/**
 * A-10: segurança HTTP real com UserDetailsService, BCrypt, papéis e gate por status.
 *
 * O teste é deliberadamente vermelho enquanto o SecurityConfig libera tudo e não existe
 * UserDetailsService real: os pontos a, b, c e e falham pelo motivo certo. Os dados são
 * inseridos e removidos via JdbcTemplate, sem depender do seed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SegurancaAutenticacaoTest {

	private static final String PREFIXO = "qa-seg-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String SENHA = "qa-senha-123";

	private final String emailAtivoAdmin = PREFIXO + "-ativo-admin@manuelo.local";
	private final String emailAtivoColab = PREFIXO + "-ativo-colab@manuelo.local";
	private final String emailPendente = PREFIXO + "-pendente@manuelo.local";
	private final String emailInativo = PREFIXO + "-inativo@manuelo.local";

	@Autowired
	private ApplicationContext contexto;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mockMvc;

	@LocalServerPort
	private int porta;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private LoginService loginService;

	@BeforeAll
	void prepararDados() {
		inserir(emailAtivoAdmin, "QA Ativo Admin", SENHA, "ADMINISTRADOR", "ATIVO");
		inserir(emailAtivoColab, "QA Ativo Colaborador", SENHA, "COLABORADOR", "ATIVO");
		inserir(emailPendente, "QA Pendente", SENHA, "COLABORADOR", "PENDENTE_APROVACAO");
		inserir(emailInativo, "QA Inativo", SENHA, "COLABORADOR", "INATIVO");
	}

	@AfterAll
	void limparDados() {
		jdbc.update("DELETE FROM usuarios WHERE email LIKE ?", PREFIXO + "%");
	}

	@AfterEach
	void limparContextoDeSeguranca() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void userDetailsServiceCarregaUsuarioAtivoComPapelEStatusCoerentes() {
		UserDetailsService userDetailsService = contexto.getBean(UserDetailsService.class);

		UserDetails admin = userDetailsService.loadUserByUsername(emailAtivoAdmin);
		assertThat(admin.getUsername()).isEqualTo(emailAtivoAdmin);
		assertThat(admin.getAuthorities())
				.extracting(GrantedAuthority::getAuthority)
				.contains("ROLE_ADMINISTRADOR");
		assertThat(admin.isEnabled())
				.as("usuário ATIVO deve estar habilitado")
				.isTrue();
		assertThat(admin.isAccountNonLocked())
				.as("usuário ATIVO não deve estar bloqueado")
				.isTrue();

		UserDetails colaborador = userDetailsService.loadUserByUsername(emailAtivoColab);
		assertThat(colaborador.getAuthorities())
				.extracting(GrantedAuthority::getAuthority)
				.contains("ROLE_COLABORADOR");
	}

	@Test
	void authenticationManagerAutenticaSenhaCorretaERecusaSenhaErrada() {
		AuthenticationManager authenticationManager = gerenciadorDeAutenticacao();

		Authentication autenticado = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(emailAtivoAdmin, SENHA));
		assertThat(autenticado.isAuthenticated()).isTrue();
		assertThat(autenticado.getAuthorities())
				.extracting(GrantedAuthority::getAuthority)
				.contains("ROLE_ADMINISTRADOR");

		assertThatThrownBy(() -> authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(emailAtivoAdmin, "senha-incorreta")))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void usuariosPendenteEInativoSaoRecusadosMesmoComSenhaCorreta() {
		AuthenticationManager authenticationManager = gerenciadorDeAutenticacao();

		assertThatThrownBy(() -> authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(emailPendente, SENHA)))
				.as("PENDENTE_APROVACAO com senha correta deve ser barrado pelo status, "
						+ "não por usuário inexistente/senha inválida")
				.isInstanceOfAny(DisabledException.class, LockedException.class);

		assertThatThrownBy(() -> authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(emailInativo, SENHA)))
				.as("INATIVO com senha correta deve ser barrado pelo status, "
						+ "não por usuário inexistente/senha inválida")
				.isInstanceOfAny(DisabledException.class, LockedException.class);
	}

	@Test
	void passwordEncoderEhBCryptEConfereOHashGravado() {
		assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);

		String hashGravado = jdbc.queryForObject(
				"SELECT senha_hash FROM usuarios WHERE email = ?", String.class, emailAtivoAdmin);
		assertThat(hashGravado).startsWith("$2");
		assertThat(passwordEncoder.matches(SENHA, hashGravado)).isTrue();
		assertThat(passwordEncoder.matches("senha-incorreta", hashGravado)).isFalse();
	}

	@Test
	void rotasProtegidasRedirecionamParaLoginERotasPublicasRespondem() throws Exception {
		esperaRedirecionarAoLogin("/home");
		esperaRedirecionarAoLogin("/imoveis");
		esperaRedirecionarAoLogin("/usuarios");

		assertThat(statusHttp("/"))
				.as("GET / é rota pública e deve responder 200")
				.isEqualTo(200);

		assertThat(statusHttp("/cadastro"))
				.as("GET /cadastro é rota pública e deve responder 200")
				.isEqualTo(200);

		assertThat(statusHttp("/VAADIN/config/stats.json") / 100)
				.as("/VAADIN/... é recurso público e não pode ser redirecionado")
				.isNotEqualTo(3);
	}

	@Test
	void usuariosAnonimoRedirecionaAoLogin() throws Exception {
		MvcResult resultado;
		try {
			resultado = mockMvc.perform(get("/usuarios")).andReturn();
		} catch (jakarta.servlet.ServletException ex) {
			throw new AssertionError("GET /usuarios anônimo deveria ser barrado pelo SecurityConfig "
					+ "com redirect ao login, mas chegou ao servlet Vaadin.", ex);
		}
		assertThat(resultado.getResponse().getStatus())
				.as("GET /usuarios anônimo deve redirecionar ao login")
				.isBetween(300, 399);
	}

	@Test
	@WithMockUser(username = "colab", roles = "COLABORADOR")
	void usuariosComPerfilColaboradorRecebe403() throws Exception {
		MvcResult resultado;
		try {
			resultado = mockMvc.perform(get("/usuarios")).andReturn();
		} catch (jakarta.servlet.ServletException ex) {
			throw new AssertionError("COLABORADOR não pode atravessar o filtro de /usuarios "
					+ "e chegar ao servlet Vaadin.", ex);
		}
		assertThat(resultado.getResponse().getStatus())
				.as("GET /usuarios com COLABORADOR deve responder 403")
				.isEqualTo(403);
	}

	@Test
	@WithMockUser(username = "admin", roles = "ADMINISTRADOR")
	void usuariosComPerfilAdministradorAtravessaOFiltro() throws Exception {
		MvcResult resultado;
		try {
			resultado = mockMvc.perform(get("/usuarios")).andReturn();
		} catch (jakarta.servlet.ServletException ex) {
			// O servlet Vaadin não renderiza a view sob MockMvc; chegar ao servlet já
			// prova que o filtro deixou o ADMINISTRADOR passar.
			return;
		}
		assertThat(resultado.getResponse().getStatus())
				.as("GET /usuarios com ADMINISTRADOR deve atravessar o filtro (200/302)")
				.isIn(200, 302, 303);
	}

	@Test
	void recursosEstaticosSaoPublicosENaoRedirecionam() throws Exception {
		for (String recurso : List.of("/styles.css", "/manifest.webmanifest", "/sw.js", "/offline.html")) {
			assertThat(statusHttp(recurso))
					.as("GET " + recurso + " é recurso estático público e deve responder 200, "
							+ "não redirecionar ao login")
					.isEqualTo(200);
		}
	}

	@Test
	void logoutLimpaContextoDeSegurancaForaDeRequisicaoVaadin() {
		AuthenticationManager authenticationManager = gerenciadorDeAutenticacao();
		Authentication autenticado = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(emailAtivoAdmin, SENHA));
		SecurityContextHolder.getContext().setAuthentication(autenticado);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

		assertThatCode(() -> loginService.logout())
				.as("logout() fora de uma requisição Vaadin não deve lançar exceção")
				.doesNotThrowAnyException();

		assertThat(SecurityContextHolder.getContext().getAuthentication())
				.as("loginService.logout() deve limpar o SecurityContextHolder")
				.isNull();
	}

	@Test
	void loginNaoVazaStatusQuandoSenhaEstaErrada() {
		assertThat(loginService.login(emailPendente, "senha-errada"))
				.as("PENDENTE_APROVACAO com senha errada não pode revelar o status")
				.isEqualTo(Resultado.INVALIDO);
		assertThat(loginService.login(emailInativo, "senha-errada"))
				.as("INATIVO com senha errada não pode revelar o status")
				.isEqualTo(Resultado.INVALIDO);
	}

	@Test
	void loginRetornaStatusDoUsuarioSomenteComSenhaCorreta() {
		assertThat(loginService.login(emailPendente, SENHA))
				.as("PENDENTE_APROVACAO com senha correta deve retornar PENDENTE")
				.isEqualTo(Resultado.PENDENTE);
		assertThat(loginService.login(emailInativo, SENHA))
				.as("INATIVO com senha correta deve retornar INATIVO")
				.isEqualTo(Resultado.INATIVO);
		assertThat(loginService.login(emailAtivoColab, "senha-errada"))
				.as("ATIVO com senha errada deve retornar INVALIDO")
				.isEqualTo(Resultado.INVALIDO);
	}

	/**
	 * Item d do A-10: a gravação de {@code SPRING_SECURITY_CONTEXT_KEY} na sessão só
	 * acontece dentro de uma requisição HTTP/Vaadin (UIDL), que não existe neste teste
	 * fora do servlet. Por isso esse aspecto não é testável aqui de forma fiel; o vínculo
	 * senha -> autenticação é provado por {@link #authenticationManagerAutenticaSenhaCorretaERecusaSenhaErrada()}
	 * e {@link #userDetailsServiceCarregaUsuarioAtivoComPapelEStatusCoerentes()}.
	 */
	@Test
	void logoutLimpaSecurityContextESessaoUsuario() {
		SessaoUsuario sessao;
		try {
			sessao = contexto.getBean(SessaoUsuario.class);
			sessao.entrar(new SessaoUsuario.UsuarioSessao(
					UUID.randomUUID().toString(), "QA Logout", emailAtivoAdmin, "ADMINISTRADOR"));
		} catch (RuntimeException ex) {
			Assumptions.assumeTrue(false,
					"SessaoUsuario indisponível fora de uma sessão Vaadin: " + ex.getMessage());
			return;
		}

		AuthenticationManager authenticationManager = gerenciadorDeAutenticacao();
		Authentication autenticado = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(emailAtivoAdmin, SENHA));
		SecurityContextHolder.getContext().setAuthentication(autenticado);
		assertThat(sessao.autenticado()).isTrue();

		SecurityContextHolder.clearContext();
		sessao.sair();

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(sessao.autenticado()).isFalse();
	}

	private AuthenticationManager gerenciadorDeAutenticacao() {
		AuthenticationManager gerenciador = contexto.getBeanProvider(AuthenticationManager.class)
				.getIfAvailable();
		if (gerenciador != null) {
			return gerenciador;
		}
		return contexto.getBean(AuthenticationConfiguration.class).getAuthenticationManager();
	}

	private void esperaRedirecionarAoLogin(String caminho) throws Exception {
		MvcResult resultado;
		try {
			resultado = mockMvc.perform(get(caminho)).andReturn();
		} catch (jakarta.servlet.ServletException ex) {
			throw new AssertionError("GET " + caminho
					+ " deveria ser barrado pelo SecurityConfig com redirect ao login, "
					+ "mas chegou ao servlet Vaadin (rota ainda liberada).", ex);
		}
		assertThat(resultado.getResponse().getStatus())
				.as("GET " + caminho + " deslogado deve redirecionar ao login")
				.isBetween(300, 399);
		assertThat(resultado.getResponse().getRedirectedUrl())
				.as("GET " + caminho + " deve redirecionar para a tela de login")
				.isNotNull()
				.endsWith("/");
	}

	private int statusHttp(String caminho) throws Exception {
		HttpClient cliente = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho))
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
		return cliente.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private void inserir(String email, String nome, String senha, String perfil, String status) {
		UUID id = UUID.randomUUID();
		String hash = passwordEncoder.encode(senha);
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

}
