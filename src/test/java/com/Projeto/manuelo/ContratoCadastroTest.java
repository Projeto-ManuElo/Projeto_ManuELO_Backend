package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.servico.ContratoCadastroService;

/**
 * A-31: cadastro de contrato real via {@link ContratoCadastroService}, contra o Postgres
 * do perfil de teste (sem mock). Cada método corre em transação e sofre rollback no fim.
 *
 * Prova o caminho completo (imóvel novo, inquilino, fiador, contrato, caução) e as
 * recusas de negócio: sobreposição no mesmo imóvel, ação judicial sem descrição, caução
 * negativa e documento inválido. As recusas acontecem antes de qualquer INSERT, então a
 * transação do teste continua válida (sem savepoint).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContratoCadastroTest {

	private static final String MARCADOR = "QA-A31-" + UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private ContratoCadastroService service;

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
	void criacaoCompletaGravaContratoFiadorCaucaoEInquilino() {
		String logradouro = logradouroUnico();
		String endereco = logradouro + ", 123 - Apto 4 - Bairro " + MARCADOR;
		String nome = nomeUnico("Inquilino");
		String cpf = cpfValido();
		String email = emailUnico("inquilino");
		String telefone = "79999999999";
		String nomeFiador = nomeUnico("Fiador");
		String documentoFiador = cpfValido();
		String emailFiador = emailUnico("fiador");
		String telefoneFiador = "79988887777";
		LocalDate hoje = jdbc.queryForObject("SELECT current_date", Date.class).toLocalDate();
		LocalDate vencimento = hoje.plusMonths(12);
		String descricaoJudicial = "Ação de cobrança em andamento " + MARCADOR;

		String contratoIdTexto = service.criar(new ContratoCadastroService.Dados(null, endereco,
				nome, cpf, email, telefone, nomeFiador, documentoFiador, emailFiador,
				telefoneFiador, true, descricaoJudicial, new BigDecimal("1500.00"), 10,
				vencimento, new BigDecimal("500.00"), null));

		assertThat(contratoIdTexto).as("criar deve devolver o id do contrato").isNotBlank();
		UUID contratoId = UUID.fromString(contratoIdTexto);

		var contrato = jdbc.queryForMap("""
				SELECT imovel_id, inquilino_id, fiador_id, inicio, fim, valor_aluguel,
				       dia_vencimento, acompanhar_pagamento_desde, possui_acao_judicial,
				       acao_judicial_descricao, criado_por
				  FROM contratos
				 WHERE id = ?
				""", contratoId);

		UUID imovelId = (UUID) contrato.get("imovel_id");
		UUID inquilinoId = (UUID) contrato.get("inquilino_id");
		assertThat(contrato.get("fiador_id")).as("o contrato deve apontar para o fiador")
				.isNotNull();
		UUID fiadorId = (UUID) contrato.get("fiador_id");

		Date inicio = (Date) contrato.get("inicio");
		assertThat(inicio.toLocalDate()).as("início = current_date").isEqualTo(hoje);
		assertThat(((Date) contrato.get("acompanhar_pagamento_desde")).toLocalDate())
				.as("acompanhamento começa no início").isEqualTo(inicio.toLocalDate());
		assertThat(((Date) contrato.get("fim")).toLocalDate()).isEqualTo(vencimento);
		assertThat((BigDecimal) contrato.get("valor_aluguel")).isEqualByComparingTo("1500.00");
		assertThat(((Number) contrato.get("dia_vencimento")).intValue()).isEqualTo(10);
		assertThat(contrato.get("possui_acao_judicial")).isEqualTo(true);
		assertThat(contrato.get("acao_judicial_descricao")).isEqualTo(descricaoJudicial);
		assertThat(contrato.get("criado_por")).isEqualTo(adminId);

		var imovel = jdbc.queryForMap(
				"SELECT logradouro, numero, bairro, criado_por FROM imoveis WHERE id = ?",
				imovelId);
		assertThat(imovel.get("logradouro")).isEqualTo(logradouro);
		assertThat(imovel.get("numero")).isEqualTo("123");
		assertThat(imovel.get("bairro")).isEqualTo("Bairro " + MARCADOR);
		assertThat(imovel.get("criado_por")).isEqualTo(adminId);

		var inquilino = jdbc.queryForMap("""
				SELECT nome, documento, email, telefone, criado_por
				  FROM inquilinos WHERE id = ?
				""", inquilinoId);
		assertThat(inquilino.get("nome")).isEqualTo(nome);
		assertThat(inquilino.get("documento")).isEqualTo(cpf);
		assertThat(inquilino.get("email")).isEqualTo(email);
		assertThat(inquilino.get("telefone")).isEqualTo(telefone);
		assertThat(inquilino.get("criado_por")).isEqualTo(adminId);

		var fiador = jdbc.queryForMap("""
				SELECT nome, documento, email, telefone, criado_por
				  FROM fiadores WHERE id = ?
				""", fiadorId);
		assertThat(fiador.get("nome")).isEqualTo(nomeFiador);
		assertThat(fiador.get("documento")).isEqualTo(documentoFiador);
		assertThat(fiador.get("email")).isEqualTo(emailFiador);
		assertThat(fiador.get("telefone")).isEqualTo(telefoneFiador);
		assertThat(fiador.get("criado_por")).isEqualTo(adminId);

		var caucao = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, valor, data, criado_por
				  FROM caucao_movimentacoes WHERE contrato_id = ?
				""", contratoId);
		assertThat(caucao.get("tipo")).isEqualTo("DEPOSITO");
		assertThat((BigDecimal) caucao.get("valor")).isEqualByComparingTo("500.00");
		assertThat(((Date) caucao.get("data")).toLocalDate()).isEqualTo(inicio.toLocalDate());
		assertThat(caucao.get("criado_por")).isEqualTo(adminId);
	}

	@Test
	void segundoContratoSobrepostoNoMesmoImovelERecusadoSemInserirNada() {
		String contratoOriginal = service.criar(dadosValidos(null,
				logradouroUnico() + ", 100 - Bairro " + MARCADOR,
				new BigDecimal("1500.00"), 10, LocalDate.now().plusMonths(12),
				new BigDecimal("500.00")));
		UUID contratoId = UUID.fromString(contratoOriginal);
		UUID imovelId = jdbc.queryForObject("SELECT imovel_id FROM contratos WHERE id = ?",
				UUID.class, contratoId);
		int contratosAntes = contar("SELECT count(*) FROM contratos WHERE imovel_id = ?",
				imovelId);

		String cpfNovo = cpfValido();
		String cpfFiadorNovo = cpfValido();

		assertThatThrownBy(() -> service.criar(new ContratoCadastroService.Dados(
				imovelId.toString(), null, nomeUnico("Sobreposto"), cpfNovo,
				emailUnico("sobreposto"), "79999999999", nomeUnico("FiadorSobreposto"),
				cpfFiadorNovo, emailUnico("fiador-sobreposto"), "79988887777", false, null,
				new BigDecimal("1800.00"), 10, LocalDate.now().plusMonths(12),
				new BigDecimal("500.00"), null)))
				.as("período sobreposto no mesmo imóvel deve ser recusado")
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Já existe contrato para este imóvel");

		assertThat(contar("SELECT count(*) FROM contratos WHERE imovel_id = ?", imovelId))
				.as("o contrato original deve continuar sendo o único do imóvel")
				.isEqualTo(contratosAntes);
		assertThat(contar("SELECT count(*) FROM inquilinos WHERE documento = ?", cpfNovo))
				.as("a tentativa recusada não pode persistir inquilino").isZero();
		assertThat(contar("SELECT count(*) FROM fiadores WHERE documento = ?", cpfFiadorNovo))
				.as("a tentativa recusada não pode persistir fiador").isZero();
	}

	@Test
	void acaoJudicialSemDescricaoERecusadaSemCriarContrato() {
		String logradouro = logradouroUnico();
		String cpf = cpfValido();

		assertThatThrownBy(() -> service.criar(new ContratoCadastroService.Dados(null,
				logradouro + ", 200 - Bairro " + MARCADOR, nomeUnico("SemDescricao"), cpf,
				emailUnico("sem-descricao"), "79999999999", null, null, null, null, true, null,
				new BigDecimal("1500.00"), 10, LocalDate.now().plusMonths(12),
				new BigDecimal("500.00"), null)))
				.as("ação judicial exige descrição")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("descrição");

		assertThat(contar("SELECT count(*) FROM contratos c JOIN inquilinos i "
				+ "ON i.id = c.inquilino_id WHERE i.documento = ?", cpf))
				.as("nenhum contrato pode ser criado").isZero();
		assertThat(contar("SELECT count(*) FROM inquilinos WHERE documento = ?", cpf))
				.as("nenhum inquilino pode ser criado").isZero();
		assertThat(contar("SELECT count(*) FROM imoveis WHERE logradouro = ?", logradouro))
				.as("nenhum imóvel pode ser criado").isZero();
	}

	@Test
	void caucaoNegativaERecusadaSemCriarContrato() {
		String logradouro = logradouroUnico();
		String cpf = cpfValido();

		assertThatThrownBy(() -> service.criar(new ContratoCadastroService.Dados(null,
				logradouro + ", 300 - Bairro " + MARCADOR, nomeUnico("CaucaoNegativa"), cpf,
				emailUnico("caucao-negativa"), "79999999999", null, null, null, null, false,
				null, new BigDecimal("1500.00"), 10, LocalDate.now().plusMonths(12),
				new BigDecimal("-1.00"), null)))
				.as("caução negativa deve ser recusada")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("caução");

		assertThat(contar("SELECT count(*) FROM inquilinos WHERE documento = ?", cpf))
				.as("nenhum inquilino pode ser criado").isZero();
		assertThat(contar("SELECT count(*) FROM imoveis WHERE logradouro = ?", logradouro))
				.as("nenhum imóvel pode ser criado").isZero();
	}

	@Test
	void cpfInvalidoERecusadoSemCriarContrato() {
		String logradouro = logradouroUnico();
		String cpfInvalido = "1234567890";

		assertThatThrownBy(() -> service.criar(new ContratoCadastroService.Dados(null,
				logradouro + ", 400 - Bairro " + MARCADOR, nomeUnico("CpfInvalido"),
				cpfInvalido, emailUnico("cpf-invalido"), "79999999999", null, null, null, null,
				false, null, new BigDecimal("1500.00"), 10, LocalDate.now().plusMonths(12),
				new BigDecimal("500.00"), null)))
				.as("CPF com 10 dígitos deve ser recusado")
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF/CNPJ");

		assertThat(contar("SELECT count(*) FROM inquilinos WHERE documento = ?", cpfInvalido))
				.as("nenhum inquilino pode ser criado").isZero();
		assertThat(contar("SELECT count(*) FROM imoveis WHERE logradouro = ?", logradouro))
				.as("nenhum imóvel pode ser criado").isZero();
	}

	private ContratoCadastroService.Dados dadosValidos(String imovelId, String endereco,
			BigDecimal aluguel, int diaVencimento, LocalDate vencimento, BigDecimal caucao) {
		return new ContratoCadastroService.Dados(imovelId, endereco, nomeUnico("Inquilino"),
				cpfValido(), emailUnico("inquilino"), "79999999999", nomeUnico("Fiador"),
				cpfValido(), emailUnico("fiador"), "79988887777", false, null, aluguel,
				diaVencimento, vencimento, caucao, null);
	}

	private int contar(String sql, Object... argumentos) {
		Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
		return total == null ? 0 : total;
	}

	private String logradouroUnico() {
		return "Rua QA A31 " + MARCADOR + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private String nomeUnico(String base) {
		return base + " " + MARCADOR + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private String emailUnico(String base) {
		return "qa-a31-" + base + "-" + UUID.randomUUID().toString().substring(0, 8)
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
}
