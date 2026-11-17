package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.servico.PagamentoAluguelService;

/**
 * A-32: pagamento de aluguel real via {@link PagamentoAluguelService} contra o
 * Postgres do perfil de teste. Prova R05 (quitação tira o atraso), a validação de
 * competência (futura/anterior ao acompanhamento), a normalização ao mês, o
 * mapeamento de forma e a caução (abatimento limitado ao saldo, sem saldo
 * negativo e sem movimentação parcial em caso de recusa).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PagamentoAluguelTest {

	private static final String MARCADOR = "QA-A32-" + UUID.randomUUID().toString().substring(0, 8);
	private static final BigDecimal ALUGUEL = new BigDecimal("1500.00");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private PagamentoAluguelService service;

	@Value("${manuelo.admin.email}")
	private String adminEmail;

	@Value("${manuelo.admin.senha}")
	private String adminSenha;

	private UUID adminId;

	@BeforeEach
	void autenticarAdmin() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, adminSenha));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
		adminId = jdbc.queryForObject("SELECT id FROM usuarios WHERE lower(email) = lower(?)",
				UUID.class, adminEmail);
	}

	@AfterEach
	void limparContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void quitarCompetenciasEmAbertoTiraOAtrasoEGravaAsMovimentacoes() {
		LocalDate hoje = LocalDate.now();
		LocalDate inicio = hoje.minusMonths(2).withDayOfMonth(1);
		short diaVencimento = 5;
		UUID contratoId = criarContrato(inicio, inicio, diaVencimento, ALUGUEL, null);

		assertThat(atrasado(contratoId))
				.as("contrato retroativo com competência vencida deve começar atrasado")
				.isTrue();

		List<LocalDate> competencias = competenciasVencidas(inicio, hoje, diaVencimento);
		assertThat(competencias).as("deve haver competência em aberto").isNotEmpty();

		for (LocalDate competencia : competencias) {
			// Passa uma data no meio do mês para provar a normalização à competência.
			service.registrar(contratoId.toString(), competencia.plusDays(5), ALUGUEL,
					"boleto", false);
		}

		assertThat(atrasado(contratoId))
				.as("quitar todas as competências em aberto deve remover o atraso (R05)")
				.isFalse();

		var movimentacoes = jdbc.query("""
				SELECT competencia, forma_pagamento::text AS forma, responsavel_id, valor
				  FROM movimentacoes
				 WHERE contrato_id = ?
				   AND tipo = 'ENTRADA'
				   AND categoria = 'ALUGUEL'
				 ORDER BY competencia
				""", (rs, rowNum) -> new MovimentacaoGravada(
						rs.getObject("competencia", LocalDate.class),
						rs.getString("forma"),
						rs.getObject("responsavel_id", UUID.class),
						rs.getBigDecimal("valor")), contratoId);

		assertThat(movimentacoes).as("uma entrada de aluguel por competência")
				.hasSize(competencias.size());

		List<LocalDate> competenciasGravadas = new ArrayList<>();
		for (MovimentacaoGravada movimentacao : movimentacoes) {
			competenciasGravadas.add(movimentacao.competencia());
			assertThat(movimentacao.forma()).as("forma 'boleto' deve virar BOLETO")
					.isEqualTo("BOLETO");
			assertThat(movimentacao.responsavelId())
					.as("responsável é o admin autenticado").isEqualTo(adminId);
			assertThat(movimentacao.valor()).isEqualByComparingTo(ALUGUEL);
		}
		assertThat(competenciasGravadas)
				.as("competência deve ser normalizada ao primeiro dia do mês")
				.containsExactlyElementsOf(competencias);
	}

	@Test
	void competenciaFuturaERecusadaSemGravarMovimentacao() {
		LocalDate hoje = LocalDate.now();
		LocalDate inicio = hoje.minusMonths(1).withDayOfMonth(1);
		UUID contratoId = criarContrato(inicio, inicio, (short) 5, ALUGUEL, null);

		assertThatThrownBy(() -> service.registrar(contratoId.toString(),
				hoje.plusMonths(1).withDayOfMonth(1), ALUGUEL, "pix", false))
				.as("competência futura não pode ser registrada")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("futura");

		assertThat(contarMovimentacoes(contratoId))
				.as("recusa não pode gravar movimentação").isZero();
	}

	@Test
	void competenciaAnteriorAoAcompanhamentoERecusada() {
		LocalDate hoje = LocalDate.now();
		LocalDate inicio = hoje.minusMonths(2).withDayOfMonth(1);
		UUID contratoId = criarContrato(inicio, inicio, (short) 5, ALUGUEL, null);

		assertThatThrownBy(() -> service.registrar(contratoId.toString(),
				inicio.minusMonths(1), ALUGUEL, "pix", false))
				.as("competência anterior ao acompanhamento deve ser recusada")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("anterior ao início do acompanhamento");

		assertThat(contarMovimentacoes(contratoId))
				.as("recusa não pode gravar movimentação").isZero();
	}

	@Test
	void caucaoAbateNoMaximoOSaldoENaoDeixaMovimentacaoParcial() {
		LocalDate hoje = LocalDate.now();
		LocalDate inicio = hoje.minusMonths(1).withDayOfMonth(1);
		UUID contratoId = criarContrato(inicio, inicio, (short) 5, ALUGUEL,
				new BigDecimal("500.00"));
		LocalDate competencia = hoje.withDayOfMonth(1);

		assertThat(saldoCaucao(contratoId)).isEqualByComparingTo("500.00");

		service.registrar(contratoId.toString(), competencia, new BigDecimal("300.00"),
				"pix", true);

		var movimentacao = jdbc.queryForMap("""
				SELECT forma_pagamento::text AS forma, valor, responsavel_id
				  FROM movimentacoes
				 WHERE contrato_id = ? AND tipo = 'ENTRADA' AND categoria = 'ALUGUEL'
				""", contratoId);
		assertThat(movimentacao.get("forma")).as("pagamento com caução usa forma CAUCAO")
				.isEqualTo("CAUCAO");
		assertThat((BigDecimal) movimentacao.get("valor")).isEqualByComparingTo("300.00");
		assertThat(movimentacao.get("responsavel_id")).isEqualTo(adminId);

		var abatimento = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, valor, criado_por
				  FROM caucao_movimentacoes
				 WHERE contrato_id = ? AND tipo = 'ABATIMENTO_ALUGUEL'
				""", contratoId);
		assertThat(abatimento.get("tipo")).isEqualTo("ABATIMENTO_ALUGUEL");
		assertThat((BigDecimal) abatimento.get("valor")).isEqualByComparingTo("300.00");
		assertThat(abatimento.get("criado_por")).isEqualTo(adminId);
		assertThat(saldoCaucao(contratoId)).isEqualByComparingTo("200.00");

		int movimentacoesAntes = contarMovimentacoes(contratoId);
		int abatimentosAntes = contar("SELECT count(*) FROM caucao_movimentacoes "
				+ "WHERE contrato_id = ?", contratoId);

		assertThatThrownBy(() -> service.registrar(contratoId.toString(), competencia,
				new BigDecimal("300.00"), "pix", true))
				.as("abatimento acima do saldo deve ser recusado")
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Saldo de caução insuficiente");

		assertThat(saldoCaucao(contratoId)).as("saldo não pode ficar negativo")
				.isEqualByComparingTo("200.00");
		assertThat(contarMovimentacoes(contratoId))
				.as("recusa não pode gravar movimentação extra").isEqualTo(movimentacoesAntes);
		assertThat(contar("SELECT count(*) FROM caucao_movimentacoes WHERE contrato_id = ?",
				contratoId))
				.as("recusa não pode gravar abatimento extra").isEqualTo(abatimentosAntes);
	}

	private UUID criarContrato(LocalDate inicio, LocalDate acompanharDesde, short diaVencimento,
			BigDecimal aluguel, BigDecimal depositoCaucao) {
		UUID imovelId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf,
				                     tipo, status)
				VALUES (?, ?, '10', NULL, ?, 'Aracaju', 'SE',
				        'APARTAMENTO'::tipo_imovel, 'ALUGADO'::status_imovel)
				""", imovelId, "Rua " + MARCADOR + " " + imovelId.toString().substring(0, 8),
				"Bairro " + MARCADOR);

		UUID inquilinoId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO inquilinos (id, nome, documento)
				VALUES (?, ?, ?)
				""", inquilinoId, "Inquilino " + MARCADOR + " " + inquilinoId.toString().substring(0, 8),
				documentoUnico());

		UUID contratoId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO contratos (id, imovel_id, inquilino_id, inicio, fim, valor_aluguel,
				                       dia_vencimento, acompanhar_pagamento_desde, criado_por)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", contratoId, imovelId, inquilinoId, inicio, inicio.plusYears(1), aluguel,
				diaVencimento, acompanharDesde, adminId);

		if (depositoCaucao != null && depositoCaucao.signum() > 0) {
			jdbc.update("""
					INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, criado_por)
					VALUES (?, 'DEPOSITO'::tipo_caucao, ?, ?, ?)
					""", contratoId, depositoCaucao, Date.valueOf(inicio), adminId);
		}
		return contratoId;
	}

	private List<LocalDate> competenciasVencidas(LocalDate desde, LocalDate hoje,
			short diaVencimento) {
		List<LocalDate> competencias = new ArrayList<>();
		for (LocalDate competencia = desde.withDayOfMonth(1);
				!competencia.isAfter(hoje.withDayOfMonth(1));
				competencia = competencia.plusMonths(1)) {
			LocalDate vencimento = competencia.withDayOfMonth(
					Math.min(diaVencimento, competencia.lengthOfMonth()));
			if (vencimento.isBefore(hoje)) {
				competencias.add(competencia.withDayOfMonth(1));
			}
		}
		return competencias;
	}

	private boolean atrasado(UUID contratoId) {
		Boolean resultado = jdbc.queryForObject(
				"SELECT atrasado FROM vw_contratos_situacao WHERE contrato_id = ?",
				Boolean.class, contratoId);
		return Boolean.TRUE.equals(resultado);
	}

	private BigDecimal saldoCaucao(UUID contratoId) {
		return jdbc.queryForObject(
				"SELECT saldo FROM vw_saldo_caucao WHERE contrato_id = ?",
				BigDecimal.class, contratoId);
	}

	private int contarMovimentacoes(UUID contratoId) {
		return contar("SELECT count(*) FROM movimentacoes WHERE contrato_id = ?", contratoId);
	}

	private int contar(String sql, Object... argumentos) {
		Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
		return total == null ? 0 : total;
	}

	private record MovimentacaoGravada(LocalDate competencia, String forma, UUID responsavelId,
			BigDecimal valor) {
	}

	private static String documentoUnico() {
		long valor = (UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
				% 100_000_000_000L;
		return String.format("%011d", valor);
	}
}
