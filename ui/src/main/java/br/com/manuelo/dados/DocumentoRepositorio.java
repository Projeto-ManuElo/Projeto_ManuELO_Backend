package br.com.manuelo.dados;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentoRepositorio {

    public record Opcao(String tipo, String id, String rotulo) {
    }

    public record DocumentoLista(String id, String nomeArquivo, String nomeInquilino, String tipo,
            boolean pendente, String meta, String financeiro, LocalDate data) {
    }

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd MMM",
            Locale.forLanguageTag("pt-BR"));

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public DocumentoRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<DocumentoLista> listar(int limite) {
        return listar(null, null, null, limite);
    }

    public List<DocumentoLista> listar(String nomeInquilino, String tipo, String tipoFinanceiro,
            int limite) {
        StringBuilder sql = new StringBuilder("""
                WITH lista AS (
                    SELECT d.id::text AS id,
                           d.nome_arquivo,
                           d.tipo::text AS tipo,
                           d.data_documento,
                           d.pendente_revisao,
                           d.enviado_em,
                           d.tamanho_bytes,
                           COALESCE(direto.nome, contrato_inq.nome, imovel_inq.nome) AS nome_inquilino,
                           mv.tipo::text AS tipo_financeiro
                      FROM documentos d
                      LEFT JOIN inquilinos direto ON direto.id = d.inquilino_id
                      LEFT JOIN contratos ct ON ct.id = d.contrato_id
                      LEFT JOIN inquilinos contrato_inq ON contrato_inq.id = ct.inquilino_id
                      LEFT JOIN LATERAL (
                          SELECT i.nome
                            FROM contratos c2
                            JOIN inquilinos i ON i.id = c2.inquilino_id
                           WHERE c2.imovel_id = d.imovel_id
                             AND c2.inicio <= current_date
                             AND (c2.fim IS NULL OR c2.fim >= current_date)
                           ORDER BY c2.inicio DESC
                           LIMIT 1
                      ) imovel_inq ON true
                      LEFT JOIN movimentacoes mv ON mv.id = d.movimentacao_id
                )
                SELECT * FROM lista WHERE 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();

        if (nomeInquilino != null && !nomeInquilino.isBlank()) {
            sql.append(" AND lower(nome_inquilino) LIKE lower(:nome) ESCAPE '\\'");
            params.put("nome", "%" + like(nomeInquilino.trim()) + "%");
        }
        String tipoEnum = tipoDocumento(tipo);
        if (tipoEnum != null) {
            sql.append(" AND tipo = :tipo");
            params.put("tipo", tipoEnum);
        }
        String financeiroEnum = tipoFinanceiro(tipoFinanceiro);
        if (financeiroEnum != null) {
            sql.append(" AND tipo_financeiro = :financeiro");
            params.put("financeiro", financeiroEnum);
        }
        sql.append(" ORDER BY enviado_em DESC LIMIT :limite");
        params.put("limite", limite);

        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, row) -> {
                    OffsetDateTime enviado = rs.getObject("enviado_em", OffsetDateTime.class);
                    String meta = "Enviado em " + DIA_MES.format(enviado) + " • "
                            + tamanho(rs.getLong("tamanho_bytes"));
                    return new DocumentoLista(
                            rs.getString("id"),
                            rs.getString("nome_arquivo"),
                            rs.getString("nome_inquilino"),
                            rs.getString("tipo"),
                            rs.getBoolean("pendente_revisao"),
                            meta,
                            rs.getString("tipo_financeiro"),
                            rs.getObject("data_documento", LocalDate.class));
                })
                .list();
    }

    private static String tipoDocumento(String rotulo) {
        if (rotulo == null || rotulo.isBlank()) {
            return null;
        }
        return Dominio.tipoDocumentoEnum(rotulo.trim());
    }

    private static String tipoFinanceiro(String rotulo) {
        if (rotulo == null || rotulo.isBlank()) {
            return null;
        }
        return switch (rotulo.trim().toLowerCase(Locale.ROOT)) {
            case "entrada" -> "ENTRADA";
            case "saída", "saida" -> "SAIDA";
            case "estorno" -> "ESTORNO";
            default -> rotulo.trim().toUpperCase(Locale.ROOT);
        };
    }

    private static String like(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public List<Opcao> opcoes() {
        return jdbc.sql("""
                SELECT 'IMOVEL' AS tipo, id::text AS id,
                       'Imóvel: ' || logradouro || ', ' || numero AS rotulo
                  FROM imoveis
                UNION ALL
                SELECT 'INQUILINO', id::text, 'Inquilino: ' || nome
                  FROM inquilinos
                UNION ALL
                SELECT 'CONTRATO', c.id::text,
                       'Contrato: ' || i.nome || ' — ' || im.logradouro || ', ' || im.numero
                  FROM contratos c
                  JOIN inquilinos i ON i.id = c.inquilino_id
                  JOIN imoveis im ON im.id = c.imovel_id
                UNION ALL
                SELECT 'MOVIMENTACAO', m.id::text,
                       'Movimentação: ' || m.descricao || ' — '
                       || to_char(m.data_movimentacao, 'DD/MM/YYYY')
                  FROM movimentacoes m
                 ORDER BY rotulo
                """)
                .query((rs, row) -> new Opcao(rs.getString("tipo"), rs.getString("id"),
                        rs.getString("rotulo")))
                .list();
    }

    public void salvar(String nome, String tipoUi, LocalDate data, Opcao vinculo) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do documento.");
        }
        if (vinculo == null) {
            throw new IllegalArgumentException("Selecione o imóvel ou inquilino do documento.");
        }
        String tipo = Dominio.tipoDocumentoEnum(tipoUi);
        if (tipo == null) {
            throw new IllegalArgumentException("Selecione o tipo do documento.");
        }
        String arquivo = nome.trim().endsWith(".pdf") ? nome.trim() : nome.trim() + ".pdf";
        String caminho = "local://documentos/" + UUID.randomUUID() + ".pdf";
        UUID usuario = UUID.fromString(sessao.id());

        jdbc.sql("""
                INSERT INTO documentos (nome_arquivo, tipo, data_documento, pendente_revisao,
                                        caminho_armazenamento, tipo_mime, tamanho_bytes,
                                        imovel_id, inquilino_id, enviado_por)
                VALUES (:nome, CAST(:tipo AS tipo_documento), :data, :pendente, :caminho,
                        'application/pdf', 1024,
                        :imovel, :inquilino, :usuario)
                """)
                .param("nome", arquivo)
                .param("tipo", tipo)
                .param("data", data)
                .param("pendente", data == null)
                .param("caminho", caminho)
                .param("imovel", "IMOVEL".equals(vinculo.tipo()) ? UUID.fromString(vinculo.id()) : null)
                .param("inquilino", "INQUILINO".equals(vinculo.tipo()) ? UUID.fromString(vinculo.id()) : null)
                .param("usuario", usuario)
                .update();
    }

    private String tamanho(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return Math.max(1, bytes / 1024) + " KB";
    }
}
