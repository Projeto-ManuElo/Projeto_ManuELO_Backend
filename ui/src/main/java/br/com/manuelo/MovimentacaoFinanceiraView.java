package br.com.manuelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "movimentacao-financeira", layout = MainLayout.class)
@PageTitle("Movimentação Financeira")
public class MovimentacaoFinanceiraView extends Div {

    private final List<DemoData.Movimentacao> movimentacoes = DemoData.movimentacoes();
    private final ComboBox<String> categoria = UiKit.select("Categoria", "Selecione uma categoria",
            List.of("Aluguel", "Caução", "Multa", "Manutenção", "Tributos", "Reforma", "Outros"));
    private final ComboBox<String> forma = UiKit.select("Forma de pagamento", "Selecione a forma de pagamento",
            List.of("Pix", "Boleto", "Dinheiro", "Transferência"));
    private final TextField valor = UiKit.text("Valor", "R$ 0,00");
    private final DatePicker data = UiKit.date("Data");
    private final TextField descricao = UiKit.text("Descrição", "Ex.: reparo hidráulico - Ed. Aurora");
    private final Div tabela = new Div();
    private final Button entrada = new Button("Entrada");
    private final Button saida = new Button("Saída");
    private final Button pesquisar = UiKit.primary("Pesquisar Lançamentos", e -> UiKit.aviso("Filtros de lançamentos em preparação."));
    private final Button registrar = UiKit.primary("Registrar Entrada", e -> registrar());
    private boolean tipoEntrada = true;

    public MovimentacaoFinanceiraView() {
        addClassName("page");

        Button exportar = UiKit.secondary("Exportar extrato", e -> UiKit.aviso("Extrato exportado com sucesso."));
        add(UiKit.header("Movimentação Financeira", null, exportar, pesquisar));

        entrada.addClassName("segment");
        saida.addClassName("segment");
        entrada.addClickListener(e -> alternarTipo(true));
        saida.addClickListener(e -> alternarTipo(false));

        Div segmentado = new Div(entrada, saida);
        segmentado.addClassName("segmented");

        descricao.addClassName("full");
        Div comprovante = new Div(new Span("Comprovante"), UiKit.upload(
                "Arraste o recibo aqui ou clique para selecionar", "PDF, JPG ou PNG, até 10 MB"));
        comprovante.addClassName("full");
        comprovante.addClassName("stack");

        Div grid = UiKit.formGrid(categoria, forma, valor, data, descricao, comprovante);
        Button cancelar = UiKit.secondary("Cancelar", e -> limpar());
        Div acoes = new Div(registrar, cancelar);
        acoes.addClassName("form-actions");
        acoes.addClassName("start");

        Div cardForm = UiKit.card(segmentado, grid, acoes);
        add(cardForm);

        tabela.addClassName("table");
        tabela.addClassName("extrato");
        add(UiKit.card(UiKit.cardHeader("Extrato do período", null), tabela));

        atualizarTipo();
        renderizar();
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
            categoria.setItems(List.of("Aluguel", "Caução", "Multa", "Outros"));
        } else {
            saida.addClassName("active");
            categoria.setItems(List.of("Manutenção", "Tributos", "Reforma", "Outros"));
        }
        categoria.clear();
        pesquisar.setVisible(!tipoEntrada);
        registrar.setText(tipoEntrada ? "Registrar Entrada" : "Registrar saída");
    }

    private void limpar() {
        categoria.clear();
        forma.clear();
        valor.clear();
        data.clear();
        descricao.clear();
    }

    private void registrar() {
        BigDecimal valorNumerico;
        try {
            String textoValor = valor.getValue() == null ? "" : valor.getValue().trim();
            valorNumerico = new BigDecimal(textoValor.replace(".", "").replace(",", "."));
            if (valorNumerico.signum() <= 0) {
                throw new NumberFormatException();
            }
        } catch (Exception ex) {
            valor.setInvalid(true);
            UiKit.aviso("Informe um valor válido.");
            return;
        }
        if (descricao.isEmpty()) {
            descricao.setInvalid(true);
            UiKit.aviso("Informe a descrição do lançamento.");
            return;
        }
        String tipo = tipoEntrada ? "Entrada" : "Saída";
        String categoriaTexto = categoria.getValue() == null ? "Outros" : categoria.getValue();
        LocalDate dataMovimento = data.getValue() == null ? LocalDate.now() : data.getValue();

        UiKit.confirmar("Confirmar Lançamento?",
                "Você tem certeza de que todas as informações estão corretas? Após essa confirmação o lançamento só será editável para o administrador.",
                "Confirmar", tipoEntrada, () -> {
                    BigDecimal assinado = tipoEntrada ? valorNumerico : valorNumerico.negate();
                    movimentacoes.add(0, new DemoData.Movimentacao(dataMovimento, descricao.getValue(),
                            categoriaTexto, assinado));
                    renderizar();
                    limpar();
                    UiKit.aviso(tipo + " registrada com sucesso.");
                });
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-extrato", "Data", "Tipo", "Categoria", "Valor"));
        for (DemoData.Movimentacao movimentacao : movimentacoes) {
            String tipo = movimentacao.valor().signum() >= 0 ? "Entrada" : "Saída";
            Span valorSpan = UiKit.money(movimentacao.valor().abs(), false, true);
            valorSpan.addClassName("amount");
            tabela.add(UiKit.tableRow("cols-extrato",
                    new Span(UiKit.diaMes(movimentacao.data())),
                    UiKit.badge(tipo),
                    UiKit.muted(movimentacao.categoria()),
                    valorSpan));
        }
    }
}
