package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.arquivos.ArmazenamentoLocal;
import com.Projeto.manuelo.servico.DocumentoService;

/**
 * A-50: upload real de documentos (R08, DEC-I06). Prova contra o Postgres do perfil
 * de teste que os bytes são gravados no armazenamento local com nome UUID+extensão,
 * que os metadados reais vão para documentos, que o download devolve o mesmo
 * conteúdo, que classificar limpa pendente_revisao e que mime/tamanho/classificação/
 * vínculo inválidos são recusados sem gravar linha. Também prova o bloqueio de path
 * traversal em {@link ArmazenamentoLocal}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UploadDocumentoTest {

	private static final String MIME_PDF = "application/pdf";
	private static final Pattern UUID_PDF =
			Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf$");
	private static final byte[] PDF =
			("%PDF-1.4\n% QA A-50\nconteudo real do upload\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
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
	private DocumentoService service;

	@Autowired
	private ArmazenamentoLocal armazenamento;

	@Value("${manuelo.admin.email}")
	private String adminEmail;

	@Value("${manuelo.admin.senha}")
	private String adminSenha;

	private UUID adminId;
	private UUID imovelId;

	@BeforeEach
	void prepararCenario() {
		Authentication autenticacao = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(adminEmail, adminSenha));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
		adminId = jdbc.queryForObject("SELECT id FROM usuarios WHERE lower(email) = lower(?)",
				UUID.class, adminEmail);

		imovelId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO imoveis (id, logradouro, numero, complemento, bairro, cidade, uf,
				                     tipo, status)
				VALUES (?, ?, '10', NULL, 'Centro', 'Aracaju', 'SE',
				        'APARTAMENTO'::tipo_imovel, 'VAGO'::status_imovel)
				""", imovelId, "Rua QA A-50 " + imovelId.toString().substring(0, 8));
	}

	@AfterEach
	void limparContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void salvaPdfRealComMetadadosPendenteEBaixaOsMesmosBytes() {
		String nomeArquivo = "contrato-" + UUID.randomUUID() + ".pdf";

		String id = service.salvar("Contrato assinado", null, null, "IMOVEL",
				imovelId.toString(), nomeArquivo, MIME_PDF, PDF);

		Map<String, Object> documento = jdbc.queryForMap("""
				SELECT nome_arquivo, tipo::text AS tipo, data_documento, pendente_revisao,
				       caminho_armazenamento, tipo_mime, tamanho_bytes, imovel_id, enviado_por
				  FROM documentos
				 WHERE id = ?
				""", UUID.fromString(id));

		String caminho = (String) documento.get("caminho_armazenamento");
		assertThat(caminho)
				.as("o caminho gravado é UUID + extensão, nunca o nome original")
				.matches(UUID_PDF);
		assertThat(documento.get("nome_arquivo")).isEqualTo(nomeArquivo);
		assertThat(documento.get("tipo_mime")).isEqualTo(MIME_PDF);
		assertThat(documento.get("tamanho_bytes"))
				.as("tamanho real em bytes").isEqualTo((long) PDF.length);
		assertThat(documento.get("imovel_id")).isEqualTo(imovelId);
		assertThat(documento.get("enviado_por")).isEqualTo(adminId);
		assertThat(documento.get("pendente_revisao")).isEqualTo(true);
		assertThat(documento.get("tipo")).as("tipo fica nulo enquanto pendente").isNull();
		assertThat(documento.get("data_documento"))
				.as("data fica nula enquanto pendente").isNull();

		Path arquivo = DIRETORIO.resolve(caminho);
		assertThat(Files.isRegularFile(arquivo)).as("o arquivo deve existir no disco").isTrue();
		assertThat(lerDisco(arquivo)).as("o disco contém exatamente os bytes enviados")
				.isEqualTo(PDF);

		DocumentoService.Arquivo baixado = service.baixar(id);
		assertThat(baixado.conteudo()).as("download devolve os mesmos bytes").isEqualTo(PDF);
		assertThat(baixado.nome()).as("download devolve o nome do arquivo").isEqualTo(nomeArquivo);
		assertThat(baixado.mime()).isEqualTo(MIME_PDF);
	}

	@Test
	void classificarPersisteTipoEDataEZeraPendencia() {
		String id = service.salvar("Comprovante", null, null, "IMOVEL", imovelId.toString(),
				"comprovante-" + UUID.randomUUID() + ".pdf", MIME_PDF, PDF);
		LocalDate data = LocalDate.of(2026, 3, 15);

		service.classificar(id, "Comprovante", data);

		Map<String, Object> documento = jdbc.queryForMap("""
				SELECT tipo::text AS tipo, data_documento, pendente_revisao, atualizado_por
				  FROM documentos
				 WHERE id = ?
				""", UUID.fromString(id));
		assertThat(documento.get("pendente_revisao")).isEqualTo(false);
		assertThat(documento.get("tipo")).isEqualTo("COMPROVANTE");
		assertThat(documento.get("data_documento")).isEqualTo(java.sql.Date.valueOf(data));
		assertThat(documento.get("atualizado_por")).as("auditoria da classificação").isEqualTo(adminId);
	}

	@Test
	void recusaMimeInvalidoSemGravarLinha() {
		int antes = contarDocumentosDoImovel();

		assertThatThrownBy(() -> service.salvar("Arquivo", null, null, "IMOVEL",
				imovelId.toString(), "arquivo.txt", "text/plain", PDF))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("não permitido");

		assertThat(contarDocumentosDoImovel()).as("recusa não pode gravar documento").isEqualTo(antes);
	}

	@Test
	void recusaArquivoMaiorQueDezMegabytesSemGravarLinha() {
		byte[] grande = new byte[10 * 1024 * 1024 + 1];
		int antes = contarDocumentosDoImovel();

		assertThatThrownBy(() -> service.salvar("Arquivo", null, null, "IMOVEL",
				imovelId.toString(), "grande.pdf", MIME_PDF, grande))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("10 MB");

		assertThat(contarDocumentosDoImovel()).as("recusa não pode gravar documento").isEqualTo(antes);
	}

	@Test
	void recusaTipoOuDataIsoladosSemGravarLinha() {
		int antes = contarDocumentosDoImovel();

		assertThatThrownBy(() -> service.salvar("Documento", "CONTRATO", null, "IMOVEL",
				imovelId.toString(), "doc.pdf", MIME_PDF, PDF))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("tipo e data");

		assertThatThrownBy(() -> service.salvar("Documento", null, LocalDate.now(), "IMOVEL",
				imovelId.toString(), "doc.pdf", MIME_PDF, PDF))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("tipo e data");

		assertThat(contarDocumentosDoImovel()).as("recusa não pode gravar documento").isEqualTo(antes);
	}

	@Test
	void recusaVinculoInexistenteSemGravarLinha() {
		UUID inexistente = UUID.randomUUID();
		int antes = contarDocumentosDoImovel();

		assertThatThrownBy(() -> service.salvar("Documento", null, null, "IMOVEL",
				inexistente.toString(), "doc.pdf", MIME_PDF, PDF))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Vínculo não encontrado");

		assertThat(contar("SELECT count(*) FROM documentos WHERE imovel_id = ?", inexistente))
				.as("nenhuma linha para o vínculo inexistente").isZero();
		assertThat(contarDocumentosDoImovel()).as("imóvel do cenário continua sem documento")
				.isEqualTo(antes);
	}

	@Test
	void bloqueiaPathTraversalNaLeitura() {
		assertThatThrownBy(() -> armazenamento.ler("../fora"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("fora do diretório");
	}

	private int contarDocumentosDoImovel() {
		return contar("SELECT count(*) FROM documentos WHERE imovel_id = ?", imovelId);
	}

	private int contar(String sql, Object... argumentos) {
		Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
		return total == null ? 0 : total;
	}

	private static byte[] lerDisco(Path arquivo) {
		try {
			return Files.readAllBytes(arquivo);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static Path criarDiretorioTemporario() {
		try {
			return Files.createTempDirectory("manuelo-storage-qa-a50-").toAbsolutePath();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
