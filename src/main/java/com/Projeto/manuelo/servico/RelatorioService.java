package com.Projeto.manuelo.servico;

import java.math.BigDecimal;
import java.sql.Types;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.Projeto.manuelo.servico.RelatorioDados.ContratoLinha;
import com.Projeto.manuelo.servico.RelatorioDados.MovimentoLinha;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Celula;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Documento;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Indicador;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Linha;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Secao;
import com.Projeto.manuelo.servico.RelatorioPdfModelo.Tom;
import com.Projeto.manuelo.servico.RelatorioPdfRenderer.PdfGerado;

/**
 * A-70/A-71/A-73: geração dos 4 PDFs, composição insert-only e download.
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

    public record Arquivo(String nome, String mime, byte[] conteudo) {
    }

    public record ResumoRelatorio(String id, String nome, String tipo, OffsetDateTime geradoEm,
            int qtd) {
    }

    private record Totais(BigDecimal brutoEntradas, BigDecimal brutoSaidas,
            BigDecimal estornosEntradas, BigDecimal estornosSaidas,
            BigDecimal liquidoEntradas, BigDecimal liquidoSaidas, BigDecimal saldo,
            int quantidade) {
    }

    private enum Tipo {
        CONTRATOS_EXISTENTES("Contratos existentes", "", "inquilino_nome"),
        CONTRATOS_ATRASADOS("Contratos atrasados", "WHERE atrasado",
                "dias_atraso DESC, inquilino_nome"),
        CONTRATOS_A_VENCER("Contratos a vencer", "WHERE situacao_validade = 'A_VENCER'",
                "dias_para_vencer, inquilino_nome"),
        MOVIMENTACAO("Movimentação financeira", null, null);

        private final String titulo;
        private final String condicao;
        private final String ordem;

        Tipo(String titulo, String condicao, String ordem) {
            this.titulo = titulo;
            this.condicao = condicao;
            this.ordem = ordem;
        }

        String titulo() {
            return titulo;
        }
    }

    private final JdbcClient jdbc;
    private final ArmazenamentoArquivos armazenamento;
    private final RelatorioDados dados;
    private final RelatorioPdfRenderer renderer = new RelatorioPdfRenderer();

    public RelatorioService(JdbcTemplate jdbcTemplate, ArmazenamentoArquivos armazenamento) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.armazenamento = armazenamento;
        this.dados = new RelatorioDados(this.jdbc);
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
        List<ContratoLinha> linhas = dados.contratos(tipo.condicao, tipo.ordem);
        LocalDate hoje = LocalDate.now();
        String id = UUID.randomUUID().toString();
        String nome = nome(tipo, hoje);
        String codigo = codigo(id);
        String geradoEm = DATA_HORA.format(java.time.LocalDateTime.now());

        Documento documento = documentoContratos(tipo, linhas, hoje, codigo, ator.getNome(),
                geradoEm);
        PdfGerado pdf = renderer.renderizar(documento);
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
        List<MovimentoLinha> linhas = dados.movimentacoes(inicio, fim, filtro);
        Totais totais = totais(linhas);

        LocalDate hoje = LocalDate.now();
        String id = UUID.randomUUID().toString();
        String nome = nome(Tipo.MOVIMENTACAO, hoje);
        String codigo = codigo(id);
        String geradoEm = DATA_HORA.format(java.time.LocalDateTime.now());

        Documento documento = documentoMovimentacao(linhas, totais, inicio, fim, filtro, codigo,
                ator.getNome(), geradoEm);
        PdfGerado pdf = renderer.renderizar(documento);
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
                .param("entradas", naoNegativo(totais.liquidoEntradas()))
                .param("saidas", naoNegativo(totais.liquidoSaidas()))
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

    private Documento documentoContratos(Tipo tipo, List<ContratoLinha> linhas, LocalDate hoje,
            String codigo, String autor, String geradoEm) {
        List<Indicador> indicadores = new ArrayList<>();
        List<Secao> secoes = new ArrayList<>();
        String subtitulo = switch (tipo) {
            case CONTRATOS_ATRASADOS -> "Contratos com competências em aberto";
            case CONTRATOS_A_VENCER -> "Vencimento em até " + janelaDias() + " dias";
            default -> "Contratos cadastrados e sua situação atual";
        };

        switch (tipo) {
            case CONTRATOS_ATRASADOS -> {
                BigDecimal soma = somarContratos(linhas, true);
                indicadores.add(new Indicador("Contratos atrasados",
                        String.valueOf(linhas.size()), Tom.NEGATIVO));
                indicadores.add(new Indicador("Valor em atraso", dinheiro(soma), Tom.NEGATIVO));
                secoes.add(new Secao("Contratos atrasados", null,
                        new String[] { "Inquilino", "Documento", "Imóvel", "Vencimento",
                                "Dias de atraso", "Valor" },
                        new float[] { 3.2f, 2.0f, 3.4f, 1.7f, 1.5f, 1.7f },
                        linhas.stream().map(linha -> linhaAtrasado(linha, hoje)).toList(), List.of()));
            }
            case CONTRATOS_A_VENCER -> {
                BigDecimal soma = somarContratos(linhas, false);
                indicadores.add(new Indicador("Contratos", String.valueOf(linhas.size()),
                        Tom.NORMAL));
                indicadores.add(new Indicador("Janela", janelaDias() + " dias", Tom.ALERTA));
                indicadores.add(new Indicador("Soma mensal", dinheiro(soma), Tom.NORMAL));
                secoes.add(new Secao("Contratos a vencer", null,
                        new String[] { "Inquilino", "Documento", "Imóvel", "Vencimento",
                                "Dias restantes", "Valor" },
                        new float[] { 3.2f, 2.0f, 3.4f, 1.7f, 1.5f, 1.7f },
                        linhas.stream().map(this::linhaAVencer).toList(), List.of()));
            }
            default -> {
                long vigentes = linhas.stream().filter(linha -> vigente(linha, hoje)).count();
                BigDecimal soma = somarContratosVigentes(linhas, hoje);
                indicadores.add(new Indicador("Contratos", String.valueOf(linhas.size()),
                        Tom.NORMAL));
                indicadores.add(new Indicador("Vigentes", String.valueOf(vigentes),
                        Tom.POSITIVO));
                indicadores.add(new Indicador("Soma mensal", dinheiro(soma), Tom.NORMAL));
                secoes.add(new Secao("Contratos existentes", null,
                        new String[] { "Inquilino", "Documento", "Imóvel", "Início", "Fim",
                                "Valor", "Situação" },
                        new float[] { 3.2f, 2.0f, 3.4f, 1.6f, 1.6f, 1.7f, 2.0f },
                        linhas.stream().map(this::linhaExistente).toList(), List.of()));
            }
        }

        String referencia = "Data de referência: " + DATA.format(hoje)
                + "    Registros: " + linhas.size()
                + (tipo == Tipo.CONTRATOS_A_VENCER ? "    Janela: até " + janelaDias() + " dias"
                        : "");
        return new Documento("Relatório de " + tipo.titulo().toLowerCase(BR), subtitulo, codigo,
                autor, geradoEm, referencia, indicadores, secoes, false);
    }

    private Documento documentoMovimentacao(List<MovimentoLinha> linhas, Totais totais,
            LocalDate inicio, LocalDate fim, String filtro, String codigo, String autor,
            String geradoEm) {
        List<Indicador> indicadores = List.of(
                new Indicador("Entradas", dinheiro(totais.liquidoEntradas()), Tom.POSITIVO),
                new Indicador("Saídas", dinheiro(totais.liquidoSaidas()), Tom.NEGATIVO),
                new Indicador("Saldo", dinheiro(totais.saldo()),
                        totais.saldo().signum() < 0 ? Tom.NEGATIVO : Tom.POSITIVO),
                new Indicador("Registros", String.valueOf(totais.quantidade()), Tom.NORMAL));

        List<Secao> secoes = new ArrayList<>();
        secoes.add(secaoSubtotais(linhas, filtro));

        if (!"SAIDA".equals(filtro)) {
            List<MovimentoLinha> entradas = porTipo(linhas, "ENTRADA");
            secoes.add(new Secao("Entradas",
                    "Origem = inquilino e imóvel ou descrição da entrada.",
                    new String[] { "Data", "Descrição", "Categoria", "Origem", "Forma", "Valor",
                            "Registrado por", "Comprovante" },
                    new float[] { 1.0f, 2.4f, 1.3f, 3.2f, 1.2f, 1.4f, 1.7f, 1.2f },
                    entradas.stream().map(this::linhaEntrada).toList(),
                    List.of("Subtotal líquido de entradas: "
                            + dinheiro(totais.liquidoEntradas()))));
        }

        if (!"ENTRADA".equals(filtro)) {
            List<MovimentoLinha> saidas = porTipo(linhas, "SAIDA");
            secoes.add(new Secao("Saídas", "Destino = favorecido.",
                    new String[] { "Data", "Descrição", "Categoria", "Favorecido", "Forma",
                            "Valor", "Registrado por", "Comprovante" },
                    new float[] { 1.0f, 2.4f, 1.3f, 3.2f, 1.2f, 1.4f, 1.7f, 1.2f },
                    saidas.stream().map(this::linhaSaida).toList(),
                    List.of("Subtotal líquido de saídas: "
                            + dinheiro(totais.liquidoSaidas()))));
        }

        List<MovimentoLinha> estornos = porTipo(linhas, "ESTORNO");
        if (!estornos.isEmpty()) {
            secoes.add(new Secao("Estornos do período",
                    "Estornos contam na data do estorno (R09).",
                    new String[] { "Data", "Lançamento original", "Justificativa",
                            "Estornado por", "Valor" },
                    new float[] { 1.2f, 3.5f, 3.5f, 2.2f, 1.4f },
                    estornos.stream().map(this::linhaEstorno).toList(), List.of()));
        }

        secoes.add(secaoTotais(totais));

        String referencia = "Período: " + DATA.format(inicio) + " a " + DATA.format(fim)
                + "    Filtro: " + (filtro == null ? "Todos" : rotulo(filtro))
                + "    Registros: " + linhas.size();
        return new Documento("Relatório de movimentação financeira",
                "Entradas, saídas, estornos e totais do período", codigo, autor, geradoEm,
                referencia, indicadores, secoes, true);
    }

    private Secao secaoSubtotais(List<MovimentoLinha> linhas, String filtro) {
        List<Linha> rows = new ArrayList<>();
        if (!"SAIDA".equals(filtro)) {
            subtotais(linhas, "ENTRADA").forEach((categoria, valor) -> rows.add(Linha.de(
                    Celula.texto(rotulo(categoria)),
                    Celula.colorida(dinheiro(valor), Tom.POSITIVO, false))));
        }
        if (!"ENTRADA".equals(filtro)) {
            subtotais(linhas, "SAIDA").forEach((categoria, valor) -> rows.add(Linha.de(
                    Celula.texto(rotulo(categoria)),
                    Celula.colorida(dinheiro(valor), Tom.NEGATIVO, false))));
        }
        return new Secao("Subtotais por categoria", "Valores líquidos de estornos.",
                new String[] { "Categoria", "Valor" }, new float[] { 3.0f, 1.5f }, rows,
                List.of());
    }

    private Secao secaoTotais(Totais totais) {
        BigDecimal brutoSaldo = totais.brutoEntradas().subtract(totais.brutoSaidas());
        BigDecimal estornosSaldo = totais.estornosEntradas().subtract(totais.estornosSaidas());
        List<Linha> rows = List.of(
                Linha.de(Celula.negrito("Entradas"), Celula.texto(dinheiro(totais.brutoEntradas())),
                        Celula.texto(dinheiro(totais.estornosEntradas())),
                        Celula.colorida(dinheiro(totais.liquidoEntradas()), Tom.POSITIVO, true)),
                Linha.de(Celula.negrito("Saídas"), Celula.texto(dinheiro(totais.brutoSaidas())),
                        Celula.texto(dinheiro(totais.estornosSaidas())),
                        Celula.colorida(dinheiro(totais.liquidoSaidas()), Tom.NEGATIVO, true)),
                Linha.de(Celula.negrito("Saldo"), Celula.texto(dinheiro(brutoSaldo)),
                        Celula.texto(dinheiro(estornosSaldo)),
                        Celula.colorida(dinheiro(totais.saldo()),
                                totais.saldo().signum() < 0 ? Tom.NEGATIVO : Tom.POSITIVO,
                                true)));
        return new Secao("Totais do período", "Bruto, estornos e líquido.",
                new String[] { "Descrição", "Bruto", "Estornos", "Líquido" },
                new float[] { 3.0f, 1.6f, 1.6f, 1.6f }, rows, List.of());
    }

    private Linha linhaExistente(ContratoLinha linha) {
        return Linha.de(
                Celula.texto(linha.inquilino()),
                Celula.texto(linha.documento()),
                Celula.texto(linha.imovel()),
                Celula.texto(data(linha.inicio())),
                Celula.texto(data(linha.fim())),
                Celula.texto(dinheiro(linha.valorAluguel())),
                Celula.colorida(situacao(linha, Tipo.CONTRATOS_EXISTENTES),
                        tomSituacao(linha, Tipo.CONTRATOS_EXISTENTES), true));
    }

    private Linha linhaAtrasado(ContratoLinha linha, LocalDate hoje) {
        LocalDate vencimento = linha.diasAtraso() == null ? null
                : hoje.minusDays(linha.diasAtraso());
        return Linha.de(
                Celula.texto(linha.inquilino()),
                Celula.texto(linha.documento()),
                Celula.texto(linha.imovel()),
                Celula.texto(data(vencimento)),
                Celula.colorida(dias(linha.diasAtraso()), Tom.NEGATIVO, true),
                Celula.colorida(dinheiro(linha.valorEmAtraso()), Tom.NEGATIVO, false));
    }

    private Linha linhaAVencer(ContratoLinha linha) {
        return Linha.de(
                Celula.texto(linha.inquilino()),
                Celula.texto(linha.documento()),
                Celula.texto(linha.imovel()),
                Celula.texto(data(linha.fim())),
                Celula.colorida(dias(linha.diasParaVencer()), Tom.ALERTA, true),
                Celula.texto(dinheiro(linha.valorAluguel())));
    }

    private Linha linhaEntrada(MovimentoLinha linha) {
        return Linha.de(
                Celula.texto(data(linha.data())),
                Celula.texto(linha.descricao()),
                Celula.texto(rotulo(linha.categoria())),
                Celula.texto(origem(linha)),
                Celula.texto(rotulo(linha.forma())),
                Celula.colorida(dinheiro(linha.valorAssinado()), Tom.POSITIVO, true),
                Celula.texto(linha.responsavel()),
                Celula.texto(linha.comprovante() ? "Sim" : "Não"));
    }

    private Linha linhaSaida(MovimentoLinha linha) {
        return Linha.de(
                Celula.texto(data(linha.data())),
                Celula.texto(linha.descricao()),
                Celula.texto(rotulo(linha.categoria())),
                Celula.texto(linha.favorecido()),
                Celula.texto(rotulo(linha.forma())),
                Celula.colorida(dinheiro(linha.valorAssinado()), Tom.NEGATIVO, true),
                Celula.texto(linha.responsavel()),
                Celula.texto(linha.comprovante() ? "Sim" : "Não"));
    }

    private Linha linhaEstorno(MovimentoLinha linha) {
        Tom tom = linha.valorAssinado() != null && linha.valorAssinado().signum() < 0
                ? Tom.NEGATIVO : Tom.POSITIVO;
        return Linha.de(
                Celula.texto(data(linha.data())),
                Celula.texto(linha.descricaoOriginal()),
                Celula.texto(linha.justificativa()),
                Celula.texto(linha.responsavel()),
                Celula.colorida(dinheiro(linha.valorAssinado()), tom, true));
    }

    private String origem(MovimentoLinha linha) {
        if ("ALUGUEL".equals(linha.categoria()) && linha.inquilino() != null) {
            return texto(linha.inquilino()) + " · " + texto(linha.imovel());
        }
        return texto(linha.origem() == null ? linha.descricao() : linha.origem());
    }

    private Totais totais(List<MovimentoLinha> linhas) {
        BigDecimal brutoEntradas = BigDecimal.ZERO;
        BigDecimal brutoSaidas = BigDecimal.ZERO;
        BigDecimal estornosEntradas = BigDecimal.ZERO;
        BigDecimal estornosSaidas = BigDecimal.ZERO;
        for (MovimentoLinha linha : linhas) {
            BigDecimal valor = linha.valor() == null ? BigDecimal.ZERO : linha.valor();
            if ("ENTRADA".equals(linha.tipo())) {
                brutoEntradas = brutoEntradas.add(valor);
            } else if ("SAIDA".equals(linha.tipo())) {
                brutoSaidas = brutoSaidas.add(valor);
            } else if ("ESTORNO".equals(linha.tipo()) && "ENTRADA".equals(linha.tipoOriginal())) {
                estornosEntradas = estornosEntradas.add(valor);
            } else if ("ESTORNO".equals(linha.tipo()) && "SAIDA".equals(linha.tipoOriginal())) {
                estornosSaidas = estornosSaidas.add(valor);
            }
        }
        BigDecimal liquidoEntradas = brutoEntradas.subtract(estornosEntradas);
        BigDecimal liquidoSaidas = brutoSaidas.subtract(estornosSaidas);
        return new Totais(brutoEntradas, brutoSaidas, estornosEntradas, estornosSaidas,
                liquidoEntradas, liquidoSaidas, liquidoEntradas.subtract(liquidoSaidas),
                linhas.size());
    }

    private Map<String, BigDecimal> subtotais(List<MovimentoLinha> linhas, String tipoOriginal) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        if ("ENTRADA".equals(tipoOriginal)) {
            mapa.put("ALUGUEL", BigDecimal.ZERO);
            mapa.put("OUTRAS_ENTRADAS", BigDecimal.ZERO);
        } else {
            mapa.put("MANUTENCAO", BigDecimal.ZERO);
            mapa.put("TRIBUTOS", BigDecimal.ZERO);
            mapa.put("ADMINISTRATIVO", BigDecimal.ZERO);
            mapa.put("OUTRAS_SAIDAS", BigDecimal.ZERO);
        }
        for (MovimentoLinha linha : linhas) {
            String categoria = linha.categoriaOriginal() == null
                    ? linha.categoria() : linha.categoriaOriginal();
            if (categoria == null || !mapa.containsKey(categoria)) {
                continue;
            }
            BigDecimal valor = linha.valor() == null ? BigDecimal.ZERO : linha.valor();
            if (tipoOriginal.equals(linha.tipo())) {
                mapa.put(categoria, mapa.get(categoria).add(valor));
            } else if ("ESTORNO".equals(linha.tipo())
                    && tipoOriginal.equals(linha.tipoOriginal())) {
                mapa.put(categoria, mapa.get(categoria).subtract(valor));
            }
        }
        return mapa;
    }

    private List<MovimentoLinha> porTipo(List<MovimentoLinha> linhas, String tipo) {
        return linhas.stream().filter(linha -> tipo.equals(linha.tipo())).toList();
    }

    private BigDecimal somarContratos(List<ContratoLinha> linhas, boolean atraso) {
        BigDecimal soma = BigDecimal.ZERO;
        for (ContratoLinha linha : linhas) {
            BigDecimal valor = atraso ? linha.valorEmAtraso() : linha.valorAluguel();
            if (valor != null) {
                soma = soma.add(valor);
            }
        }
        return soma;
    }

    private BigDecimal somarContratosVigentes(List<ContratoLinha> linhas, LocalDate hoje) {
        BigDecimal soma = BigDecimal.ZERO;
        for (ContratoLinha linha : linhas) {
            if (vigente(linha, hoje) && linha.valorAluguel() != null) {
                soma = soma.add(linha.valorAluguel());
            }
        }
        return soma;
    }

    private boolean vigente(ContratoLinha linha, LocalDate hoje) {
        return linha.inicio() != null && !linha.inicio().isAfter(hoje)
                && (linha.fim() == null || !linha.fim().isBefore(hoje));
    }

    private String situacao(ContratoLinha linha, Tipo tipo) {
        if (tipo == Tipo.CONTRATOS_ATRASADOS || linha.atrasado()) {
            return "Atrasado";
        }
        if (tipo == Tipo.CONTRATOS_A_VENCER
                || "A_VENCER".equals(texto(linha.situacaoValidade()))) {
            return "Próximo do vencimento";
        }
        if ("VENCIDO".equals(texto(linha.situacaoValidade()))) {
            return "Vencido";
        }
        return "Vigente";
    }

    private Tom tomSituacao(ContratoLinha linha, Tipo tipo) {
        String situacao = situacao(linha, tipo);
        return switch (situacao) {
            case "Atrasado" -> Tom.NEGATIVO;
            case "Próximo do vencimento" -> Tom.ALERTA;
            case "Vencido" -> Tom.MUTED;
            default -> Tom.POSITIVO;
        };
    }

    private String dias(Integer dias) {
        if (dias == null) {
            return "—";
        }
        return dias + (dias == 1 ? " dia" : " dias");
    }

    private String data(LocalDate data) {
        return data == null ? "Indeterminado" : DATA.format(data);
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
        return dados.janelaDias();
    }

    private BigDecimal naoNegativo(BigDecimal valor) {
        return valor == null || valor.signum() < 0 ? BigDecimal.ZERO : valor;
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
            return "—";
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
        String semAcento = Normalizer.normalize(valor.trim().replace('_', ' '),
                Normalizer.Form.NFD).replaceAll("\\p{M}", "");
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
        return valor == null || valor.isBlank() ? "—" : valor.trim();
    }

    private record Registro(String nome, String caminho) {
    }
}
