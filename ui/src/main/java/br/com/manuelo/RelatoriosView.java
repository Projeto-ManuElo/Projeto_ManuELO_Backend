package br.com.manuelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.GeradorRelatorio;
import br.com.manuelo.dados.MovimentacaoRepositorio;
import br.com.manuelo.dados.RelatorioRepositorio;

@Route(value = "relatorios", layout = MainLayout.class)
@PageTitle("Relatórios")
public class RelatoriosView extends Div {

    private static final Pattern TRIMESTRE = Pattern.compile("(\\d)º trimestre (\\d{4})");

    private final MovimentacaoRepositorio movimentacoes;
    private final RelatorioRepositorio relatorios;
    private final GeradorRelatorio gerador;
    private final Div tabela = new Div();
    private final Div kpis = new Div();
    private final ComboBox<String> trimestre;
    private final ComboBox<String> formato;
    private final ComboBox<String> tipo;

    public RelatoriosView(MovimentacaoRepositorio movimentacoes, RelatorioRepositorio relatorios,
            GeradorRelatorio gerador) {
        this.movimentacoes = movimentacoes;
        this.relatorios = relatorios;
        this.gerador = gerador;
        addClassName("page");

        Button gerar = UiKit.primary("Gerar PDF", e -> gerar());
        add(UiKit.header("Relatórios", null, gerar));

        trimestre = UiKit.select("Trimestre", "Selecione",
                List.of("1º trimestre 2026 (jan-mar)", "2º trimestre 2026 (abr-jun)",
                        "3º trimestre 2026 (jul-set)", "4º trimestre 2026 (out-dez)"));
        trimestre.setValue("3º trimestre 2026 (jul-set)");
        formato = UiKit.select("Formato", "Selecione", List.of("PDF", "XLSX"));
        formato.setValue("PDF");
        tipo = UiKit.select("Tipo de relatório", "Selecione",
                List.of("Contratos existentes", "Movimentação financeira", "Inadimplência"));
        tipo.setValue("Contratos existentes");
        tipo.addClassName("full");

        Div formulario = UiKit.card(UiKit.cardHeader("Gerar relatório trimestral", null),
                UiKit.formGrid(trimestre, formato, tipo));
        add(formulario);

        kpis.addClassName("report-kpis");
        add(kpis);

        tabela.addClassName("table");
        tabela.addClassName("extrato");
        add(UiKit.card(UiKit.cardHeader("Extrato do período", null), tabela));

        trimestre.addValueChangeListener(e -> atualizar());
        tipo.addValueChangeListener(e -> atualizar());
        atualizar();
    }

    private void atualizar() {
        LocalDate[] periodo = periodoSelecionado();
        MovimentacaoRepositorio.Resumo resumo = movimentacoes.resumo(periodo[0], periodo[1]);
        renderizarKpis(resumo);
        renderizarExtrato(periodo);
    }

    private void renderizarKpis(MovimentacaoRepositorio.Resumo resumo) {
        kpis.removeAll();
        kpis.add(kpi("Entradas", dinheiro(resumo.entradas()), "green"));
        kpis.add(kpi("Saídas", dinheiro(resumo.saidas()), "red"));
        kpis.add(kpi("Saldo", dinheiro(resumo.saldo()), null));
    }

    private void renderizarExtrato(LocalDate[] periodo) {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-extrato", "Data", "Tipo", "Categoria", "Valor"));
        for (DemoData.Movimentacao movimentacao : movimentacoes.extrato(periodo[0], periodo[1], 200)) {
            String tipoMov = movimentacao.valor().signum() >= 0 ? "Entrada" : "Saída";
            Span valor = UiKit.money(movimentacao.valor().abs(), false, true);
            valor.addClassName("amount");
            tabela.add(UiKit.tableRow("cols-extrato",
                    new Span(UiKit.diaMes(movimentacao.data())),
                    UiKit.badge(tipoMov),
                    UiKit.muted(movimentacao.categoria()),
                    valor));
        }
    }

    private void gerar() {
        UiKit.confirmar("Gerar relatório?",
                "O relatório será gerado com os dados do trimestre selecionado.",
                "Gerar", true, () -> {
                    try {
                        LocalDate[] periodo = periodoSelecionado();
                        boolean movimentacao = "Movimentação financeira".equals(tipo.getValue());
                        MovimentacaoRepositorio.Resumo resumo = movimentacao
                                ? movimentacoes.resumo(periodo[0], periodo[1])
                                : null;
                        List<List<String>> linhas = movimentacao
                                ? linhasMovimentacao(periodo)
                                : relatorios.linhasContratos(tipo.getValue());
                        String caminho = gerador.gerar(formato.getValue(),
                                "Relatório de " + tipo.getValue(), periodo[0], periodo[1], resumo, linhas);
                        relatorios.registrar(tipo.getValue(), periodo[0], periodo[1], resumo,
                                linhas.size(), caminho);
                        UiKit.aviso("Relatório gerado em " + caminho);
                    } catch (RuntimeException ex) {
                        UiKit.erro(ex.getMessage());
                    }
                });
    }

    private List<List<String>> linhasMovimentacao(LocalDate[] periodo) {
        List<List<String>> linhas = new ArrayList<>();
        for (DemoData.Movimentacao movimentacao : movimentacoes.extrato(periodo[0], periodo[1], 5000)) {
            linhas.add(List.of(UiKit.data(movimentacao.data()), movimentacao.descricao(),
                    movimentacao.categoria(), dinheiro(movimentacao.valor())));
        }
        return linhas;
    }

    private LocalDate[] periodoSelecionado() {
        Matcher matcher = TRIMESTRE.matcher(trimestre.getValue() == null ? "" : trimestre.getValue());
        if (!matcher.find()) {
            LocalDate hoje = LocalDate.now();
            return new LocalDate[] { hoje.withDayOfMonth(1), hoje };
        }
        int quarter = Integer.parseInt(matcher.group(1));
        int ano = Integer.parseInt(matcher.group(2));
        LocalDate inicio = LocalDate.of(ano, (quarter - 1) * 3 + 1, 1);
        return new LocalDate[] { inicio, inicio.plusMonths(3).minusDays(1) };
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

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0,00";
        }
        return "R$ " + valor.toPlainString().replace(".", ",");
    }
}
