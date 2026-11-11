package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "home", layout = MainLayout.class)
@PageTitle("Visão geral")
public class HomeView extends Div {

    public HomeView() {
        addClassName("page");

        Button exportar = UiKit.secondary("Exportar", e -> UiKit.aviso("Exportação gerada com sucesso."));
        Button novo = UiKit.primaryIcon("Novo Movimentação", VaadinIcon.PLUS,
                e -> UiKit.ir("movimentacao-financeira"));
        add(UiKit.header("Visão geral", null, exportar, novo));

        Div kpis = new Div(
                UiKit.kpi("Imóveis alugados", "24", "#0E9F6E", VaadinIcon.TRENDING_UP,
                        "imoveis?filtro=alugado", "1 em manutenção", "#0E9F6E"),
                UiKit.kpi("Imóveis cadastrados", "27", "#F5A623", VaadinIcon.BUILDING,
                        "imoveis", "3 vagos", "#F5A623"),
                UiKit.kpi("Contratos próximos do vencimentos", "3", "#7A5AF8", VaadinIcon.FILE_TEXT,
                        "contratos?filtro=vencendo", "Aguardando resolução", "#3538CD"),
                UiKit.kpi("Contratos atrasados", "4", "#F04438", VaadinIcon.MONEY,
                        "contratos?filtro=atrasados", "2 mais de uma semana sem pagar", "#F04438"));
        kpis.addClassName("kpi-grid");
        add(kpis);

        add(cardMovimentacoes());
    }

    private Div cardMovimentacoes() {
        Div tabela = new Div();
        tabela.addClassName("table");
        tabela.add(UiKit.tableHead("cols-home", "Data", "Descrição", "Categoria", "Valor"));

        for (DemoData.Movimentacao movimentacao : DemoData.homeMovimentacoes()) {
            Span valor = UiKit.money(movimentacao.valor(), true);
            valor.addClassName("amount");
            Div linha = UiKit.tableRow("cols-home",
                    new Span(UiKit.diaMes(movimentacao.data())),
                    new Span(movimentacao.descricao()),
                    UiKit.badge(movimentacao.categoria()),
                    valor);
            tabela.add(linha);
        }

        Button verTudo = UiKit.secondary("Ver tudo", e -> UiKit.ir("movimentacao-financeira"));
        Div card = UiKit.card(
                UiKit.cardHeader("Movimentação recentes", "Últimas movimentações registradas no sistema", verTudo),
                tabela);
        return card;
    }
}
