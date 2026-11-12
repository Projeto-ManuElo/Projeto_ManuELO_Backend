package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.servico.InquilinoCadastroService;

import jakarta.persistence.EntityManager;

/**
 * A-21: o cadastro de /inquilinos cria inquilino + fiador + contrato + depósito de caução
 * na MESMA transação (rollback total quando qualquer etapa falha).
 *
 * Vermelho proposital: {@code InquilinoCadastroService} e seu record {@code Dados} ainda
 * não existem, então o módulo de teste nem compila. O cenário roda contra o Postgres real
 * do perfil de teste; cada método faz rollback no fim (a caução é imutável e não pode ser
 * apagada por DELETE).
 *
 * Para provar o rollback sem abortar a transação do teste, cada tentativa inválida roda
 * dentro de um savepoint e é revertida em seguida.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InquilinoCadastroTest {

	private static final String SENHA = "senha-qa-1234";
	private static final String MARCADOR = "QA-A21-" + UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private InquilinoCadastroService service;

	private UUID adminId;
	private String adminEmail;

	@BeforeEach
	void autenticarAdmin() {
		adminId = UUID.randomUUID();
		adminEmail = "qa-a21-" + UUID.randomUUID().toString().substring(0, 8) + "@manuelo.local";

		jdbc.update("""
				INSERT INTO usuarios (id, nome, email, senha_hash, perfil, status,
				                      aprovado_por, aprovado_em)
				VALUES (?, ?, ?, ?, 'ADMINISTRADOR'::perfil_usuario, 'ATIVO'::status_usuario,
				        ?, now())
				""", adminId, "QA A21 Admin", adminEmail, passwordEncoder.encode(SENHA), adminId);

		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, SENHA));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
	}

	@AfterEach
	void limparContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void umEnvioCriaInquilinoFiadorContratoEDepositoNaMesmaTransacao() {
		UUID imovelId = inserirImovelVago();
		String nome = nomeUnico("Inquilino");
		String documento = cpfValido();
		String email = emailUnico("inquilino");
		String nomeFiador = nomeUnico("Fiador");
		String documentoFiador = cpfValido();
		String emailFiador = emailUnico("fiador");

		String inquilinoId = service.salvar(dados(null, nome, documento, email, "79999999999",
				nomeFiador, documentoFiador, emailFiador, imovelId,
				new BigDecimal("1500.00"), 5, new BigDecimal("3000.00"), null));
		entityManager.flush();

		assertThat(inquilinoId).as("salvar deve devolver o id do inquilino").isNotBlank();
		UUID inquilinoUuid = UUID.fromString(inquilinoId);

		var inquilino = jdbc.queryForMap(
				"SELECT id, nome, documento, criado_por FROM inquilinos WHERE documento = ?",
				documento);
		assertThat(inquilino.get("id")).isEqualTo(inquilinoUuid);
		assertThat(inquilino.get("nome")).isEqualTo(nome);
		assertThat(inquilino.get("criado_por")).isEqualTo(adminId);

		var fiador = jdbc.queryForMap("""
				SELECT id, nome, documento, email, criado_por
				  FROM fiadores WHERE documento = ?
				""", documentoFiador);
		assertThat(fiador.get("nome")).isEqualTo(nomeFiador);
		assertThat(fiador.get("email")).isEqualTo(emailFiador);
		assertThat(fiador.get("criado_por")).isEqualTo(adminId);

		var contrato = jdbc.queryForMap("""
				SELECT id, inquilino_id, fiador_id, valor_aluguel, dia_vencimento, criado_por,
				       (inicio <= current_date AND (fim IS NULL OR fim >= current_date)) AS vigente
				  FROM contratos
				 WHERE imovel_id = ?
				""", imovelId);
		assertThat(contrato.get("inquilino_id")).isEqualTo(inquilinoUuid);
		assertThat(contrato.get("fiador_id")).isEqualTo(fiador.get("id"));
		assertThat((BigDecimal) contrato.get("valor_aluguel")).isEqualByComparingTo("1500.00");
		assertThat(((Number) contrato.get("dia_vencimento")).intValue()).isEqualTo(5);
		assertThat(contrato.get("criado_por")).isEqualTo(adminId);
		assertThat(contrato.get("vigente")).as("o contrato criado deve estar vigente").isEqualTo(true);

		UUID contratoId = (UUID) contrato.get("id");
		assertThat(contar("SELECT count(*) FROM caucao_movimentacoes WHERE contrato_id = ?",
				contratoId)).isEqualTo(1);

		var caucao = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, valor, criado_por
				  FROM caucao_movimentacoes
				 WHERE contrato_id = ?
				""", contratoId);
		assertThat(caucao.get("tipo")).isEqualTo("DEPOSITO");
		assertThat((BigDecimal) caucao.get("valor")).isEqualByComparingTo("3000.00");
		assertThat(caucao.get("criado_por")).isEqualTo(adminId);
	}

	@Test
	void sobreposicaoNoMesmoImovelFalhaSemDeixarRegistrosParciais() {
		UUID imovelId = inserirImovelVago();
		String documento = cpfValido();
		String documentoFiador = cpfValido();
		service.salvar(dados(null, nomeUnico("Inquilino"), documento, emailUnico("inquilino"),
				"79999999999", nomeUnico("Fiador"), documentoFiador, emailUnico("fiador"),
				imovelId, new BigDecimal("1500.00"), 5, new BigDecimal("3000.00"), null));
		entityManager.flush();

		UUID contratoId = jdbc.queryForObject(
				"SELECT id FROM contratos WHERE imovel_id = ?", UUID.class, imovelId);

		String nome2 = nomeUnico("InquilinoSobreposto");
		String documento2 = cpfValido();
		String documentoFiador2 = cpfValido();

		assertThatThrownBy(() -> comSavepoint(() -> service.salvar(dados(null, nome2, documento2,
				emailUnico("sobreposto"), "79999999999", nomeUnico("FiadorSobreposto"),
				documentoFiador2, emailUnico("fiador-sobreposto"), imovelId,
				new BigDecimal("1800.00"), 10, new BigDecimal("2000.00"), null))))
				.as("um segundo contrato sobreposto no mesmo imóvel deve ser recusado")
				.isInstanceOf(RuntimeException.class);

		assertThat(contar("SELECT count(*) FROM inquilinos WHERE documento = ?", documento2))
				.as("o inquilino da tentativa recusada não pode permanecer").isZero();
		assertThat(contar("SELECT count(*) FROM fiadores WHERE documento = ?", documentoFiador2))
				.as("o fiador da tentativa recusada não pode permanecer").isZero();
		assertThat(contar("SELECT count(*) FROM contratos WHERE imovel_id = ?", imovelId))
				.as("o imóvel deve continuar com apenas o contrato original").isEqualTo(1);
		assertThat(contar("SELECT count(*) FROM caucao_movimentacoes WHERE contrato_id = ?",
				contratoId)).as("a caução original não pode ser duplicada").isEqualTo(1);
	}

	@Test
	void cpfInvalidoRecusaSemPersistirNada() {
		UUID imovelId = inserirImovelVago();
		String nome = nomeUnico("InquilinoCpfInvalido");
		String documentoInvalido = "1234567890";
		String documentoFiador = cpfValido();

		assertThatThrownBy(() -> comSavepoint(() -> service.salvar(dados(null, nome,
				documentoInvalido, emailUnico("cpf-invalido"), "79999999999",
				nomeUnico("FiadorCpfInvalido"), documentoFiador, emailUnico("fiador-cpf-invalido"),
				imovelId, new BigDecimal("1500.00"), 5, new BigDecimal("3000.00"), null))))
				.as("CPF/CNPJ com 10 dígitos deve ser recusado")
				.isInstanceOf(RuntimeException.class);

		assertThat(contar("SELECT count(*) FROM inquilinos WHERE nome = ?", nome))
				.as("nenhum inquilino pode ser gravado com documento inválido").isZero();
		assertThat(contar("SELECT count(*) FROM fiadores WHERE documento = ?", documentoFiador))
				.as("nenhum fiador pode permanecer na tentativa recusada").isZero();
		assertThat(contar("SELECT count(*) FROM contratos WHERE imovel_id = ?", imovelId))
				.as("nenhum contrato pode ser criado na tentativa recusada").isZero();
	}

	@Test
	void aluguelNaoPositivoRecusaSemPersistirNada() {
		UUID imovelId = inserirImovelVago();
		String nome = nomeUnico("InquilinoAluguelInvalido");
		String documento = cpfValido();
		String documentoFiador = cpfValido();

		assertThatThrownBy(() -> comSavepoint(() -> service.salvar(dados(null, nome, documento,
				emailUnico("aluguel-invalido"), "79999999999", nomeUnico("FiadorAluguelInvalido"),
				documentoFiador, emailUnico("fiador-aluguel-invalido"), imovelId,
				BigDecimal.ZERO, 5, new BigDecimal("3000.00"), null))))
				.as("aluguel menor ou igual a zero deve ser recusado")
				.isInstanceOf(RuntimeException.class);

		assertThat(contar("SELECT count(*) FROM inquilinos WHERE documento = ?", documento))
				.as("nenhum inquilino pode permanecer na tentativa recusada").isZero();
		assertThat(contar("SELECT count(*) FROM fiadores WHERE documento = ?", documentoFiador))
				.as("nenhum fiador pode permanecer na tentativa recusada").isZero();
		assertThat(contar("SELECT count(*) FROM contratos WHERE imovel_id = ?", imovelId))
				.as("nenhum contrato pode ser criado na tentativa recusada").isZero();
	}

	private InquilinoCadastroService.Dados dados(String inquilinoId, String nome, String documento,
			String email, String telefone, String nomeFiador, String documentoFiador,
			String emailFiador, UUID imovelId, BigDecimal aluguel, int diaVencimento,
			BigDecimal caucao, String reciboCaminho) {
		return new InquilinoCadastroService.Dados(inquilinoId, nome, documento, email, telefone,
				nomeFiador, documentoFiador, emailFiador, imovelId.toString(), aluguel,
				diaVencimento, caucao, reciboCaminho);
	}

	private void comSavepoint(Runnable acao) {
		Connection conexao = DataSourceUtils.getConnection(dataSource);
		Savepoint savepoint;
		try {
			savepoint = conexao.setSavepoint();
		} catch (SQLException e) {
			DataSourceUtils.releaseConnection(conexao, dataSource);
			throw new IllegalStateException("não foi possível abrir savepoint", e);
		}

		RuntimeException falha = null;
		try {
			acao.run();
		} catch (RuntimeException e) {
			falha = e;
			throw e;
		} finally {
			try {
				conexao.rollback(savepoint);
			} catch (SQLException e) {
				if (falha != null) {
					falha.addSuppressed(e);
				} else {
					throw new IllegalStateException("não foi possível reverter o savepoint", e);
				}
			} finally {
				DataSourceUtils.releaseConnection(conexao, dataSource);
			}
		}
	}

	private UUID inserirImovelVago() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf, cep,
				                     tipo, status)
				VALUES (?, ?, ?, NULL, ?, 'Aracaju', 'SE', NULL,
				        'CASA'::tipo_imovel, 'VAGO'::status_imovel)
				""", id, "Rua QA A21 " + MARCADOR + " " + id, "10", "Bairro QA A21");
		return id;
	}

	private int contar(String sql, Object... argumentos) {
		Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
		return total == null ? 0 : total;
	}

	private String nomeUnico(String base) {
		return base + " " + MARCADOR + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private String emailUnico(String base) {
		return "qa-a21-" + base + "-" + UUID.randomUUID().toString().substring(0, 8)
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
