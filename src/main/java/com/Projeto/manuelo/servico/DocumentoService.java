package com.Projeto.manuelo.servico;

import java.sql.Types;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.arquivos.ArmazenamentoArquivos;
import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.seguranca.UsuarioDetalhes;

/**
 * A-50: upload real de documentos (R08, DEC-I06). Valida vínculo, arquivo e mime,
 * grava o arquivo no armazenamento antes do registro e mantém enviado_por vindo do
 * SecurityContext. Também classifica pendentes e entrega o conteúdo para download.
 */
@Service
@Transactional
public class DocumentoService {

    private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    private static final Set<String> MIMES_PERMITIDOS = Set.of(
            "application/pdf", "image/jpeg", "image/png");

    public record Arquivo(String nome, String mime, byte[] conteudo) {
    }

    private final JdbcClient jdbc;
    private final ArmazenamentoArquivos armazenamento;

    public DocumentoService(JdbcTemplate jdbcTemplate, ArmazenamentoArquivos armazenamento) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.armazenamento = armazenamento;
    }

    public String salvar(String nome, String tipoUi, LocalDate data, String vinculoTipo,
                         String vinculoId, String nomeArquivo, String tipoMime, byte[] conteudo) {
        String nomeDocumento = textoObrigatorio(nome, "Informe o nome do documento.");
        String nomeDoArquivo = textoOpcional(nomeArquivo);
        if (nomeDoArquivo == null) {
            nomeDoArquivo = nomeDocumento;
        }

        if (nomeDoArquivo.length() > 200) {
            throw new IllegalArgumentException("O nome do arquivo deve ter até 200 caracteres.");
        }

        Vinculo vinculo = vinculo(vinculoTipo, vinculoId);
        if (!vinculoExiste(vinculo)) {
            throw new IllegalArgumentException(
                    "Vínculo não encontrado: " + vinculoTipo + " " + vinculoId);
        }

        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalArgumentException("Selecione um arquivo PDF, JPG ou PNG.");
        }
        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("O arquivo deve ter no máximo 10 MB.");
        }
        String mime = mime(tipoMime);

        boolean temTipo = tipoUi != null && !tipoUi.isBlank();
        boolean temData = data != null;
        String tipo;
        boolean pendente;
        if (temTipo && temData) {
            tipo = tipoDocumento(tipoUi);
            pendente = false;
        } else if (!temTipo && !temData) {
            tipo = null;
            pendente = true;
        } else {
            throw new IllegalArgumentException(
                    "Informe tipo e data do documento juntos ou deixe ambos em branco para revisão.");
        }

        UsuarioDetalhes ator = ator();

        String caminho = armazenamento.salvar(nomeDoArquivo, mime, conteudo);

        String sql = """
                INSERT INTO documentos (nome_arquivo, tipo, data_documento, pendente_revisao,
                                        caminho_armazenamento, tipo_mime, tamanho_bytes,
                                        %s, enviado_por)
                VALUES (:nomeArquivo, CAST(:tipo AS tipo_documento), :data, :pendente,
                        :caminho, :mime, :tamanho, :vinculo, :enviadoPor)
                RETURNING id::text
                """.formatted(vinculo.coluna());

        return jdbc.sql(sql)
                .param("nomeArquivo", nomeDoArquivo)
                .param("tipo", tipo, Types.VARCHAR)
                .param("data", data, Types.DATE)
                .param("pendente", pendente)
                .param("caminho", caminho)
                .param("mime", mime)
                .param("tamanho", (long) conteudo.length)
                .param("vinculo", vinculo.id())
                .param("enviadoPor", ator.getId())
                .query(String.class)
                .single();
    }

    public void classificar(String documentoId, String tipoUi, LocalDate data) {
        UsuarioDetalhes ator = atorColaborador();
        UUID documento = uuid(documentoId, "Identificador do documento inválido.");
        String tipo = tipoDocumento(tipoUi);
        if (data == null) {
            throw new IllegalArgumentException("Informe a data do documento.");
        }

        int atualizados = jdbc.sql("""
                UPDATE documentos
                   SET tipo = CAST(:tipo AS tipo_documento), data_documento = :data,
                       pendente_revisao = false, atualizado_por = :ator
                 WHERE id = :id
                """)
                .param("tipo", tipo)
                .param("data", data)
                .param("ator", ator.getId())
                .param("id", documento)
                .update();

        if (atualizados == 0) {
            throw new IllegalArgumentException("Documento não encontrado: " + documentoId);
        }
    }

    @Transactional(readOnly = true)
    public Arquivo baixar(String documentoId) {
        ator();
        UUID documento = uuid(documentoId, "Identificador do documento inválido.");

        Registro registro = jdbc.sql("""
                SELECT nome_arquivo, tipo_mime, caminho_armazenamento
                  FROM documentos
                 WHERE id = :id
                """)
                .param("id", documento)
                .query((rs, rowNum) -> new Registro(
                        rs.getString("nome_arquivo"),
                        rs.getString("tipo_mime"),
                        rs.getString("caminho_armazenamento")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Documento não encontrado: " + documentoId));

        byte[] conteudo = armazenamento.ler(registro.caminho());
        return new Arquivo(registro.nome(), registro.mime(), conteudo);
    }

    private boolean vinculoExiste(Vinculo vinculo) {
        return jdbc.sql("SELECT 1 FROM " + vinculo.tabela() + " WHERE id = :id")
                .param("id", vinculo.id())
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private Vinculo vinculo(String tipo, String id) {
        String tipoNormalizado = normalizar(tipo);
        if (tipoNormalizado == null) {
            throw new IllegalArgumentException("Selecione o vínculo do documento.");
        }
        UUID vinculoId = uuid(id, "Identificador do vínculo inválido.");
        String tabela = switch (tipoNormalizado) {
            case "IMOVEL" -> "imoveis";
            case "INQUILINO" -> "inquilinos";
            case "CONTRATO" -> "contratos";
            case "MOVIMENTACAO" -> "movimentacoes";
            default -> throw new IllegalArgumentException(
                    "Vínculo inválido: " + tipo + ". Use IMOVEL, INQUILINO, CONTRATO ou MOVIMENTACAO.");
        };
        return new Vinculo(tipoNormalizado, tabela, vinculoId);
    }

    private String tipoDocumento(String tipoUi) {
        String tipo = normalizar(tipoUi);
        if (tipo == null) {
            throw new IllegalArgumentException("Selecione o tipo do documento.");
        }
        return switch (tipo) {
            case "CONTRATO" -> "CONTRATO";
            case "COMPROVANTE" -> "COMPROVANTE";
            case "NOTA FISCAL", "NOTA_FISCAL" -> "NOTA_FISCAL";
            case "DOCUMENTO CONTABIL", "DOCUMENTO_CONTABIL", "LAUDO" -> "DOCUMENTO_CONTABIL";
            case "CADASTRO" -> "CADASTRO";
            default -> throw new IllegalArgumentException("Tipo de documento inválido: " + tipoUi);
        };
    }

    private String mime(String tipoMime) {
        if (tipoMime == null || tipoMime.isBlank()) {
            throw new IllegalArgumentException("Selecione um arquivo PDF, JPG ou PNG.");
        }
        String mime = tipoMime.trim().toLowerCase(Locale.ROOT);
        if (!MIMES_PERMITIDOS.contains(mime)) {
            throw new IllegalArgumentException(
                    "Tipo de arquivo não permitido: " + tipoMime + ". Use PDF, JPG ou PNG.");
        }
        return mime;
    }

    private UsuarioDetalhes ator() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null
                || !(autenticacao.getPrincipal() instanceof UsuarioDetalhes detalhes)
                || detalhes.getId() == null) {
            throw new IllegalStateException("Usuário autenticado obrigatório");
        }
        return detalhes;
    }

    private UsuarioDetalhes atorColaborador() {
        UsuarioDetalhes ator = ator();
        PerfilUsuario perfil = ator.getPerfil();
        if (perfil != PerfilUsuario.ADMINISTRADOR && perfil != PerfilUsuario.COLABORADOR) {
            throw new AccessDeniedException(
                    "Apenas administradores e colaboradores podem classificar documentos.");
        }
        return ator;
    }

    private UUID uuid(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(mensagem, ex);
        }
    }

    private String textoObrigatorio(String valor, String mensagem) {
        String texto = textoOpcional(valor);
        if (texto == null) {
            throw new IllegalArgumentException(mensagem);
        }
        return texto;
    }

    private String textoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String semAcento = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record Vinculo(String tipo, String tabela, UUID id) {

        String coluna() {
            return switch (tipo) {
                case "IMOVEL" -> "imovel_id";
                case "INQUILINO" -> "inquilino_id";
                case "CONTRATO" -> "contrato_id";
                case "MOVIMENTACAO" -> "movimentacao_id";
                default -> throw new IllegalStateException("Vínculo inválido: " + tipo);
            };
        }
    }

    private record Registro(String nome, String mime, String caminho) {
    }
}
