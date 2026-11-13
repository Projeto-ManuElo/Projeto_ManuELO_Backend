package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.servico.ContratoCadastroService;
import com.Projeto.manuelo.servico.DocumentoService;
import com.Projeto.manuelo.servico.InquilinoCadastroService;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService;

/**
 * A-15: auditoria real (DEC-I11). Prova, contra o Postgres do perfil de teste e via
 * serviços de produção, que criado_por/atualizado_por (e o papel equivalente
 * enviado_por/responsavel_id) vêm do usuário autenticado no SecurityContext, tanto
 * para ADMIN quanto para COLABORADOR, e que uma atualização posterior troca
 * atualizado_por para o novo ator sem mexer no criado_por.
 *
 * Cada método roda na transação do teste e sofre rollback no fim.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditoriaTest {

	private static final String MARCADOR = "QA-A15-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String SENHA_COLABORADOR = "qa-a15-1234";
	private static final byte[] PDF =
			("%PDF-1.4\n% QA A-15 auditoria\nconteudo real\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
	private static final Path DIRETORIO = criarDiretorioTemporario();

	@DynamicPropertySource
	static void armazenamento(DynamicPropertyRegistry registry) {
		registry.add("manuelo.storage.dir", DIRETORIO::toString);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ContratoCadastroService contratoService;

	@Autowired
	private InquilinoCadastroService inquilinoService;

	@Autowired
	private DocumentoService documentoService;

	@Autowired
	private MovimentacaoFinanceiraService movimentacaoService;

	@Value("${manuelo.admin.email}")
	private String adminEmail;

	@Value("${manuelo.admin.senha}")
	private String adminSenha;

	private UUID adminId;
	private UUID colaboradorId;
	private String colaboradorEmail;

	@BeforeEach
	void prepararAtores() {
		autenticarAdmin();
		adminId = jdbc.queryForObject("SELECT id FROM usuarios WHERE lower(email) = lower(?)",
				UUID.class, adminEmail);

		colaboradorEmail = "qa-a15-" + UUID.randomUUID().toString().substring(0, 8)
				+ "@manuelo.local";
		colaboradorId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status,
				                      aprovado_por, aprovado_em)
				VALUES (?, 'QA A15 Colaborador', ?, ?, 'COLABORADOR'::perfil_usuario,
				        'ATIVO'::status_usuario, ?, now())
				""", colaboradorId, colaboradorEmail, passwordEncoder.encode(SENHA_COLABORADOR),
				adminId);
	}

	@AfterEach
	void limparContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void criacaoDeContratoCarimbaCriadoPorDoAdminEmTodasAsTabelas() {
		String endereco = "Rua QA A15 " + UUID.randomUUID().toString().substring(0, 8)
				+ ", 123 - Bairro " + MARCADOR;
		String documentoInquilino = cpfValido();
		String documentoFiador = cpfValido();

		String contratoIdTexto = contratoService.criar(new ContratoCadastroService.Dados(null,
				endereco, nomeUnico("Inquilino"), documentoInquilino, emailUnico("inquilino"),
				"79999999999", nomeUnico("Fiador"), documentoFiador, emailUnico("fiador"),
				"79988887777", false, null, new BigDecimal("1500.00"), 10,
				LocalDate.now().plusMonths(12), new BigDecimal("500.00"), null));

		assertThat(contratoIdTexto).isNotBlank();
		UUID contratoId = UUID.fromString(contratoIdTexto);

		Map<String, Object> contrato = jdbc.queryForMap("""
				SELECT imovel_id, inquilino_id, fiador_id, criado_por, atualizado_por
				  FROM contratos
				 WHERE id = ?
				""", contratoId);
		assertThat(contrato.get("criado_por"))
				.as("A-15: contrato grava o ADMIN logado em criado_por")
				.isEqualTo(adminId);
		assertThat(contrato.get("atualizado_por")).as("criação não inventa atualizador").isNull();

		UUID imovelId = (UUID) contrato.get("imovel_id");
		UUID inquilinoId = (UUID) contrato.get("inquilino_id");
		UUID fiadorId = (UUID) contrato.get("fiador_id");

		assertThat(criadoPor("imoveis", imovelId)).isEqualTo(adminId);
		assertThat(criadoPor("inquilinos", inquilinoId)).isEqualTo(adminId);
		assertThat(criadoPor("fiadores", fiadorId)).isEqualTo(adminId);

		Map<String, Object> caucao = jdbc.queryForMap("""
				SELECT criado_por
				  FROM caucao_movimentacoes
				 WHERE contrato_id = ?
				""", contratoId);
		assertThat(caucao.get("criado_por"))
				.as("A-15: depósito de caução grava o ADMIN logado").isEqualTo(adminId);
	}

	@Test
	void atualizacaoDeInquilinoTrocaAtualizadoPorParaOColaboradorSemAlterarCriadoPor() {
		UUID imovelId = inserirImovelVago();
		String documento = cpfValido();
		String email = emailUnico("inquilino");

		String inquilinoId = inquilinoService.salvar(new InquilinoCadastroService.Dados(null,
				nomeUnico("Inquilino"), documento, email, "79999999999", null, null, null,
				imovelId.toString(), new BigDecimal("1200.00"), 5, BigDecimal.ZERO, null));
		UUID inquilinoUuid = UUID.fromString(inquilinoId);

		Map<String, Object> criado = jdbc.queryForMap("""
				SELECT criado_por, atualizado_por
				  FROM inquilinos
				 WHERE id = ?
				""", inquilinoUuid);
		assertThat(criado.get("criado_por"))
				.as("A-15: inquilino criado pelo ADMIN").isEqualTo(adminId);
		assertThat(criado.get("atualizado_por")).as("criação não inventa atualizador").isNull();

		autenticarColaborador();
		String novoNome = nomeUnico("InquilinoAtualizado");
		String novoEmail = emailUnico("inquilino-atualizado");
		inquilinoService.salvar(new InquilinoCadastroService.Dados(inquilinoId, novoNome,
				documento, novoEmail, "79988887777", null, null, null, null, null, 0, null,
				null));

		Map<String, Object> atualizado = jdbc.queryForMap("""
				SELECT nome, email, criado_por, atualizado_por
				  FROM inquilinos
				 WHERE id = ?
				""", inquilinoUuid);
		assertThat(atualizado.get("nome")).isEqualTo(novoNome);
		assertThat(atualizado.get("email")).isEqualTo(novoEmail);
		assertThat(atualizado.get("criado_por"))
				.as("A-15: atualização preserva o criador original").isEqualTo(adminId);
		assertThat(atualizado.get("atualizado_por"))
				.as("A-15: atualização troca atualizado_por para o COLABORADOR logado")
				.isEqualTo(colaboradorId);
	}

	@Test
	void classificacaoDeDocumentoTrocaAtualizadoPorEntreAdminEColaborador() {
		UUID imovelId = inserirImovelVago();
		String documentoId = documentoService.salvar("Documento " + MARCADOR, null, null,
				"IMOVEL", imovelId.toString(),
				"doc-" + UUID.randomUUID().toString().substring(0, 8) + ".pdf",
				"application/pdf", PDF);
		UUID documentoUuid = UUID.fromString(documentoId);

		Map<String, Object> enviado = jdbc.queryForMap("""
				SELECT enviado_por, atualizado_por
				  FROM documentos
				 WHERE id = ?
				""", documentoUuid);
		assertThat(enviado.get("enviado_por"))
				.as("A-15: upload grava o ADMIN logado em enviado_por").isEqualTo(adminId);
		assertThat(enviado.get("atualizado_por")).as("upload não inventa atualizador").isNull();

		autenticarColaborador();
		LocalDate dataColaborador = LocalDate.of(2026, 4, 1);
		documentoService.classificar(documentoId, "Comprovante", dataColaborador);

		Map<String, Object> aposColaborador = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, enviado_por, atualizado_por
				  FROM documentos
				 WHERE id = ?
				""", documentoUuid);
		assertThat(aposColaborador.get("tipo")).isEqualTo("COMPROVANTE");
		assertThat(aposColaborador.get("enviado_por"))
				.as("classificação não troca o autor do upload").isEqualTo(adminId);
		assertThat(aposColaborador.get("atualizado_por"))
				.as("A-15: classificação pelo COLABORADOR troca atualizado_por")
				.isEqualTo(colaboradorId);

		autenticarAdmin();
		documentoService.classificar(documentoId, "Contrato", dataColaborador.plusDays(1));

		Map<String, Object> aposAdmin = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, enviado_por, atualizado_por
				  FROM documentos
				 WHERE id = ?
				""", documentoUuid);
		assertThat(aposAdmin.get("tipo")).isEqualTo("CONTRATO");
		assertThat(aposAdmin.get("enviado_por")).isEqualTo(adminId);
		assertThat(aposAdmin.get("atualizado_por"))
				.as("A-15: nova classificação pelo ADMIN troca atualizado_por de volta")
				.isEqualTo(adminId);
	}

	@Test
	void movimentacaoRegistradaPeloColaboradorCarimbaResponsavelLogado() {
		autenticarColaborador();
		LocalDate data = LocalDate.of(2002, 1, 10);

		String movimentacaoId = movimentacaoService.registrarEntrada("Doação", null,
				new BigDecimal("99.90"), data, "Entrada de auditoria " + MARCADOR, "PIX");

		Map<String, Object> movimentacao = jdbc.queryForMap("""
				SELECT responsavel_id
				  FROM movimentacoes
				 WHERE id = ?
				""", UUID.fromString(movimentacaoId));
		assertThat(movimentacao.get("responsavel_id"))
				.as("A-15: movimentação grava o COLABORADOR logado em responsavel_id")
				.isEqualTo(colaboradorId);
	}

	private void autenticarAdmin() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, adminSenha));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
	}

	private void autenticarColaborador() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(colaboradorEmail, SENHA_COLABORADOR));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
	}

	private UUID criadoPor(String tabela, UUID id) {
		return jdbc.queryForObject("SELECT criado_por FROM " + tabela + " WHERE id = ?",
				UUID.class, id);
	}

	private UUID inserirImovelVago() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf,
				                     tipo, status)
				VALUES (?, ?, '10', NULL, ?, 'Aracaju', 'SE',
				        'CASA'::tipo_imovel, 'VAGO'::status_imovel)
				""", id, "Rua QA A15 " + MARCADOR + " " + id, "Bairro " + MARCADOR);
		return id;
	}

	private String nomeUnico(String base) {
		return base + " " + MARCADOR + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private String emailUnico(String base) {
		return "qa-a15-" + base + "-" + UUID.randomUUID().toString().substring(0, 8)
				+ "@manuelo.local";
	}

	private String cpfValido() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int[] digitos = new int[11];
		for (int i = 0; i < 9; i++) {
			digitos[i] = random.nextInt(10);
		}
		digitos[9] = digitoVerificador(digitos, 9, 10);
		digitos[10] = digitoVerificador(digitos, 10, 11);

		StringBuilder cpf = new StringBuilder(11);
		for (int digito : digitos) {
			cpf.append(digito);
		}
		return cpf.toString();
	}

	private int digitoVerificador(int[] digitos, int quantidade, int pesoInicial) {
		int soma = 0;
		for (int i = 0; i < quantidade; i++) {
			soma += digitos[i] * (pesoInicial - i);
		}
		int resto = (soma * 10) % 11;
		return resto == 10 ? 0 : resto;
	}

	private static Path criarDiretorioTemporario() {
		try {
			return Files.createTempDirectory("manuelo-storage-qa-a15-").toAbsolutePath();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
