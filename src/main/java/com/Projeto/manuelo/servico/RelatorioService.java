package com.Projeto.manuelo.servico;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Types;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import com.Projeto.manuelo.dominio.enums.SituacaoRelatorioContrato;
import com.Projeto.manuelo.seguranca.UsuarioDetalhes;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * A-70/A-71: geração dos 4 PDFs, composição insert-only e download de relatórios.
 *
 * <p>Os relatórios são exclusivos do ADMINISTRADOR (DEC-08, R09) e guardam a
 * composição exata do PDF em {@code relatorio_contratos} ou
 * {@code relatorio_movimentacoes}. O PDF leva cabeçalho da fundação, autor, data/hora,
 * código e rodapé "Página X de Y". O valor das movimentações é assinado e os
 * estornos contam no período da data do estorno (R09).</p>
 */
@Service
@Transactional
public class RelatorioService {

    private static final String MIME_PDF = "application/pdf";
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", BR);
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", BR);

    private static final Font FONTE_FUNDACAO = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font FONTE_TITULO = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font FONTE_META = new Font(Font.HELVETICA, 9);
    private static final Font FONTE_CABECALHO = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font FONTE_CELULA = new Font(Font.HELVETICA, 8);
    private static final Font FONTE_RODAPE = new Font(Font.HELVETICA, 8);

    public record Arquivo(String nome, String mime, byte[] conteudo) {
    }

    public record ResumoRelatorio(String id, String nome, String tipo, OffsetDateTime geradoEm,
            int qtd) {
    }

    private record ContratoLinha(String contratoId, String inquilino, String documento,
            String imovel, LocalDate inicio, LocalDate fim, String situacaoValidade,
            boolean atrasado, Integer diasAtraso, Integer diasParaVencer, BigDecimal valorAluguel,
            BigDecimal valorEmAtraso) {
    }

    private record MovimentoLinha(String id, String tipo, String tipoOriginal, String categoria,
            BigDecimal valorAssinado, LocalDate data, String descricao, String forma) {
    }

    private record Totais(BigDecimal entradas, BigDecimal saidas, BigDecimal saldo) {
    }

    private record PdfGerado(byte[] conteudo, int paginas) {
    }

    private enum Tipo {
        CONTRATOS_EXISTENTES("Contratos existentes"),
        CONTRATOS_ATRASADOS("Contratos atrasados"),
        CONTRATOS_A_VENCER("Contratos a vencer"),
        MOVIMENTACAO("Movimentação financeira");

        private final String titulo;

        Tipo(String titulo) {
            this.titulo = titulo;
        }

        String titulo() {
            return titulo;
        }
    }

    private final JdbcClient jdbc;
    private final ArmazenamentoArquivos armazenamento;

