package com.Projeto.manuelo.servico;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.seguranca.UsuarioDetalhes;

/**
 * A-40/A-41/A-42: movimentação financeira transacional (R01/R02/R03/R04/R07).
 *
 * <p>Entradas de aluguel exigem contrato e usam a competência do mês da data;
 * caução é depósito em livro próprio e não gera receita; saídas exigem
 * comprovante gravado na mesma transação (trigger diferido); estorno é exclusivo
 * do administrador e a correção é estorno + nova movimentação com
 * {@code corrige_movimentacao_id}. O extrato/resumo usam o valor assinado de R09.</p>
 */
@Service
@Transactional
public class MovimentacaoFinanceiraService {

    private static final long TAMANHO_MAXIMO_COMPROVANTE = 10L * 1024 * 1024;
    private static final Set<String> MIMES_PERMITIDOS = Set.of(
            "application/pdf", "image/jpeg", "image/png");

    private static final String BASE_EXTRATO = """
            SELECT mv.id,
                   mv.tipo::text AS tipo,
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
                   mv.forma_pagamento::text AS forma,
                   mv.origem_descricao AS origem,
                   (mv.tipo <> 'ESTORNO' AND EXISTS (
                     SELECT 1 FROM movimentacoes e
                      WHERE e.movimentacao_original_id = mv.id
                   )) AS estornado
              FROM movimentacoes mv
              LEFT JOIN movimentacoes o ON o.id = mv.movimentacao_original_id
            """;

    private static final String BASE_RESUMO = """
            SELECT COALESCE(SUM(CASE
                     WHEN mv.tipo = 'ENTRADA' THEN mv.valor
                     WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA' THEN -mv.valor
                     ELSE 0 END), 0) AS entradas,
                   COALESCE(SUM(CASE
                     WHEN mv.tipo = 'SAIDA' THEN mv.valor
                     WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA' THEN -mv.valor
                     ELSE 0 END), 0) AS saidas,
                   COALESCE(SUM(CASE
                     WHEN mv.tipo = 'ENTRADA' THEN mv.valor
                     WHEN mv.tipo = 'SAIDA' THEN -mv.valor
                     WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA' THEN -mv.valor
                     WHEN mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA' THEN mv.valor
                     ELSE 0 END), 0) AS saldo,
                   COUNT(*)::integer AS quantidade,
                   COALESCE(SUM(CASE
                     WHEN mv.tipo = 'ESTORNO' THEN mv.valor
                     ELSE 0 END), 0) AS total_estornos
              FROM movimentacoes mv
              LEFT JOIN movimentacoes o ON o.id = mv.movimentacao_original_id
            """;

    public record ExtratoItem(String id, String tipo, String categoria, BigDecimal valorAssinado,
            LocalDate data, String descricao, String forma, String origem, boolean estornado) {
    }

    public record Resumo(BigDecimal entradas, BigDecimal saidas, BigDecimal saldo, int quantidade,
            BigDecimal totalEstornos) {

        public Resumo(BigDecimal entradas, BigDecimal saidas, BigDecimal saldo, int quantidade) {
            this(entradas, saidas, saldo, quantidade, BigDecimal.ZERO);
        }
    }

    private final JdbcClient jdbc;
    private final DocumentoService documentos;

