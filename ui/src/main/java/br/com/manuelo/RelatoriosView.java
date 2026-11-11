package br.com.manuelo;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "relatorios", layout = MainLayout.class)
@PageTitle("Relatórios")
public class RelatoriosView extends Div {

    private final Div tabela = new Div();

    public RelatoriosView() {
        addClassName("page");

        Button gerar = UiKit.primary("Gerar PDF", e -> UiKit.confirmar("Gerar relatório?",
                "O relatório será gerado em PDF com os dados do trimestre selecionado.",
                "Gerar", true, () -> UiKit.aviso("Relatório gerado com sucesso.")));
        add(UiKit.header("Relatórios", null, gerar));

        ComboBox<String> trimestre = UiKit.select("Trimestre", "Selecione",
                List.of("1º trimestre 2026 (jan-mar)", "2º trimestre 2026 (abr-jun)",
                        "3º trimestre 2026 (jul-set)", "4º trimestre 2026 (out-dez)"));
        trimestre.setValue("3º trimestre 2026 (jul-set)");
        ComboBox<String> formato = UiKit.select("Formato", "Selecione", List.of("PDF", "XLSX"));
        formato.setValue("PDF");
        ComboBox<String> tipo = UiKit.select("Tipo de relatório", "Selecione",
                List.of("Contratos existentes", "Movimentação financeira", "Inadimplência"));
        tipo.setValue("Contratos existentes");
        tipo.addClassName("full");

        Div formulario = UiKit.card(UiKit.cardHeader("Gerar relatório trimestral", null),
                UiKit.formGrid(trimestre, formato, tipo));
        add(formulario);

        Div kpis = new Div(
                kpi("Entradas", "R$ 112.400", "green"),
                kpi("Saídas", "R$ 28.170", "red"),
                kpi("Saldo", "R$ 84.230", null));
        kpis.addClassName("report-kpis");
        add(kpis);

        tabela.addClassName("table");
        tabela.addClassName("extrato");
        add(UiKit.card(UiKit.cardHeader("Extrato do período", null), tabela));
        renderizar();
    }

    private Div kpi(String rotulo, String valor, String cor) {
        Div label = new Div(rotulo);
        label.addClassName("report-kpi-label");
        Div value = new Div(valor);
        value.addClassName("report-kpi-value");
        if (cor != null) {
            value.addClassName(cor);
        }
        Div card = new Div(label, value);
        card.addClassName("report-kpi");
        return card;
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-extrato", "Data", "Tipo", "Categoria", "Valor"));
        for (DemoData.Movimentacao movimentacao : DemoData.movimentacoes()) {
            String tipo = movimentacao.valor().signum() >= 0 ? "Entrada" : "Saída";
            Span valor = UiKit.money(movimentacao.valor().abs(), false, true);
            valor.addClassName("amount");
            tabela.add(UiKit.tableRow("cols-extrato",
                    new Span(UiKit.diaMes(movimentacao.data())),
                    UiKit.badge(tipo),
                    UiKit.muted(movimentacao.categoria()),
                    valor));
        }
    }
}