    public RelatorioService(JdbcTemplate jdbcTemplate, ArmazenamentoArquivos armazenamento) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.armazenamento = armazenamento;
    }

    public String gerar(String tipoUi, LocalDate inicio, LocalDate fim,
            String filtroMovimentacao) {
        UsuarioDetalhes ator = atorAdmin();
        Tipo tipo = tipo(tipoUi);
        if (tipo == Tipo.MOVIMENTACAO) {
            return gerarMovimentacao(ator, inicio, fim, filtroMovimentacao);
        }
        return gerarContratos(ator, tipo);
    }

    @Transactional(readOnly = true)
    public Arquivo baixar(String relatorioId) {
        atorAdmin();
        UUID id = uuid(relatorioId, "Identificador do relatório inválido.");
        Registro registro = jdbc.sql("""
                SELECT nome, caminho_arquivo
                  FROM relatorios
                 WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new Registro(
                        rs.getString("nome"), rs.getString("caminho_arquivo")))
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Relatório não encontrado: " + relatorioId));

        byte[] conteudo = armazenamento.ler(registro.caminho());
        return new Arquivo(nomeArquivo(registro.nome()), MIME_PDF, conteudo);
    }

    @Transactional(readOnly = true)
    public List<ResumoRelatorio> listarRecentes(int limite) {
        atorAdmin();
        int quantidade = Math.max(0, limite);
        if (quantidade == 0) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT id::text AS id, nome, tipo::text AS tipo, gerado_em, qtd_registros
                  FROM relatorios
                 ORDER BY gerado_em DESC, id DESC
                 LIMIT :limite
                """)
                .param("limite", quantidade)
                .query((rs, rowNum) -> new ResumoRelatorio(
                        rs.getString("id"),
                        rs.getString("nome"),
                        rs.getString("tipo"),
                        rs.getObject("gerado_em", OffsetDateTime.class),
                        rs.getInt("qtd_registros")))
                .list();
    }

    private String gerarContratos(UsuarioDetalhes ator, Tipo tipo) {
        List<ContratoLinha> linhas = consultarContratos(tipo);
        LocalDate hoje = LocalDate.now();
        String id = UUID.randomUUID().toString();
        String nome = nome(tipo, hoje);
        String codigo = codigo(id);

        List<String> informacoes = new ArrayList<>();
        informacoes.add("Data de referência: " + DATA.format(hoje));
        informacoes.add("Registros: " + linhas.size());

        List<String[]> dados = new ArrayList<>();
        for (ContratoLinha linha : linhas) {
            dados.add(new String[] {
                    texto(linha.inquilino()),
                    texto(linha.documento()),
                    texto(linha.imovel()),
                    vigencia(linha.inicio(), linha.fim()),
                    situacao(linha, tipo),
                    dias(linha, tipo),
                    dinheiro(valor(linha, tipo)) });
        }

        PdfGerado pdf = montarPdf("Relatório de " + tipo.titulo().toLowerCase(BR), codigo,
                ator.getNome(), informacoes,
                new String[] { "Inquilino", "Documento", "Imóvel", "Vigência", "Situação", "Dias",
                        "Valor" },
                new float[] { 3.2f, 2.1f, 3.4f, 2.2f, 1.7f, 0.9f, 1.6f },
                dados, true);

        String caminho = armazenamento.salvar(nome + ".pdf", MIME_PDF, pdf.conteudo());
        Integer janela = tipo == Tipo.CONTRATOS_A_VENCER ? janelaDias() : null;
        jdbc.sql("""
                INSERT INTO relatorios (id, nome, tipo, data_referencia, janela_dias,
                                        qtd_registros, caminho_arquivo, gerado_por)
                VALUES (:id, :nome, CAST(:tipo AS tipo_relatorio), :dataRef, :janela,
                        :qtd, :caminho, :ator)
                """)
                .param("id", UUID.fromString(id))
                .param("nome", nome)
                .param("tipo", tipo.name(), Types.VARCHAR)
                .param("dataRef", hoje)
                .param("janela", janela, Types.INTEGER)
                .param("qtd", linhas.size())
                .param("caminho", caminho)
                .param("ator", ator.getId())
                .update();

        SituacaoRelatorioContrato situacao = switch (tipo) {
            case CONTRATOS_ATRASADOS -> SituacaoRelatorioContrato.ATRASADO;
            case CONTRATOS_A_VENCER -> SituacaoRelatorioContrato.A_VENCER;
            default -> SituacaoRelatorioContrato.EXISTENTE;
        };
        for (int i = 0; i < linhas.size(); i++) {
            ContratoLinha linha = linhas.get(i);
            Integer dias = switch (tipo) {
                case CONTRATOS_ATRASADOS -> linha.diasAtraso();
                case CONTRATOS_A_VENCER -> linha.diasParaVencer();
                default -> null;
            };
            jdbc.sql("""
                    INSERT INTO relatorio_contratos (relatorio_id, contrato_id, ordem, situacao, dias)
                    VALUES (:relatorio, :contrato, :ordem,
                            CAST(:situacao AS situacao_relatorio_contrato), :dias)
                    """)
                    .param("relatorio", UUID.fromString(id))
                    .param("contrato", UUID.fromString(linha.contratoId()))
                    .param("ordem", i + 1)
                    .param("situacao", situacao.name(), Types.VARCHAR)
                    .param("dias", dias, Types.INTEGER)
                    .update();
        }
        return id;
    }

    private String gerarMovimentacao(UsuarioDetalhes ator, LocalDate inicio, LocalDate fim,
            String filtroUi) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException(
                    "Informe o período inicial e final do relatório de movimentação.");
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Período inválido: início posterior ao fim.");
        }
        String filtro = filtro(filtroUi);
        List<MovimentoLinha> linhas = consultarMovimentacoes(inicio, fim, filtro);
        Totais totais = totais(linhas);

        LocalDate hoje = LocalDate.now();
        String id = UUID.randomUUID().toString();
        String nome = nome(Tipo.MOVIMENTACAO, hoje);
        String codigo = codigo(id);

        List<String> informacoes = new ArrayList<>();
        informacoes.add("Período: " + DATA.format(inicio) + " a " + DATA.format(fim));
        informacoes.add("Filtro: " + (filtro == null ? "Todos" : rotulo(filtro)));
        informacoes.add("Entradas: " + dinheiro(totais.entradas())
                + "    Saídas: " + dinheiro(totais.saidas())
                + "    Saldo: " + dinheiro(totais.saldo()));
        informacoes.add("Registros: " + linhas.size());

        List<String[]> dados = new ArrayList<>();
        for (MovimentoLinha linha : linhas) {
            dados.add(new String[] {
                    DATA.format(linha.data()),
                    rotulo(linha.tipo()),
                    linha.categoria() == null ? "-" : rotulo(linha.categoria()),
                    texto(linha.descricao()),
                    linha.forma() == null ? "-" : rotulo(linha.forma()),
                    dinheiro(linha.valorAssinado()) });
        }

        PdfGerado pdf = montarPdf("Relatório de movimentação financeira", codigo,
                ator.getNome(), informacoes,
                new String[] { "Data", "Tipo", "Categoria", "Descrição", "Forma", "Valor" },
                new float[] { 1.3f, 1.1f, 1.5f, 3.5f, 1.5f, 1.3f },
                dados, false);

        String caminho = armazenamento.salvar(nome + ".pdf", MIME_PDF, pdf.conteudo());
        jdbc.sql("""
                INSERT INTO relatorios (id, nome, tipo, data_referencia, periodo_inicio,
                                        periodo_fim, filtro_tipo_movimentacao, total_entradas,
                                        total_saidas, saldo, qtd_registros, caminho_arquivo,
                                        gerado_por)
                VALUES (:id, :nome, 'MOVIMENTACAO', :dataRef, :inicio, :fim,
                        CAST(:filtro AS filtro_tipo_movimentacao), :entradas, :saidas, :saldo,
                        :qtd, :caminho, :ator)
                """)
                .param("id", UUID.fromString(id))
                .param("nome", nome)
                .param("dataRef", hoje)
                .param("inicio", inicio)
                .param("fim", fim)
                .param("filtro", filtro, Types.VARCHAR)
                .param("entradas", totais.entradas())
                .param("saidas", totais.saidas())
                .param("saldo", totais.saldo())
                .param("qtd", linhas.size())
                .param("caminho", caminho)
                .param("ator", ator.getId())
                .update();

        for (int i = 0; i < linhas.size(); i++) {
            jdbc.sql("""
                    INSERT INTO relatorio_movimentacoes (relatorio_id, movimentacao_id, ordem)
                    VALUES (:relatorio, :movimentacao, :ordem)
                    """)
                    .param("relatorio", UUID.fromString(id))
                    .param("movimentacao", UUID.fromString(linhas.get(i).id()))
                    .param("ordem", i + 1)
                    .update();
        }
        return id;
    }

    private List<ContratoLinha> consultarContratos(Tipo tipo) {
        String condicao = switch (tipo) {
            case CONTRATOS_ATRASADOS -> "WHERE atrasado";
            case CONTRATOS_A_VENCER -> "WHERE situacao_validade = 'A_VENCER'";
            default -> "";
        };
        String ordem = switch (tipo) {
            case CONTRATOS_ATRASADOS -> "dias_atraso DESC, inquilino_nome";
            case CONTRATOS_A_VENCER -> "dias_para_vencer, inquilino_nome";
            default -> "inicio, inquilino_nome";
        };
        return jdbc.sql("""
                SELECT contrato_id, inquilino_nome, inquilino_documento, imovel_endereco,
                       inicio, fim, situacao_validade, atrasado, dias_atraso, dias_para_vencer,
                       valor_aluguel, valor_em_atraso
                  FROM vw_contratos_situacao
                """ + " " + condicao + " ORDER BY " + ordem)
                .query((rs, rowNum) -> new ContratoLinha(
                        rs.getObject("contrato_id", UUID.class).toString(),
                        rs.getString("inquilino_nome"),
                        rs.getString("inquilino_documento"),
                        rs.getString("imovel_endereco"),
                        rs.getObject("inicio", LocalDate.class),
                        rs.getObject("fim", LocalDate.class),
                        rs.getString("situacao_validade"),
                        rs.getBoolean("atrasado"),
                        rs.getObject("dias_atraso", Integer.class),
                        rs.getObject("dias_para_vencer", Integer.class),
                        rs.getBigDecimal("valor_aluguel"),
                        rs.getBigDecimal("valor_em_atraso")))
                .list();
    }

    private List<MovimentoLinha> consultarMovimentacoes(LocalDate inicio, LocalDate fim,
            String filtro) {
        StringBuilder condicoes = new StringBuilder(
                " WHERE mv.data_movimentacao BETWEEN :inicio AND :fim");
        if ("ENTRADA".equals(filtro)) {
            condicoes.append(" AND (mv.tipo = 'ENTRADA'"
                    + " OR (mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA'))");
        } else if ("SAIDA".equals(filtro)) {
            condicoes.append(" AND (mv.tipo = 'SAIDA'"
                    + " OR (mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA'))");
        }
        return jdbc.sql("""
                SELECT mv.id,
                       mv.tipo::text AS tipo,
                       o.tipo::text AS tipo_original,
                       mv.categoria::text AS categoria,
                       CASE
                         WHEN mv.tipo = 'ENTRADA' THEN mv.valor
                         WHEN mv.tipo = 'SAIDA' THEN -mv.valor
                         WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA' THEN -mv.valor
                         WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA' THEN mv.valor
                         ELSE mv.valor
                       END AS valor_assinado,
                       mv.data_movimentacao,
                       mv.descricao,
                       mv.forma_pagamento::text AS forma
                  FROM movimentacoes mv
                  LEFT JOIN movimentacoes o ON o.id = mv.movimentacao_original_id
                """ + condicoes + """
                 ORDER BY mv.data_movimentacao, mv.criado_em, mv.id
                """)
                .param("inicio", inicio)
                .param("fim", fim)
                .query((rs, rowNum) -> new MovimentoLinha(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("tipo"),
                        rs.getString("tipo_original"),
                        rs.getString("categoria"),
                        rs.getBigDecimal("valor_assinado"),
                        rs.getObject("data_movimentacao", LocalDate.class),
                        rs.getString("descricao"),
                        rs.getString("forma")))
                .list();
    }

    private Totais totais(List<MovimentoLinha> linhas) {
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        BigDecimal saldo = BigDecimal.ZERO;
        for (MovimentoLinha linha : linhas) {
            BigDecimal valor = linha.valorAssinado() == null
                    ? BigDecimal.ZERO : linha.valorAssinado();
            saldo = saldo.add(valor);
            if ("ENTRADA".equals(linha.tipo())) {
                entradas = entradas.add(valor);
            } else if ("SAIDA".equals(linha.tipo())) {
                saidas = saidas.add(valor.abs());
            } else if ("ESTORNO".equals(linha.tipo()) && "ENTRADA".equals(linha.tipoOriginal())) {
                entradas = entradas.subtract(valor.abs());
            } else if ("ESTORNO".equals(linha.tipo()) && "SAIDA".equals(linha.tipoOriginal())) {
                saidas = saidas.subtract(valor.abs());
            }
        }
        if (entradas.signum() < 0) {
            entradas = BigDecimal.ZERO;
        }
        if (saidas.signum() < 0) {
            saidas = BigDecimal.ZERO;
        }
        return new Totais(entradas, saidas, saldo);
    }

    private PdfGerado montarPdf(String titulo, String codigo, String autor,
            List<String> informacoes, String[] colunas, float[] larguras, List<String[]> linhas,
            boolean paisagem) {
        int paginas = renderizar(titulo, codigo, autor, informacoes, colunas, larguras, linhas,
                paisagem, 0).paginas();
        return renderizar(titulo, codigo, autor, informacoes, colunas, larguras, linhas, paisagem,
                paginas);
    }

    private PdfGerado renderizar(String titulo, String codigo, String autor,
            List<String> informacoes, String[] colunas, float[] larguras, List<String[]> linhas,
            boolean paisagem, int totalPaginas) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            Document documento = new Document(paisagem ? PageSize.A4.rotate() : PageSize.A4,
                    32, 32, 40, 44);
            PdfWriter escritor = PdfWriter.getInstance(documento, saida);
            escritor.setPageEvent(new Rodape(totalPaginas));
            documento.open();

            Paragraph fundacao = new Paragraph("Fundação Manoel Cruz", FONTE_FUNDACAO);
            fundacao.setAlignment(Element.ALIGN_CENTER);
            documento.add(fundacao);

            Paragraph tituloPdf = new Paragraph(titulo, FONTE_TITULO);
            tituloPdf.setAlignment(Element.ALIGN_CENTER);
            documento.add(tituloPdf);

            Paragraph autoria = new Paragraph("Gerado por: " + autor
                    + "    Data e hora: " + DATA_HORA.format(LocalDateTime.now()), FONTE_META);
            autoria.setAlignment(Element.ALIGN_CENTER);
            documento.add(autoria);

            Paragraph referencia = new Paragraph("Código: " + codigo + "    "
                    + String.join("    ", informacoes), FONTE_META);
            referencia.setAlignment(Element.ALIGN_CENTER);
            documento.add(referencia);
            documento.add(new Paragraph(" ", FONTE_META));

            PdfPTable tabela = new PdfPTable(larguras);
            tabela.setWidthPercentage(100);
            tabela.setHeaderRows(1);
            for (String coluna : colunas) {
                PdfPCell celula = new PdfPCell(new Phrase(coluna, FONTE_CABECALHO));
                celula.setHorizontalAlignment(Element.ALIGN_LEFT);
                celula.setPadding(4);
                celula.setGrayFill(0.88f);
                tabela.addCell(celula);
            }
            if (linhas.isEmpty()) {
                PdfPCell vazio = new PdfPCell(
                        new Phrase("Nenhum registro encontrado.", FONTE_CELULA));
                vazio.setColspan(colunas.length);
                vazio.setPadding(6);
                tabela.addCell(vazio);
            } else {
                for (String[] linha : linhas) {
                    for (String valor : linha) {
                        PdfPCell celula = new PdfPCell(new Phrase(texto(valor), FONTE_CELULA));
                        celula.setPadding(4);
                        tabela.addCell(celula);
                    }
                }
            }
            documento.add(tabela);
            int paginas = escritor.getPageNumber();
            documento.close();
            return new PdfGerado(saida.toByteArray(), paginas);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Falha ao gerar o PDF do relatório: " + ex.getMessage(), ex);
        }
    }

    private static final class Rodape extends PdfPageEventHelper {

        private final int totalPaginas;

        private Rodape(int totalPaginas) {
            this.totalPaginas = totalPaginas;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte conteudo = writer.getDirectContent();
                BaseFont fonte = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI,
                        BaseFont.NOT_EMBEDDED);
                conteudo.beginText();
                conteudo.setFontAndSize(fonte, 8);
                conteudo.setTextMatrix(0, 0);
                conteudo.endText();
                float x = (document.left() + document.right()) / 2;
                String texto = "Página " + writer.getPageNumber() + " de " + totalPaginas;
                com.lowagie.text.pdf.ColumnText.showTextAligned(conteudo,
                        Element.ALIGN_CENTER, new Phrase(texto, FONTE_RODAPE), x,
                        document.bottomMargin() - 18, 0);
            } catch (Exception ex) {
                throw new IllegalStateException("Falha ao escrever o rodapé do PDF.", ex);
            }
        }
    }

    private String nome(Tipo tipo, LocalDate data) {
        return "Relatório de " + tipo.titulo().toLowerCase(BR) + " - " + DATA.format(data);
    }

    private String codigo(String id) {
        return "REL-" + id.replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String nomeArquivo(String nome) {
        String base = texto(nome).replaceAll("[\\\\/:*?\"<>|]", "_");
        return base.toLowerCase(Locale.ROOT).endsWith(".pdf") ? base : base + ".pdf";
    }

    private int janelaDias() {
        return jdbc.sql("""
                SELECT valor::int
                  FROM configuracoes
                 WHERE chave = 'dias_alerta_vencimento'
                """)
                .query(Integer.class)
                .optional()
                .orElse(7);
    }

    private String situacao(ContratoLinha linha, Tipo tipo) {
        if (tipo == Tipo.CONTRATOS_ATRASADOS || linha.atrasado()) {
            return "Atrasado";
        }
        if (tipo == Tipo.CONTRATOS_A_VENCER) {
            return "A vencer";
        }
        return switch (texto(linha.situacaoValidade())) {
            case "A_VENCER" -> "A vencer";
            case "VENCIDO" -> "Vencido";
            default -> "Vigente";
        };
    }

    private String dias(ContratoLinha linha, Tipo tipo) {
        Integer dias = switch (tipo) {
            case CONTRATOS_ATRASADOS -> linha.diasAtraso();
            case CONTRATOS_A_VENCER -> linha.diasParaVencer();
            default -> linha.atrasado() ? linha.diasAtraso() : linha.diasParaVencer();
        };
        return dias == null ? "-" : dias + (dias == 1 ? " dia" : " dias");
    }

    private BigDecimal valor(ContratoLinha linha, Tipo tipo) {
        if (tipo == Tipo.CONTRATOS_ATRASADOS) {
            return linha.valorEmAtraso();
        }
        if (tipo == Tipo.CONTRATOS_EXISTENTES && linha.atrasado()
                && linha.valorEmAtraso() != null && linha.valorEmAtraso().signum() > 0) {
            return linha.valorEmAtraso();
        }
        return linha.valorAluguel();
    }

    private String vigencia(LocalDate inicio, LocalDate fim) {
        String inicioTexto = inicio == null ? "-" : DATA.format(inicio);
        String fimTexto = fim == null ? "indeterminado" : DATA.format(fim);
        return inicioTexto + " a " + fimTexto;
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0,00";
        }
        NumberFormat formato = NumberFormat.getCurrencyInstance(BR);
        return formato.format(valor);
    }

    private String rotulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return "-";
        }
        String chave = normalizar(valor);
        if (chave != null) {
            switch (chave) {
                case "ENTRADA":
                    return "Entrada";
                case "SAIDA":
                    return "Saída";
                case "ESTORNO":
                    return "Estorno";
                case "PIX":
                    return "PIX";
                case "BOLETO":
                    return "Boleto";
                case "DINHEIRO":
                    return "Dinheiro";
                case "TRANSFERENCIA":
                    return "Transferência";
                case "CAUCAO":
                    return "Caução";
                case "ALUGUEL":
                    return "Aluguel";
                case "OUTRAS ENTRADAS":
                    return "Outras entradas";
                case "OUTRAS SAIDAS":
                    return "Outras saídas";
                case "MANUTENCAO":
                    return "Manutenção";
                case "TRIBUTOS", "TRIBUTO":
                    return "Tributos";
                case "ADMINISTRATIVO":
                    return "Administrativo";
                case "A VENCER":
                    return "A vencer";
                default:
                    break;
            }
        }
        String texto = valor.trim().replace('_', ' ').toLowerCase(BR);
        return texto.substring(0, 1).toUpperCase(BR) + texto.substring(1);
    }

    private Tipo tipo(String tipoUi) {
        String valor = normalizar(tipoUi);
        if (valor == null) {
            throw new IllegalArgumentException("Selecione o tipo de relatório.");
        }
        if (valor.contains("A VENCER") || valor.contains("VENCER")) {
            return Tipo.CONTRATOS_A_VENCER;
        }
        if (valor.contains("ATRASAD") || valor.contains("INADIMPL")) {
            return Tipo.CONTRATOS_ATRASADOS;
        }
        if (valor.contains("MOVIMENTA")) {
            return Tipo.MOVIMENTACAO;
        }
        if (valor.contains("CONTRATO") && valor.contains("EXISTENTE")) {
            return Tipo.CONTRATOS_EXISTENTES;
        }
        throw new IllegalArgumentException("Tipo de relatório inválido: " + tipoUi);
    }

    private String filtro(String filtroUi) {
        String valor = normalizar(filtroUi);
        if (valor == null || valor.startsWith("TODOS") || valor.startsWith("TODAS")) {
            return null;
        }
        if (valor.startsWith("ENTRADA")) {
            return "ENTRADA";
        }
        if (valor.startsWith("SAIDA")) {
            return "SAIDA";
        }
        throw new IllegalArgumentException(
                "Filtro de movimentação inválido: " + filtroUi + ". Use Todos, Entrada ou Saída.");
    }

    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String semAcento = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private UsuarioDetalhes atorAdmin() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null
                || !(autenticacao.getPrincipal() instanceof UsuarioDetalhes detalhes)
                || detalhes.getId() == null) {
            throw new IllegalStateException("Usuário autenticado obrigatório");
        }
        if (detalhes.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            throw new AccessDeniedException(
                    "Apenas administradores podem gerar, baixar ou listar relatórios.");
        }
        return detalhes;
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

    private String texto(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor.trim();
    }

    private record Registro(String nome, String caminho) {
    }
}
