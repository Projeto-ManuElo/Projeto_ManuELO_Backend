package br.com.manuelo;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.ExtratoItem;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.Resumo;

import br.com.manuelo.dados.ContratoRepositorio;
import br.com.manuelo.dados.Dominio;
import br.com.manuelo.dados.SessaoUsuario;

@Route(value = "movimentacao-financeira", layout = MainLayout.class)
@PageTitle("Movimentação Financeira")
public class MovimentacaoFinanceiraView extends Div {

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(UiKit.PT_BR);
    private static final List<String> CATEGORIAS_ENTRADA = List.of("Aluguel", "Caução", "Multa", "Outros");
    private static final List<String> CATEGORIAS_SAIDA = List.of("Manutenção", "Tributos", "Reforma", "Outros");
    private static final List<String> FORMAS = List.of("Pix", "Boleto", "Dinheiro", "Transferência");
    private static final List<String> TIPOS_EXTRATO = List.of("Todos", "Entrada", "Saída");

    private final MovimentacaoFinanceiraService servico;
    private final Map<String, String> contratosPorRotulo = new LinkedHashMap<>();

    private final ComboBox<String> categoria = UiKit.select("Categoria", "Selecione uma categoria",
            CATEGORIAS_ENTRADA);
    private final ComboBox<String> contrato = UiKit.select("Contrato", "Selecione o contrato", List.of());
    private final ComboBox<String> forma = UiKit.select("Forma de pagamento", "Selecione a forma de pagamento",
            FORMAS);
    private final TextField valor = UiKit.text("Valor", "R$ 0,00");
    private final DatePicker data = UiKit.date("Data");
    private final TextField descricao = UiKit.text("Descrição", "Ex.: reparo hidráulico - Ed. Aurora");
    private final TextField favorecido = UiKit.text("Favorecido", "Ex.: fornecedor ou prestador");
    private Upload upload;
    private final Div tabela = new Div();
    private final Div resumo = new Div();
    private final DatePicker inicio = UiKit.date("Início");
    private final DatePicker fim = UiKit.date("Fim");
    private final ComboBox<String> tipoExtrato = UiKit.select("Tipo", "Todos", TIPOS_EXTRATO);
    private final TextField busca = UiKit.text("Busca", "Descrição, categoria ou favorecido");
    private final Button entrada = new Button("Entrada");
    private final Button saida = new Button("Saída");
    private final Button pesquisar = UiKit.primary("Pesquisar Lançamentos", e -> renderizarExtrato());
    private final Button registrar = UiKit.primary("Registrar Entrada", e -> registrar());
    private final boolean administrador;
    private boolean tipoEntrada = true;
    private UiKit.ArquivoSelecionado comprovante;

    public MovimentacaoFinanceiraView(MovimentacaoFinanceiraService servico,
            ContratoRepositorio contratoRepositorio, SessaoUsuario sessao) {
        this.servico = servico;
        this.administrador = sessao.administrador();
        addClassName("page");

        for (ContratoRepositorio.Opcao opcao : contratoRepositorio.opcoes()) {
            contratosPorRotulo.put(opcao.rotulo(), opcao.id());
        }
        contrato.setItems(contratosPorRotulo.keySet());
        contrato.setVisible(false);
        categoria.addValueChangeListener(e -> contrato.setVisible(exigeContrato(e.getValue())));

        Button exportar = UiKit.secondary("Exportar extrato",
                e -> UiKit.aviso("Exportação disponível após a próxima atualização."));
        add(UiKit.header("Movimentação Financeira", null, exportar));

        add(criarFormulario());
        add(criarFiltros());

        resumo.addClassName("kpi-grid");
        add(resumo);

        tabela.addClassName("table");
        tabela.addClassName("extrato");
        add(UiKit.card(UiKit.cardHeader("Extrato do período", null), tabela));

        atualizarTipo();
        renderizarExtrato();
    }

    private Div criarFormulario() {
        entrada.addClassName("segment");
        saida.addClassName("segment");
        entrada.addClickListener(e -> alternarTipo(true));
        saida.addClickListener(e -> alternarTipo(false));

        Div segmentado = new Div(entrada, saida);
        segmentado.addClassName("segmented");

        descricao.addClassName("full");
        favorecido.addClassName("full");
        favorecido.setVisible(false);
        upload = UiKit.upload("Arraste o recibo aqui ou clique para selecionar",
                "PDF, JPG ou PNG, até 10 MB", arquivo -> comprovante = arquivo);
        Div comprovanteBox = new Div(new Span("Comprovante"), upload);
        comprovanteBox.addClassName("full");
        comprovanteBox.addClassName("stack");

        Div grid = UiKit.formGrid(categoria, contrato, forma, valor, data, descricao, favorecido,
                comprovanteBox);
        Button cancelar = UiKit.secondary("Cancelar", e -> limpar());
        Div acoes = new Div(registrar, cancelar);
        acoes.addClassName("form-actions");
        acoes.addClassName("start");

        return UiKit.card(segmentado, grid, acoes);
    }