    public MovimentacaoFinanceiraService(JdbcTemplate jdbcTemplate, DocumentoService documentos) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.documentos = documentos;
    }

    public String registrarEntrada(String categoriaUi, String contratoId, BigDecimal valor,
            LocalDate data, String descricao, String formaUi) {
        return registrarEntrada(categoriaUi, contratoId, valor, data, descricao, formaUi, null,
                null);
    }

    public String registrarEntrada(String categoriaUi, String contratoId, BigDecimal valor,
            LocalDate data, String descricao, String formaUi, String imovelId,
            LocalDate competencia) {
        UsuarioDetalhes ator = atorOperador();
        return registrarEntrada(ator, categoriaUi, contratoId, valor, data, descricao, formaUi,
                null, imovelId, competencia);
    }

    public String registrarSaida(String categoriaUi, BigDecimal valor, LocalDate data,
            String descricao, String formaUi, String favorecido, String nomeArquivo, String mime,
            byte[] comprovante) {
        UsuarioDetalhes ator = atorOperador();
        return registrarSaida(ator, categoriaUi, valor, data, descricao, formaUi, favorecido,
                nomeArquivo, mime, comprovante, null);
    }

    public void estornar(String movimentacaoId, String justificativa) {
        UsuarioDetalhes ator = atorAdmin();
        UUID originalId = uuid(movimentacaoId, "Identificador da movimentação inválido.");
        String justificativaFinal = justificativa(justificativa, "Informe a justificativa do estorno.");
        estornar(originalId, justificativaFinal, ator);
    }

    public String corrigir(String movimentacaoId, String categoriaUi, String contratoId,
            BigDecimal valor, LocalDate data, String descricao, String formaUi, String favorecido,
            String nomeArquivo, String mime, byte[] comprovante) {
        return corrigir(movimentacaoId, categoriaUi, contratoId, valor, data, descricao, formaUi,
                favorecido, nomeArquivo, mime, comprovante, "Correção administrativa");
    }

    public String corrigir(String movimentacaoId, String categoriaUi, String contratoId,
            BigDecimal valor, LocalDate data, String descricao, String formaUi, String favorecido,
            String nomeArquivo, String mime, byte[] comprovante, String justificativa) {
        UsuarioDetalhes ator = atorAdmin();
        UUID originalId = uuid(movimentacaoId, "Identificador da movimentação inválido.");
        Original original = buscarOriginal(originalId);
        if (original == null) {
            throw new IllegalArgumentException("Movimentação não encontrada: " + movimentacaoId);
        }
        if ("ESTORNO".equals(original.tipo())) {
            throw new IllegalArgumentException("Não é permitido corrigir um estorno.");
        }
        String justificativaFinal = justificativa(justificativa,
                "Informe a justificativa da correção.");

        BigDecimal valorFinal = valor == null ? original.valor() : valor;
        LocalDate dataFinal = data == null ? LocalDate.now() : data;
        String descricaoFinal = textoOpcional(descricao) == null
                ? original.descricao() : descricao;
        String formaFinal = textoOpcional(formaUi) == null ? original.forma() : formaUi;
        String contratoFinal = textoOpcional(contratoId) == null
                ? (original.contratoId() == null ? null : original.contratoId().toString())
                : contratoId;

        estornar(originalId, justificativaFinal, ator);

        String categoriaFinal = textoOpcional(categoriaUi) == null
                ? (original.origem() == null ? original.categoria() : original.origem())
                : categoriaUi;

        if ("ENTRADA".equals(original.tipo())) {
            return registrarEntrada(ator, categoriaFinal, contratoFinal, valorFinal, dataFinal,
                    descricaoFinal, formaFinal, originalId);
        }

        String favorecidoFinal = textoOpcional(favorecido) == null
                ? original.favorecido() : favorecido;
        return registrarSaida(ator, categoriaFinal, valorFinal, dataFinal, descricaoFinal,
                formaFinal, favorecidoFinal, nomeArquivo, mime, comprovante, originalId);
    }

    @Transactional(readOnly = true)
    public List<ExtratoItem> extrato(LocalDate inicio, LocalDate fim, String tipoUi,
            String busca) {
        Consulta consulta = consulta(inicio, fim, tipoUi, busca);
        return jdbc.sql(BASE_EXTRATO + consulta.filtro()
                + " ORDER BY mv.data_movimentacao DESC, mv.criado_em DESC, mv.id")
                .params(consulta.parametros())
                .query((rs, rowNum) -> new ExtratoItem(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("tipo"),
                        rs.getString("categoria"),
                        rs.getBigDecimal("valor_assinado"),
                        rs.getObject("data_movimentacao", LocalDate.class),
                        rs.getString("descricao"),
                        rs.getString("forma"),
                        rs.getString("origem"),
                        rs.getBoolean("estornado")))
                .list();
    }

    @Transactional(readOnly = true)
    public List<ExtratoItem> extrato(LocalDate inicio, LocalDate fim) {
        return extrato(inicio, fim, null, null);
    }

    @Transactional(readOnly = true)
    public Resumo resumo(LocalDate inicio, LocalDate fim, String tipoUi, String busca) {
        Consulta consulta = consulta(inicio, fim, tipoUi, busca);
        return jdbc.sql(BASE_RESUMO + consulta.filtro())
                .params(consulta.parametros())
                .query((rs, rowNum) -> new Resumo(
                        rs.getBigDecimal("entradas"),
                        rs.getBigDecimal("saidas"),
                        rs.getBigDecimal("saldo"),
                        rs.getInt("quantidade"),
                        rs.getBigDecimal("total_estornos")))
                .single();
    }

    @Transactional(readOnly = true)
    public Resumo resumo(LocalDate inicio, LocalDate fim) {
        return resumo(inicio, fim, null, null);
    }

    private String registrarEntrada(UsuarioDetalhes ator, String categoriaUi, String contratoId,
            BigDecimal valor, LocalDate data, String descricao, String formaUi, UUID corrigeId) {
        return registrarEntrada(ator, categoriaUi, contratoId, valor, data, descricao, formaUi,
                corrigeId, null, null);
    }

    private String registrarEntrada(UsuarioDetalhes ator, String categoriaUi, String contratoId,
            BigDecimal valor, LocalDate data, String descricao, String formaUi, UUID corrigeId,
            String imovelUi, LocalDate competenciaUi) {
        validarValor(valor);
        LocalDate quando = dataNaoFutura(data);
        String categoria = categoriaEntrada(categoriaUi);

        if ("CAUCAO".equals(categoria)) {
            if (corrigeId != null) {
                throw new IllegalArgumentException(
                        "Depósito de caução não pode ser corrigido como movimentação.");
            }
            return depositarCaucao(ator, contratoId, valor, quando);
        }

        String descricaoFinal = descricao(descricao);
        String forma = formaEnum(formaUi);
        if ("CAUCAO".equals(forma)) {
            throw new IllegalArgumentException(
                    "Forma CAUÇÃO só é usada no pagamento com abatimento de caução (A-32).");
        }

        if ("ALUGUEL".equals(categoria)) {
            UUID contrato = uuid(contratoId, "Selecione o contrato do aluguel.");
            UUID imovel = imovelDoContrato(contrato);
            if (imovelUi != null && !imovelUi.isBlank()) {
                UUID imovelInformado = uuid(imovelUi, "Identificador do imóvel inválido.");
                if (!imovelInformado.equals(imovel)) {
                    throw new IllegalArgumentException(
                            "O imóvel deve ser o mesmo do contrato selecionado.");
                }
            }
            LocalDate competencia = competenciaUi == null
                    ? quando.withDayOfMonth(1) : competenciaUi.withDayOfMonth(1);
            if (competencia.isAfter(LocalDate.now().withDayOfMonth(1))) {
                throw new IllegalArgumentException("A competência não pode ser futura.");
            }
            return inserirEntrada(ator, "ALUGUEL", valor, quando, descricaoFinal, forma, imovel,
                    contrato, competencia, null, corrigeId);
        }

        return inserirEntrada(ator, "OUTRAS_ENTRADAS", valor, quando, descricaoFinal, forma, null,
                null, null, origemEntrada(categoriaUi), corrigeId);
    }

    private String registrarSaida(UsuarioDetalhes ator, String categoriaUi, BigDecimal valor,
            LocalDate data, String descricao, String formaUi, String favorecido,
            String nomeArquivo, String mime, byte[] comprovante, UUID corrigeId) {
        validarValor(valor);
        LocalDate quando = dataNaoFutura(data);
        String descricaoFinal = descricao(descricao);
        String forma = formaEnum(formaUi);
        if ("CAUCAO".equals(forma)) {
            throw new IllegalArgumentException(
                    "Forma CAUÇÃO só é usada no pagamento com abatimento de caução (A-32).");
        }
        validarComprovante(comprovante, mime);

        String categoria = categoriaSaida(categoriaUi);
        String origem = "OUTRAS_SAIDAS".equals(categoria) ? origemSaida(categoriaUi) : null;
        String favorecidoFinal = textoOpcional(favorecido);

        String id = inserirSaida(ator, valor, quando, descricaoFinal, forma, favorecidoFinal,
                categoria, origem, corrigeId);

        documentos.salvar("Comprovante - " + descricaoFinal, "Comprovante", quando,
                "MOVIMENTACAO", id, nomeArquivo, mime, comprovante);
        return id;
    }

    private String depositarCaucao(UsuarioDetalhes ator, String contratoId, BigDecimal valor,
            LocalDate data) {
        UUID contrato = uuid(contratoId, "Selecione o contrato da caução.");
        imovelDoContrato(contrato);
        return jdbc.sql("""
                INSERT INTO caucao_movimentacoes (contrato_id, tipo, valor, data, criado_por)
                VALUES (:contrato, 'DEPOSITO'::tipo_caucao, :valor, :data, :ator)
                RETURNING id::text
                """)
                .param("contrato", contrato)
                .param("valor", valor)
                .param("data", data)
                .param("ator", ator.getId())
                .query(String.class)
                .single();
    }

    private String inserirEntrada(UsuarioDetalhes ator, String categoria, BigDecimal valor,
            LocalDate data, String descricao, String forma, UUID imovel, UUID contrato,
            LocalDate competencia, String origem, UUID corrigeId) {
        return jdbc.sql("""
                INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                                           forma_pagamento, imovel_id, responsavel_id, contrato_id,
                                           competencia, origem_descricao, corrige_movimentacao_id)
                VALUES ('ENTRADA', CAST(:categoria AS categoria_movimentacao), :valor, :data,
                        :descricao, CAST(:forma AS forma_pagamento), :imovel, :responsavel,
                        :contrato, :competencia, :origem, :corrige)
                RETURNING id::text
                """)
                .param("categoria", categoria)
                .param("valor", valor)
                .param("data", data)
                .param("descricao", descricao)
                .param("forma", forma)
                .param("imovel", imovel)
                .param("responsavel", ator.getId())
                .param("contrato", contrato)
                .param("competencia", competencia)
                .param("origem", origem)
                .param("corrige", corrigeId)
                .query(String.class)
                .single();
    }

    private String inserirSaida(UsuarioDetalhes ator, BigDecimal valor, LocalDate data,
            String descricao, String forma, String favorecido, String categoria, String origem,
            UUID corrigeId) {
        return jdbc.sql("""
                INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                                           forma_pagamento, responsavel_id, origem_descricao,
                                           favorecido, corrige_movimentacao_id)
                VALUES ('SAIDA', CAST(:categoria AS categoria_movimentacao), :valor, :data,
                        :descricao, CAST(:forma AS forma_pagamento), :responsavel, :origem,
                        :favorecido, :corrige)
                RETURNING id::text
                """)
                .param("categoria", categoria)
                .param("valor", valor)
                .param("data", data)
                .param("descricao", descricao)
                .param("forma", forma)
                .param("responsavel", ator.getId())
                .param("origem", origem)
                .param("favorecido", favorecido)
                .param("corrige", corrigeId)
                .query(String.class)
                .single();
    }

    private void estornar(UUID originalId, String justificativa, UsuarioDetalhes ator) {
        Original original = buscarOriginal(originalId);
        if (original == null) {
            throw new IllegalArgumentException("Movimentação não encontrada: " + originalId);
        }
        if ("ESTORNO".equals(original.tipo())) {
            throw new IllegalArgumentException("Não é permitido estornar um estorno.");
        }
        if (existeEstorno(originalId)) {
            throw new IllegalStateException("A movimentação já possui estorno.");
        }

        jdbc.sql("""
                INSERT INTO movimentacoes (tipo, categoria, valor, data_movimentacao, descricao,
                                           responsavel_id, movimentacao_original_id, justificativa)
                VALUES ('ESTORNO', NULL, :valor, :data, :descricao, :responsavel, :original,
                        :justificativa)
                """)
                .param("valor", original.valor())
                .param("data", LocalDate.now())
                .param("descricao", descricaoEstorno(original.descricao()))
                .param("responsavel", ator.getId())
                .param("original", originalId)
                .param("justificativa", justificativa)
                .update();
    }

    private Original buscarOriginal(UUID id) {
        return jdbc.sql("""
                SELECT id, tipo::text AS tipo, valor, descricao, categoria::text AS categoria,
                       forma_pagamento::text AS forma, contrato_id, competencia,
                       origem_descricao AS origem, favorecido
                  FROM movimentacoes
                 WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> new Original(
                        rs.getObject("id", UUID.class),
                        rs.getString("tipo"),
                        rs.getBigDecimal("valor"),
                        rs.getString("descricao"),
                        rs.getString("categoria"),
                        rs.getString("forma"),
                        rs.getObject("contrato_id", UUID.class),
                        rs.getObject("competencia", LocalDate.class),
                        rs.getString("origem"),
                        rs.getString("favorecido")))
                .optional()
                .orElse(null);
    }

    private boolean existeEstorno(UUID originalId) {
        return jdbc.sql("SELECT 1 FROM movimentacoes WHERE movimentacao_original_id = :id")
                .param("id", originalId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private UUID imovelDoContrato(UUID contrato) {
        return jdbc.sql("SELECT imovel_id FROM contratos WHERE id = :id")
                .param("id", contrato)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contrato não encontrado: " + contrato));
    }

    private Consulta consulta(LocalDate inicio, LocalDate fim, String tipoUi, String busca) {
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException(
                    "Período inválido: início posterior ao fim.");
        }

        StringBuilder filtro = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> parametros = new LinkedHashMap<>();

        if (inicio != null) {
            filtro.append(" AND mv.data_movimentacao >= :inicio");
            parametros.put("inicio", inicio);
        }
        if (fim != null) {
            filtro.append(" AND mv.data_movimentacao <= :fim");
            parametros.put("fim", fim);
        }

        String tipo = tipoFiltro(tipoUi);
        if ("ENTRADA".equals(tipo)) {
            filtro.append(" AND (mv.tipo = 'ENTRADA'"
                    + " OR (mv.tipo = 'ESTORNO' AND o.tipo = 'ENTRADA'))");
        } else if ("SAIDA".equals(tipo)) {
            filtro.append(" AND (mv.tipo = 'SAIDA'"
                    + " OR (mv.tipo = 'ESTORNO' AND o.tipo = 'SAIDA'))");
        } else if ("ESTORNO".equals(tipo)) {
            filtro.append(" AND mv.tipo = 'ESTORNO'");
        }

        String termo = textoOpcional(busca);
        if (termo != null) {
            filtro.append(" AND lower(concat_ws(' ', mv.descricao, mv.origem_descricao,"
                    + " mv.favorecido, mv.categoria::text)) LIKE :busca ESCAPE '!'");
            parametros.put("busca", like(termo));
        }

        return new Consulta(filtro.toString(), parametros);
    }

    private String tipoFiltro(String tipoUi) {
        String tipo = normalizar(tipoUi);
        if (tipo == null || "TODOS".equals(tipo) || "TODAS".equals(tipo)) {
            return null;
        }
        if (tipo.startsWith("ENTRADA")) {
            return "ENTRADA";
        }
        if (tipo.startsWith("SAIDA")) {
            return "SAIDA";
        }
        if (tipo.startsWith("ESTORNO")) {
            return "ESTORNO";
        }
        throw new IllegalArgumentException("Tipo de movimentação inválido: " + tipoUi);
    }

    private String categoriaEntrada(String categoriaUi) {
        String categoria = normalizar(categoriaUi);
        if (categoria == null) {
            return "OUTRAS_ENTRADAS";
        }
        return switch (categoria) {
            case "ALUGUEL" -> "ALUGUEL";
            case "CAUCAO" -> "CAUCAO";
            default -> "OUTRAS_ENTRADAS";
        };
    }

    private String categoriaSaida(String categoriaUi) {
        String categoria = normalizar(categoriaUi);
        if (categoria == null) {
            return "OUTRAS_SAIDAS";
        }
        return switch (categoria) {
            case "MANUTENCAO" -> "MANUTENCAO";
            case "TRIBUTOS", "TRIBUTO" -> "TRIBUTOS";
            case "ADMINISTRATIVO", "ADMINISTRATIVA" -> "ADMINISTRATIVO";
            default -> "OUTRAS_SAIDAS";
        };
    }

    private String origemEntrada(String categoriaUi) {
        String texto = textoOpcional(categoriaUi);
        if (texto == null) {
            return "Outras entradas";
        }
        String normalizado = normalizar(texto);
        if ("OUTRAS_ENTRADAS".equals(normalizado) || "OUTRAS".equals(normalizado)) {
            return "Outras entradas";
        }
        return texto;
    }

    private String origemSaida(String categoriaUi) {
        String texto = textoOpcional(categoriaUi);
        if (texto == null) {
            return "Outras saídas";
        }
        String normalizado = normalizar(texto);
        if ("OUTRAS_SAIDAS".equals(normalizado) || "OUTRAS".equals(normalizado)) {
            return "Outras saídas";
        }
        return texto;
    }

    private String formaEnum(String formaUi) {
        String forma = normalizar(formaUi);
        if (forma == null) {
            throw new IllegalArgumentException("Selecione a forma de pagamento.");
        }
        return switch (forma) {
            case "PIX" -> "PIX";
            case "BOLETO" -> "BOLETO";
            case "DINHEIRO" -> "DINHEIRO";
            case "TRANSFERENCIA", "TRANSFERENCIA BANCARIA", "TED", "DOC" -> "TRANSFERENCIA";
            case "CAUCAO" -> "CAUCAO";
            default -> throw new IllegalArgumentException(
                    "Forma de pagamento inválida: " + formaUi);
        };
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe um valor maior que zero.");
        }
    }

    private void validarComprovante(byte[] comprovante, String mime) {
        if (comprovante == null || comprovante.length == 0) {
            throw new IllegalArgumentException(
                    "Anexe o comprovante da saída (PDF, JPG ou PNG).");
        }
        if (comprovante.length > TAMANHO_MAXIMO_COMPROVANTE) {
            throw new IllegalArgumentException("O comprovante deve ter no máximo 10 MB.");
        }
        String tipoMime = textoOpcional(mime);
        if (tipoMime == null) {
            throw new IllegalArgumentException(
                    "Anexe o comprovante da saída (PDF, JPG ou PNG).");
        }
        if (!MIMES_PERMITIDOS.contains(tipoMime.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Tipo de arquivo não permitido: " + mime + ". Use PDF, JPG ou PNG.");
        }
    }

    private LocalDate dataNaoFutura(LocalDate data) {
        LocalDate quando = data == null ? LocalDate.now() : data;
        if (quando.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("A data não pode ser futura.");
        }
        return quando;
    }

    private String descricao(String valor) {
        String texto = textoObrigatorio(valor, "Informe a descrição da movimentação.");
        if (texto.length() > 255) {
            throw new IllegalArgumentException("A descrição deve ter até 255 caracteres.");
        }
        return texto;
    }

    private String justificativa(String valor, String mensagem) {
        String texto = textoObrigatorio(valor, mensagem);
        if (texto.length() > 500) {
            throw new IllegalArgumentException("A justificativa deve ter até 500 caracteres.");
        }
        return texto;
    }

    private String descricaoEstorno(String descricaoOriginal) {
        String texto = "Estorno de " + descricaoOriginal;
        return texto.length() <= 255 ? texto : texto.substring(0, 255);
    }

    private String like(String termo) {
        String escapado = termo
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escapado.toLowerCase(Locale.ROOT) + "%";
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

    private UsuarioDetalhes atorOperador() {
        UsuarioDetalhes ator = ator();
        PerfilUsuario perfil = ator.getPerfil();
        if (perfil != PerfilUsuario.ADMINISTRADOR && perfil != PerfilUsuario.COLABORADOR) {
            throw new AccessDeniedException(
                    "Apenas administradores e colaboradores podem registrar movimentações.");
        }
        return ator;
    }

    private UsuarioDetalhes atorAdmin() {
        UsuarioDetalhes ator = ator();
        if (ator.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            throw new AccessDeniedException(
                    "Apenas administradores podem estornar ou corrigir movimentações.");
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

    private record Consulta(String filtro, Map<String, Object> parametros) {
    }

    private record Original(UUID id, String tipo, BigDecimal valor, String descricao,
            String categoria, String forma, UUID contratoId, LocalDate competencia,
            String origem, String favorecido) {
    }
}
