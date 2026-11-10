package br.com.manuelo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;

@Route(value = "home", layout = MainLayout.class)
@PageTitle("Visão geral")
public class HomeView extends VerticalLayout {

    record Movimentacao(String data, String descricao, String categoria, String valor) {}

    private final Grid<Movimentacao> grid = new Grid<>();
    private final Span imoveisAlugados = new Span("0");
    private final Span imoveisCadastrados = new Span("0");
    private final Span contratosVencendo = new Span("0");
    private final Span contratosAtrasados = new Span("0");

    public HomeView() {
        setSizeFull();
        setPadding(true);
        getStyle().set("background", "#fff9e8").set("flex", "1").set("overflow", "auto");

        add(criarTopo(), criarCards(), criarTabela());

        atualizarResumo(0, 0, 0, 0);
        atualizarMovimentacoes(new ArrayList<>());
    }

   

    private Component criarTopo() {
        H2 titulo = new H2("Visão geral");
        Button exportar = new Button("Exportar");
        Button novo = new Button("+ Novo Movimentação");
        novo.getStyle().set("background", "#5b5bf0").set("color", "white");

        HorizontalLayout topo = new HorizontalLayout(titulo, new HorizontalLayout(exportar, novo));
        topo.setWidthFull();
        topo.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topo.setAlignItems(FlexComponent.Alignment.CENTER);
        return topo;
    }

    private Component criarCards() {
        HorizontalLayout cards = new HorizontalLayout(
                card("Imóveis alugados", imoveisAlugados, "#00a884", "imoveis?filtro=alugado"),
                card("Imóveis cadastrados", imoveisCadastrados, "#f59e0b", "imoveis"),
                card("Contratos próximos dos vencimentos", contratosVencendo, "#4f46e5", "contratos?filtro=vencendo"),
                card("Contratos atrasados", contratosAtrasados, "#e11d48", "contratos?filtro=atrasados"));
        cards.setWidthFull();
        return cards;
    }

    private Component criarTabela() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.addColumn(Movimentacao::data).setHeader("Data").setFlexGrow(0).setWidth("90px");
        grid.addColumn(Movimentacao::descricao).setHeader("Descrição");
        grid.addComponentColumn(m -> badge(m.categoria())).setHeader("Categoria");
        grid.addComponentColumn(m -> {
            Span v = new Span(m.valor());
            v.getStyle()
                    .set("color", m.valor().startsWith("+") ? "#00a884" : "#e11d48")
                    .set("font-weight", "bold");
            return v;
        }).setHeader("Valor");
        grid.setEmptyStateText("Nenhuma movimentação registrada");
        grid.setHeight("300px");

        H2 titulo = new H2("Movimentação recentes");
        titulo.getStyle().set("font-size", "1rem").set("margin", "0");
        Span sub = new Span("Últimas movimentações registradas no sistema");
        sub.getStyle().set("font-size", "0.75rem").set("color", "gray");
        Button verTudo = new Button("Ver tudo");

        VerticalLayout textos = new VerticalLayout(titulo, sub);
        textos.setPadding(false);
        textos.setSpacing(false);

        HorizontalLayout cabecalho = new HorizontalLayout(textos, verTudo);
        cabecalho.setWidthFull();
        cabecalho.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout caixa = new VerticalLayout(cabecalho, grid);
        caixa.setWidthFull();
        caixa.getStyle().set("background", "white").set("border-radius", "10px");
        return caixa;
    }

    // ---------- Métodos para o backend usar depois ----------

    public void atualizarResumo(int alugados, int cadastrados, int vencendo, int atrasados) {
        imoveisAlugados.setText(String.valueOf(alugados));
        imoveisCadastrados.setText(String.valueOf(cadastrados));
        contratosVencendo.setText(String.valueOf(vencendo));
        contratosAtrasados.setText(String.valueOf(atrasados));
    }

    public void atualizarMovimentacoes(List<Movimentacao> lista) {
        grid.setItems(lista);
    }



    private Component card(String rotulo, Span numero, String cor, String destino) {
        Span r = new Span(rotulo);
        r.getStyle().set("font-size", "0.8rem").set("color", "#555");
        numero.getStyle().set("font-size", "2rem").set("font-weight", "bold");

        VerticalLayout c = new VerticalLayout(r, numero);
        c.setSpacing(false);
        c.getStyle()
                .set("background", "white")
                .set("border-radius", "10px")
                .set("border-left", "4px solid " + cor)
                .set("flex", "1")
                .set("cursor", "pointer");
        c.addClickListener(e -> c.getUI().ifPresent(ui -> ui.navigate(destino)));
        return c;
    }

    private Span badge(String texto) {
        String cor = switch (texto) {
            case "Entrada" -> "#00a884";
            case "Manutenção" -> "#f59e0b";
            default -> "#e11d48";
        };
        Span b = new Span(texto);
        b.getStyle()
                .set("color", cor)
                .set("border", "1px solid " + cor)
                .set("border-radius", "6px")
                .set("padding", "2px 8px")
                .set("font-size", "0.7rem");
        return b;
    }
}