    private Div criarFiltros() {
        inicio.setValue(LocalDate.now().withDayOfMonth(1));
        fim.setValue(LocalDate.now());
        tipoExtrato.setValue("Todos");
        busca.addClassName("full");

        Div acoes = new Div(
                UiKit.secondary("Cancelar", e -> {
                    LocalDate hoje = LocalDate.now();
                    inicio.setValue(hoje.withDayOfMonth(1));
                    fim.setValue(hoje);
                    tipoExtrato.setValue("Todos");
                    busca.clear();
                    renderizarExtrato();
                }),
                pesquisar);
        acoes.addClassName("form-actions");

        Div grid = UiKit.formGrid(inicio, fim, tipoExtrato, busca);
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private void alternarTipo(boolean entradaSelecionada) {
        tipoEntrada = entradaSelecionada;
        atualizarTipo();
    }

    private void atualizarTipo() {
        entrada.removeClassName("active");
        saida.removeClassName("active");
        if (tipoEntrada) {
            entrada.addClassName("active");
            categoria.setItems(CATEGORIAS_ENTRADA);
        } else {
            saida.addClassName("active");
            categoria.setItems(CATEGORIAS_SAIDA);
        }
        categoria.clear();
        contrato.clear();
        contrato.setVisible(false);
        favorecido.setVisible(!tipoEntrada);
        registrar.setText(tipoEntrada ? "Registrar Entrada" : "Registrar saída");
    }

    private boolean exigeContrato(String categoriaSelecionada) {
        return "Aluguel".equals(categoriaSelecionada) || "Caução".equals(categoriaSelecionada);
    }

    private void limpar() {
        categoria.clear();
        categoria.setInvalid(false);
        contrato.clear();
        contrato.setInvalid(false);
        contrato.setVisible(false);
        forma.clear();
        forma.setInvalid(false);
        valor.clear();
        valor.setInvalid(false);
        data.clear();
        data.setInvalid(false);
        descricao.clear();
        descricao.setInvalid(false);
        favorecido.clear();
        upload.clearFileList();
        comprovante = null;
    }

    private void registrar() {
        BigDecimal valorNumerico = valor();
        if (valorNumerico == null) {
            return;
        }
        if (descricao.isEmpty()) {
            descricao.setInvalid(true);
            UiKit.erro("Informe a descrição do lançamento.");
            return;
        }
        String categoriaTexto = categoria.getValue();
        if (categoriaTexto == null || categoriaTexto.isBlank()) {
            categoria.setInvalid(true);
            UiKit.erro("Selecione a categoria.");
            return;
        }
        if (forma.getValue() == null || forma.getValue().isBlank()) {
            forma.setInvalid(true);
            UiKit.erro("Selecione a forma de pagamento.");
            return;
        }
        LocalDate dataMovimento = data.getValue() == null ? LocalDate.now() : data.getValue();
        String contratoId = contrato.getValue() == null ? null : contratosPorRotulo.get(contrato.getValue());
        if (tipoEntrada && exigeContrato(categoriaTexto) && contratoId == null) {
            contrato.setInvalid(true);
            UiKit.erro("Selecione o contrato.");
            return;
        }
        UiKit.ArquivoSelecionado arquivo = comprovante;
        if (!tipoEntrada && (arquivo == null || arquivo.conteudo() == null
                || arquivo.conteudo().length == 0)) {
            UiKit.erro("Anexe o comprovante da saída (PDF, JPG ou PNG).");
            return;
        }

        String tipo = tipoEntrada ? "Entrada" : "Saída";
        UiKit.confirmar("Confirmar Lançamento?",
                "Você tem certeza de que todas as informações estão corretas? Após essa confirmação o lançamento só será editável para o administrador.",
                "Confirmar", tipoEntrada, () -> {
                    try {
                        if (tipoEntrada) {
                            servico.registrarEntrada(categoriaTexto, contratoId, valorNumerico,
                                    dataMovimento, descricao.getValue(), forma.getValue());
                        } else {
                            servico.registrarSaida(categoriaTexto, valorNumerico, dataMovimento,
                                    descricao.getValue(), forma.getValue(), favorecido.getValue(),
                                    arquivo.nome(), arquivo.mime(), arquivo.conteudo());
                        }
                        renderizarExtrato();
                        limpar();
                        UiKit.aviso(tipo + " registrada com sucesso.");
                    } catch (RuntimeException ex) {
                        UiKit.erro(ex.getMessage());
                    }
                });
    }

    private BigDecimal valor() {
        try {
            String textoValor = valor.getValue() == null ? "" : valor.getValue().trim()
                    .replace("R$", "").replace(" ", "");
            BigDecimal valorNumerico = new BigDecimal(textoValor.replace(".", "").replace(",", "."));
            if (valorNumerico.signum() <= 0) {
                throw new NumberFormatException();
            }
            return valorNumerico;
        } catch (Exception ex) {
            valor.setInvalid(true);
            UiKit.erro("Informe um valor válido.");
            return null;
        }
    }

    private void renderizarExtrato() {
        List<ExtratoItem> itens;
        Resumo total;
        try {
            LocalDate inicioSelecionado = inicio.getValue();
            LocalDate fimSelecionado = fim.getValue();
            String tipo = tipoExtrato.getValue();
            String termo = busca.getValue();
            itens = servico.extrato(inicioSelecionado, fimSelecionado, tipo, termo);
            total = servico.resumo(inicioSelecionado, fimSelecionado, tipo, termo);
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
            return;
        }

        renderizarResumo(total);
        tabela.removeAll();
        tabela.add(cabecalhoExtrato());

        if (itens.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhum lançamento encontrado",
                    "Ajuste o período ou os filtros e tente novamente."));
            return;
        }

