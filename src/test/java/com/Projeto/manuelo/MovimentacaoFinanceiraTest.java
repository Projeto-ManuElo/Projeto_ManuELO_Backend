package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.ExtratoItem;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.Resumo;

/**
 * A-40/A-41/A-42: movimentação financeira real contra o Postgres do perfil de teste.
 * Prova R03 (imóvel do contrato), R04 (saída com comprovante diferido), R07 (caução
 * em livro próprio sem virar receita), o extrato assinado de R09, os filtros/totais
 * de A-41 e o estorno/correção exclusivos do administrador de A-42.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MovimentacaoFinanceiraTest {

	private static final String MARCADOR = "QA-A40-" + UUID.randomUUID().toString().substring(0, 8);
	private static final byte[] PDF =
			("%PDF-1.4\n% QA A-40\ncomprovante real\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
	private static final Path DIRETORIO = criarDiretorioTemporario();
	private static final String SENHA_COLABORADOR = "qa-colab-123";

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
	private MovimentacaoFinanceiraService service;

	@Value("${manuelo.admin.email}")
	private String adminEmail;

	@Value("${manuelo.admin.senha}")
	private String adminSenha;

	private UUID adminId;
	private String colaboradorEmail;

	@BeforeEach
	void prepararAtores() {
		autenticarAdmin();
		adminId = jdbc.queryForObject("SELECT id FROM usuarios WHERE lower(email) = lower(?)",
				UUID.class, adminEmail);

		colaboradorEmail = MARCADOR + "-colab@manuelo.local";
		jdbc.update("""
				INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status,
				                      aprovado_por, aprovado_em)
				VALUES (?, 'QA A40 Colaborador', ?, ?, 'COLABORADOR'::perfil_usuario,
				        'ATIVO'::status_usuario, ?, now())
				""", UUID.randomUUID(), colaboradorEmail,
				passwordEncoder.encode(SENHA_COLABORADOR), adminId);
	}

	@AfterEach
	void limparContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void entradaOutrasEntradasGravaValorDataOrigemESomaNoResumo() {
		LocalDate data = LocalDate.of(2002, 5, 10);

		String id = service.registrarEntrada("Doação", null, new BigDecimal("175.50"), data,
				"Entrada avulsa " + MARCADOR, "PIX");

		Map<String, Object> gravada = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, categoria::text AS categoria, valor,
				       data_movimentacao, descricao, forma_pagamento::text AS forma,
				       origem_descricao, responsavel_id
				  FROM movimentacoes
				 WHERE id = ?
				""", UUID.fromString(id));
		assertThat(gravada.get("tipo")).isEqualTo("ENTRADA");
		assertThat(gravada.get("categoria")).isEqualTo("OUTRAS_ENTRADAS");
		assertThat((BigDecimal) gravada.get("valor")).isEqualByComparingTo("175.50");
		assertThat(gravada.get("data_movimentacao")).isEqualTo(java.sql.Date.valueOf(data));
		assertThat(gravada.get("descricao")).isEqualTo("Entrada avulsa " + MARCADOR);
		assertThat(gravada.get("origem_descricao")).as("origem obrigatória de R01")
				.isEqualTo("Doação");
		assertThat(gravada.get("forma")).isEqualTo("PIX");
		assertThat(gravada.get("responsavel_id")).isEqualTo(adminId);

		Resumo resumo = service.resumo(data, data, null, MARCADOR);
		assertThat(resumo.entradas()).isEqualByComparingTo("175.50");
		assertThat(resumo.saidas()).isEqualByComparingTo("0");
		assertThat(resumo.saldo()).isEqualByComparingTo("175.50");
		assertThat(resumo.quantidade()).isEqualTo(1);

		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
	}

	@Test
	void saidaSemComprovanteERecusadaPeloServicoSemGravarLinha() {
		LocalDate data = LocalDate.of(2002, 6, 1);
		String descricao = "Saída sem nota " + MARCADOR;

		assertThatThrownBy(() -> service.registrarSaida("MANUTENCAO", new BigDecimal("90.00"),
				data, descricao, "PIX", "Fornecedor", "nota.pdf", "application/pdf", null))
				.as("A-40/R04: saída sem comprovante não pode nem chegar ao insert")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("comprovante");

		assertThat(contar("SELECT count(*) FROM movimentacoes WHERE descricao = ?", descricao))
				.as("recusa não pode gravar movimentação").isZero();
	}

	@Test
	void saidaSemComprovanteFalhaAoTornarOTriggerDiferidoImediato() {
		LocalDate data = LocalDate.of(2002, 6, 2);

		jdbc.update("""
				INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
				                           forma_pagamento, responsavel_id, favorecido)
				VALUES ('SAIDA', 'OUTRAS_SAIDAS', 42.00, ?, ?, 'PIX', ?, 'Fornecedor')
				""", data, "Saída crua sem comprovante " + MARCADOR, adminId);

		assertThatThrownBy(() -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"))
				.as("R04 é garantida pelo constraint trigger diferido do banco")
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("comprovante");
	}

	@Test
	void saidaComPdfRealGravaMovimentacaoEDocumentoVinculado() {
		LocalDate data = LocalDate.of(2002, 7, 15);
		String descricao = "Troca de bomba " + MARCADOR;

		String id = service.registrarSaida("MANUTENCAO", new BigDecimal("320.00"), data,
				descricao, "PIX", "Encanador " + MARCADOR, "nota-" + MARCADOR + ".pdf",
				"application/pdf", PDF);
		UUID movimentacaoId = UUID.fromString(id);

		Map<String, Object> gravada = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, categoria::text AS categoria, valor,
				       data_movimentacao, descricao, forma_pagamento::text AS forma,
				       favorecido, responsavel_id
				  FROM movimentacoes
				 WHERE id = ?
				""", movimentacaoId);
		assertThat(gravada.get("tipo")).isEqualTo("SAIDA");
		assertThat(gravada.get("categoria")).isEqualTo("MANUTENCAO");
		assertThat((BigDecimal) gravada.get("valor")).isEqualByComparingTo("320.00");
		assertThat(gravada.get("data_movimentacao")).isEqualTo(java.sql.Date.valueOf(data));
		assertThat(gravada.get("favorecido")).isEqualTo("Encanador " + MARCADOR);
		assertThat(gravada.get("responsavel_id")).isEqualTo(adminId);

		Map<String, Object> documento = jdbc.queryForMap("""
				SELECT nome_arquivo, tipo::text AS tipo, data_documento, pendente_revisao,
				       caminho_armazenamento, tipo_mime, tamanho_bytes, movimentacao_id,
				       enviado_por
				  FROM documentos
				 WHERE movimentacao_id = ?
				""", movimentacaoId);
		assertThat(documento.get("tipo")).as("vínculo financeiro usa tipo COMPROVANTE")
				.isEqualTo("COMPROVANTE");
		assertThat(documento.get("data_documento")).isEqualTo(java.sql.Date.valueOf(data));
		assertThat(documento.get("pendente_revisao")).isEqualTo(false);
		assertThat(documento.get("tipo_mime")).isEqualTo("application/pdf");
		assertThat(documento.get("tamanho_bytes")).isEqualTo((long) PDF.length);
		assertThat(documento.get("movimentacao_id")).isEqualTo(movimentacaoId);
		assertThat(documento.get("enviado_por")).isEqualTo(adminId);
		assertThat(Files.isRegularFile(DIRETORIO.resolve((String) documento.get(
				"caminho_armazenamento")))).as("PDF real deve existir no armazenamento").isTrue();

		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
	}

	@Test
	void depositoDeCaucaoVaiParaOLivroProprioENaoEntraComoReceita() {
		LocalDate data = LocalDate.of(2002, 8, 5);
		UUID contratoId = criarContrato(LocalDate.of(2002, 1, 1));
		Resumo antes = service.resumo(null, null);

		String id = service.registrarEntrada("Caucao", contratoId.toString(),
				new BigDecimal("500.00"), data, "Depósito de caução " + MARCADOR, "PIX");

		assertThat(id).as("depósito retorna id do livro de caução").isNotBlank();
		Map<String, Object> caucao = jdbc.queryForMap("""
				SELECT contrato_id, tipo::text AS tipo, valor, data, criado_por
				  FROM caucao_movimentacoes
				 WHERE id = ?
				""", UUID.fromString(id));
		assertThat(caucao.get("contrato_id")).isEqualTo(contratoId);
		assertThat(caucao.get("tipo")).isEqualTo("DEPOSITO");
		assertThat((BigDecimal) caucao.get("valor")).isEqualByComparingTo("500.00");
		assertThat(caucao.get("data")).isEqualTo(java.sql.Date.valueOf(data));
		assertThat(caucao.get("criado_por")).isEqualTo(adminId);

		assertThat(contar("SELECT count(*) FROM movimentacoes WHERE contrato_id = ?", contratoId))
				.as("R07: caução não cria movimentação de receita").isZero();

		Resumo depois = service.resumo(null, null);
		assertThat(depois.entradas()).isEqualByComparingTo(antes.entradas());
		assertThat(depois.saidas()).isEqualByComparingTo(antes.saidas());
		assertThat(depois.saldo()).isEqualByComparingTo(antes.saldo());
		assertThat(depois.quantidade()).isEqualTo(antes.quantidade());
		assertThat(service.extrato(data, data, null, MARCADOR))
				.as("depósito de caução não aparece no extrato").isEmpty();
	}

	@Test
	void estornoAdminMantemOriginalIntactaZeraExtratoERecusaSegundoEstorno() {
		LocalDate hoje = LocalDate.now();
		String descricao = "Entrada a estornar " + MARCADOR;
		String justificativa = "Pagamento em duplicidade " + MARCADOR;
		String originalId = service.registrarEntrada("Doação", null, new BigDecimal("800.00"),
				hoje, descricao, "PIX");

		Map<String, Object> antes;
		antes = jdbc.queryForMap("""
				SELECT valor, data_movimentacao, descricao, responsavel_id
				  FROM movimentacoes
				 WHERE id = ?
				""", UUID.fromString(originalId));

		service.estornar(originalId, justificativa);
		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");

		Map<String, Object> depois = jdbc.queryForMap("""
				SELECT valor, data_movimentacao, descricao, responsavel_id
				  FROM movimentacoes
				 WHERE id = ?
				""", UUID.fromString(originalId));
		assertThat(depois).as("R01: a original nunca é alterada").isEqualTo(antes);

		Map<String, Object> estorno = jdbc.queryForMap("""
				SELECT d.valor, d.justificativa, d.responsavel_id, d.movimentacao_original_id,
				       d.data_movimentacao
				  FROM movimentacoes d
				 WHERE d.tipo = 'ESTORNO' AND d.movimentacao_original_id = ?
				""", UUID.fromString(originalId));
		assertThat((BigDecimal) estorno.get("valor")).isEqualByComparingTo("800.00");
		assertThat(estorno.get("justificativa")).isEqualTo(justificativa);
		assertThat(estorno.get("responsavel_id")).isEqualTo(adminId);
		assertThat(estorno.get("data_movimentacao"))
				.isEqualTo(java.sql.Date.valueOf(LocalDate.now()));

		List<ExtratoItem> extrato = service.extrato(hoje, hoje, null, MARCADOR);
		assertThat(extrato).hasSize(2);
		assertThat(extrato.stream().map(ExtratoItem::valorAssinado)
				.reduce(BigDecimal.ZERO, BigDecimal::add))
				.as("R09: entrada + estorno de entrada zeram o extrato assinado")
				.isEqualByComparingTo("0");
		assertThat(extrato).filteredOn(item -> item.tipo().equals("ENTRADA"))
				.singleElement().extracting(ExtratoItem::estornado).isEqualTo(true);

		Resumo resumo = service.resumo(hoje, hoje, null, MARCADOR);
		assertThat(resumo.entradas()).isEqualByComparingTo("0");
		assertThat(resumo.saldo()).isEqualByComparingTo("0");
		assertThat(resumo.quantidade()).isEqualTo(2);
		assertThat(resumo.totalEstornos()).isEqualByComparingTo("800.00");

		assertThatThrownBy(() -> service.estornar(originalId, "Segunda tentativa " + MARCADOR))
				.as("R01: um original só pode ter um estorno")
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("já possui estorno");
	}

	@Test
	void colaboradorNaoPodeEstornarMovimentacao() {
		LocalDate hoje = LocalDate.now();
		String originalId = service.registrarEntrada("Doação", null, new BigDecimal("60.00"),
				hoje, "Entrada para negar estorno " + MARCADOR, "PIX");

		autenticarColaborador();

		assertThatThrownBy(() -> service.estornar(originalId, "Tentativa do colaborador"))
				.as("R01/A-42: estorno é exclusivo do administrador")
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("administradores");

		assertThat(contar("SELECT count(*) FROM movimentacoes WHERE tipo = 'ESTORNO' "
				+ "AND movimentacao_original_id = ?", UUID.fromString(originalId))).isZero();
	}

	@Test
	void correcaoAdminEstornaOriginalECriaNovaLigadaPorCorrigeMovimentacao() {
		LocalDate hoje = LocalDate.now();
		String descricaoOriginal = "Correção original " + MARCADOR;
		String justificativa = "Ajuste de valor " + MARCADOR;
		String originalId = service.registrarEntrada("Doação", null, new BigDecimal("100.00"),
				hoje, descricaoOriginal, "PIX");
		UUID original = UUID.fromString(originalId);

		Map<String, Object> antes = jdbc.queryForMap("""
				SELECT valor, data_movimentacao, descricao, categoria::text AS categoria
				  FROM movimentacoes
				 WHERE id = ?
				""", original);

		String corrigidaId = service.corrigir(originalId, null, null, new BigDecimal("150.00"),
				hoje, "Correção ajustada " + MARCADOR, "PIX", null, null, null, null,
				justificativa);
		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
		UUID corrigida = UUID.fromString(corrigidaId);

		Map<String, Object> depois = jdbc.queryForMap("""
				SELECT valor, data_movimentacao, descricao, categoria::text AS categoria
				  FROM movimentacoes
				 WHERE id = ?
				""", original);
		assertThat(depois).as("R02: original permanece intacta, só é estornada").isEqualTo(antes);

		assertThat(contar("SELECT count(*) FROM movimentacoes WHERE tipo = 'ESTORNO' "
				+ "AND movimentacao_original_id = ?", original))
				.as("correção exige exatamente um estorno da original").isEqualTo(1);

		Map<String, Object> nova = jdbc.queryForMap("""
				SELECT valor, data_movimentacao, descricao, categoria::text AS categoria,
				       corrige_movimentacao_id, responsavel_id
				  FROM movimentacoes
				 WHERE id = ?
				""", corrigida);
		assertThat((BigDecimal) nova.get("valor")).isEqualByComparingTo("150.00");
		assertThat(nova.get("data_movimentacao")).isEqualTo(java.sql.Date.valueOf(hoje));
		assertThat(nova.get("descricao")).isEqualTo("Correção ajustada " + MARCADOR);
		assertThat(nova.get("categoria")).isEqualTo("OUTRAS_ENTRADAS");
		assertThat(nova.get("corrige_movimentacao_id")).isEqualTo(original);
		assertThat(nova.get("responsavel_id")).isEqualTo(adminId);

		List<ExtratoItem> extrato = service.extrato(hoje, hoje, null, MARCADOR);
		assertThat(extrato).hasSize(3);
		assertThat(extrato.stream().map(ExtratoItem::valorAssinado)
				.reduce(BigDecimal.ZERO, BigDecimal::add))
				.as("extrato reflete -original + correção = 150")
				.isEqualByComparingTo("150.00");
		assertThat(extrato).filteredOn(item -> item.id().equals(originalId))
				.singleElement().extracting(ExtratoItem::estornado).isEqualTo(true);
		assertThat(extrato).filteredOn(item -> item.id().equals(corrigidaId))
				.singleElement().extracting(ExtratoItem::estornado).isEqualTo(false);

		Resumo resumo = service.resumo(hoje, hoje, null, MARCADOR);
		assertThat(resumo.entradas()).isEqualByComparingTo("150.00");
		assertThat(resumo.saldo()).isEqualByComparingTo("150.00");
		assertThat(resumo.quantidade()).isEqualTo(3);
	}

	@Test
	void filtrosDeExtratoPorPeriodoTipoEBuscaComTotaisCoerentes() {
		LocalDate primeiro = LocalDate.of(2002, 2, 10);
		LocalDate segundo = LocalDate.of(2002, 2, 15);
		LocalDate terceiro = LocalDate.of(2002, 2, 20);

		service.registrarEntrada("Doação", null, new BigDecimal("100.00"), primeiro,
				"Entrada Alfa " + MARCADOR, "PIX");
		service.registrarEntrada("Doação", null, new BigDecimal("50.00"), segundo,
				"Entrada Beta " + MARCADOR, "PIX");
		service.registrarSaida("MANUTENCAO", new BigDecimal("30.00"), terceiro,
				"Saída Gama " + MARCADOR, "PIX", "Fornecedor " + MARCADOR,
				"comprovante-" + MARCADOR + ".pdf", "application/pdf", PDF);

		List<ExtratoItem> periodo = service.extrato(LocalDate.of(2002, 2, 1),
				LocalDate.of(2002, 2, 28), null, MARCADOR);
		assertThat(periodo).hasSize(3);
		assertThat(periodo.stream().map(ExtratoItem::valorAssinado)
				.reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("120.00");

		assertThat(service.extrato(LocalDate.of(2002, 2, 16), LocalDate.of(2002, 2, 28),
				null, MARCADOR))
				.as("filtro de período").singleElement()
				.extracting(ExtratoItem::valorAssinado).isEqualTo(new BigDecimal("-30.00"));

		assertThat(service.extrato(LocalDate.of(2002, 2, 1), LocalDate.of(2002, 2, 28),
				"Entrada", MARCADOR))
				.as("filtro por tipo entrada").hasSize(2)
				.allMatch(item -> item.tipo().equals("ENTRADA"));
		assertThat(service.extrato(LocalDate.of(2002, 2, 1), LocalDate.of(2002, 2, 28),
				"Saída", MARCADOR))
				.as("filtro por tipo saída").singleElement()
				.extracting(ExtratoItem::tipo).isEqualTo("SAIDA");
		assertThat(service.extrato(LocalDate.of(2002, 2, 1), LocalDate.of(2002, 2, 28),
				null, "Alfa " + MARCADOR))
				.as("filtro por busca").singleElement()
				.extracting(ExtratoItem::descricao).isEqualTo("Entrada Alfa " + MARCADOR);

		Resumo todos = service.resumo(LocalDate.of(2002, 2, 1), LocalDate.of(2002, 2, 28),
				null, MARCADOR);
		assertThat(todos.entradas()).isEqualByComparingTo("150.00");
		assertThat(todos.saidas()).isEqualByComparingTo("30.00");
		assertThat(todos.saldo()).isEqualByComparingTo("120.00");
		assertThat(todos.quantidade()).isEqualTo(3);
		assertThat(todos.totalEstornos()).isEqualByComparingTo("0");

		Resumo entradas = service.resumo(LocalDate.of(2002, 2, 1), LocalDate.of(2002, 2, 28),
				"Entrada", MARCADOR);
		assertThat(entradas.entradas()).isEqualByComparingTo("150.00");
		assertThat(entradas.saidas()).isEqualByComparingTo("0");
		assertThat(entradas.saldo()).isEqualByComparingTo("150.00");
		assertThat(entradas.quantidade()).isEqualTo(2);

		Resumo saidas = service.resumo(LocalDate.of(2002, 2, 1), LocalDate.of(2002, 2, 28),
				"Saída", MARCADOR);
		assertThat(saidas.entradas()).isEqualByComparingTo("0");
		assertThat(saidas.saidas()).isEqualByComparingTo("30.00");
		assertThat(saidas.saldo()).isEqualByComparingTo("-30.00");
		assertThat(saidas.quantidade()).isEqualTo(1);

		assertThatThrownBy(() -> service.extrato(LocalDate.of(2002, 2, 28),
				LocalDate.of(2002, 2, 1), null, MARCADOR))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Período inválido");
	}

	@Test
	void aluguelUsaOImovelDoContratoERecusaImovelDivergenteNoBanco() {
		UUID imovelCorreto = criarImovel("Correto");
		UUID imovelErrado = criarImovel("Errado");
		UUID contratoId = criarContrato(imovelCorreto, LocalDate.of(2001, 1, 1));
		LocalDate data = LocalDate.of(2001, 3, 10);

		String id = service.registrarEntrada("ALUGUEL", contratoId.toString(),
				new BigDecimal("1000.00"), data, "Aluguel março " + MARCADOR, "PIX");

		Map<String, Object> gravada = jdbc.queryForMap("""
				SELECT imovel_id, contrato_id, competencia
				  FROM movimentacoes
				 WHERE id = ?
				""", UUID.fromString(id));
		assertThat(gravada.get("imovel_id")).as("R03: imóvel vem do contrato")
				.isEqualTo(imovelCorreto);
		assertThat(gravada.get("contrato_id")).isEqualTo(contratoId);
		assertThat(gravada.get("competencia"))
				.isEqualTo(java.sql.Date.valueOf(LocalDate.of(2001, 3, 1)));

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
				                           forma_pagamento, imovel_id, responsavel_id,
				                           contrato_id, competencia)
				VALUES ('ENTRADA', 'ALUGUEL', 10.00, ?, 'Aluguel imóvel errado',
				        'PIX', ?, ?, ?, ?)
				""", data, imovelErrado, adminId, contratoId, LocalDate.of(2001, 3, 1)))
				.as("R03 é garantida também pelo trigger de imóvel do contrato")
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("imóvel");
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

	private UUID criarContrato(LocalDate inicio) {
		UUID imovelId = criarImovel("Caução");
		return criarContrato(imovelId, inicio);
	}

	private UUID criarImovel(String rotulo) {
		UUID imovelId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf,
				                     tipo, status)
				VALUES (?, ?, '10', NULL, ?, 'Aracaju', 'SE',
				        'APARTAMENTO'::tipo_imovel, 'ALUGADO'::status_imovel)
				""", imovelId, "Rua " + rotulo + " " + MARCADOR + " "
				+ imovelId.toString().substring(0, 8), "Bairro " + MARCADOR);
		return imovelId;
	}

	private UUID criarContrato(UUID imovelId, LocalDate inicio) {
		UUID inquilinoId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO inquilinos (id, nome, documento)
				VALUES (?, ?, ?)
				""", inquilinoId, "Inquilino " + MARCADOR + " "
				+ inquilinoId.toString().substring(0, 8), documentoUnico());

		UUID contratoId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, fim, valor_aluguel,
				                       dia_vencimento, acompanhar_pagamento_desde, criado_por)
				VALUES (?, ?, ?, ?, ?, 1000.00, 5, ?, ?)
				""", contratoId, imovelId, inquilinoId, inicio, inicio.plusYears(2), inicio,
				adminId);
		return contratoId;
	}

	private int contar(String sql, Object... argumentos) {
		Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
		return total == null ? 0 : total;
	}

	private static String documentoUnico() {
		long valor = (UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
				% 100_000_000_000L;
		return String.format("%011d", valor);
	}

	private static Path criarDiretorioTemporario() {
		try {
			return Files.createTempDirectory("manuelo-storage-qa-a40-").toAbsolutePath();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
