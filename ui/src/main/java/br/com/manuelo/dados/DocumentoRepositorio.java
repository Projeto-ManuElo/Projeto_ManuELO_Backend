package br.com.manuelo.dados;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.manuelo.DemoData;

@Repository
public class DocumentoRepositorio {

    public record Opcao(String tipo, String id, String rotulo) {
    }

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd MMM",
            Locale.forLanguageTag("pt-BR"));

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public DocumentoRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<DemoData.Documento> listar(int limite) {
        return jdbc.sql("""
                SELECT nome_arquivo, tipo::text AS tipo, pendente_revisao, enviado_em, tamanho_bytes
                  FROM documentos
                 ORDER BY enviado_em DESC
                 LIMIT :limite
                """)
                .param("limite", limite)
                .query((rs, row) -> {
                    OffsetDateTime enviado = rs.getObject("enviado_em", OffsetDateTime.class);
                    String meta = "Enviado em " + DIA_MES.format(enviado) + " • "
                            + tamanho(rs.getLong("tamanho_bytes"));
                    boolean pendente = rs.getBoolean("pendente_revisao");
                    String tipo = pendente ? "Pendente" : Dominio.tipoDocumento(rs.getString("tipo"));
                    return new DemoData.Documento(rs.getString("nome_arquivo"), meta, tipo,
                            pendente ? "Pendente" : "Concluído");
                })
                .list();
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