        for (ExtratoItem item : itens) {
            tabela.add(linhaExtrato(item));
        }
    }

    private void renderizarResumo(Resumo total) {
        resumo.removeAll();
        resumo.add(cardResumo("Entradas", total.entradas(), "green"));
        resumo.add(cardResumo("Saídas", total.saidas(), "red"));
        resumo.add(cardResumo("Saldo", total.saldo(), null));
        resumo.add(cardResumo("Estornos", total.totalEstornos(), "red"));
    }

    private Div cardResumo(String rotulo, BigDecimal valorTotal, String cor) {
        Div label = new Div(rotulo);
        label.addClassName("report-kpi-label");
        Div value = new Div(MOEDA.format(valorTotal == null ? BigDecimal.ZERO : valorTotal));
        value.addClassName("report-kpi-value");
        if (cor != null) {
            value.addClassName(cor);
        }
        Div card = new Div(label, value);
        card.addClassName("report-kpi");
        return card;
    }

    private Div cabecalhoExtrato() {
        String[] colunas = administrador
                ? new String[] { "Data", "Tipo", "Categoria", "Descrição", "Forma", "Valor", "", "Ações" }
                : new String[] { "Data", "Tipo", "Categoria", "Descrição", "Forma", "Valor", "" };
        Div head = new Div();
        head.addClassName("table-head");
        head.getStyle().set("grid-template-columns", gridExtrato());
        for (String coluna : colunas) {
            head.add(new Span(coluna));
        }
        return head;
    }

    private Div linhaExtrato(ExtratoItem item) {
        String tipo = switch (item.tipo() == null ? "" : item.tipo()) {
            case "ENTRADA" -> "Entrada";
            case "SAIDA" -> "Saída";
            case "ESTORNO" -> "Estorno";
            default -> item.tipo();
        };
        String categoria = item.categoria() == null || item.categoria().isBlank()
                ? "—" : Dominio.categoria(item.categoria());
        String formaTexto = item.forma() == null || item.forma().isBlank()
                ? "—" : Dominio.formaPagamento(item.forma());

        Span valorAssinado = UiKit.money(item.valorAssinado(), true);
        valorAssinado.addClassName("amount");
        Span estornado = item.estornado() ? UiKit.badge("Estornado", "danger") : new Span("");

        Div linha = new Div(
                new Span(UiKit.data(item.data())),
                UiKit.badge(tipo),
                UiKit.muted(categoria),
                new Span(item.descricao() == null ? "" : item.descricao()),
                UiKit.muted(formaTexto),
                valorAssinado,
                estornado);
        linha.addClassName("table-row");
        linha.getStyle().set("grid-template-columns", gridExtrato());

        if (administrador) {
            linha.add(criarAcoes(item));
        }
        return linha;
    }

    private Div criarAcoes(ExtratoItem item) {
        Div acoes = new Div();
        acoes.addClassName("row");
        acoes.getStyle().set("gap", "8px");
        boolean estorno = "ESTORNO".equals(item.tipo());
        if (!estorno && !item.estornado()) {
            Button editar = UiKit.tertiary("Editar",
                    e -> new EditarMovimentacaoDialog(servico, item, this::renderizarExtrato).open());
            Button estornar = UiKit.tertiary("Estornar",
                    e -> new EstornoDialog(servico, item, this::renderizarExtrato).open());
            acoes.add(editar, estornar);
        }
        return acoes;
    }

    private String gridExtrato() {
        String base = "120px 100px 150px minmax(0, 1fr) 120px 130px 100px";
        return administrador ? base + " 180px" : base;
    }
}
