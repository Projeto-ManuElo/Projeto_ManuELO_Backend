package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.HomeRepositorio;
import br.com.manuelo.dados.MovimentacaoRepositorio;

@Route(value = "home", layout = MainLayout.class)
@PageTitle("Visão geral")
public class HomeView extends Div {

    private final HomeRepositorio indicadores;
    private final MovimentacaoRepositorio movimentacoes;

    public HomeView(HomeRepositorio indicadores, MovimentacaoRepositorio movimentacoes) {
        this.indicadores = indicadores;
        this.movimentacoes = movimentacoes;
        addClassName("page");

        Button novo = UiKit.primaryIcon("Nova movimentação", VaadinIcon.PLUS,
                e -> UiKit.ir("movimentacao-financeira"));
        add(UiKit.header("Visão geral", null, novo));

        HomeRepositorio.Indicadores i = indicadores.indicadores();
        Div kpis = new Div(
                UiKit.kpi("Imóveis alugados", String.valueOf(i.alugados()), "#0E9F6E", VaadinIcon.TRENDING_UP,
                        "imoveis?filtro=alugado", i.manutencao() + " em manutenção", "#0E9F6E"),
                UiKit.kpi("Imóveis cadastrados", String.valueOf(i.cadastrados()), "#F5A623", VaadinIcon.BUILDING,
                        "imoveis", i.vagos() + " vagos", "#F5A623"),
                UiKit.kpi("Contratos próximos do vencimento", String.valueOf(i.aVencer()), "#7A5AF8",
                        VaadinIcon.FILE_TEXT, "contratos?filtro=vencendo",
                        i.aVencer() + " na janela de alerta", "#3538CD"),
                UiKit.kpi("Contratos atrasados", String.valueOf(i.atrasados()), "#F04438", VaadinIcon.MONEY,
                        "contratos?filtro=atrasados",
                        i.atrasadosCriticos() + " em atraso crítico", "#F04438"));
        kpis.addClassName("kpi-grid");
        add(kpis);

        add(cardMovimentacoes());
    }

    private Div cardMovimentacoes() {
        Div tabela = new Div();
        tabela.addClassName("table");
        tabela.add(UiKit.tableHead("cols-home", "Data", "Descrição", "Categoria", "Valor"));

        for (DemoData.Movimentacao movimentacao : movimentacoes.listar(10)) {
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
        return UiKit.card(
                UiKit.cardHeader("Movimentações recentes", "Últimas movimentações registradas no sistema", verTudo),
                tabela);
    }
}
