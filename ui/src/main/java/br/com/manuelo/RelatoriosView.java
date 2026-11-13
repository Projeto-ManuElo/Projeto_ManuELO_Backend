package br.com.manuelo;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;

import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.ExtratoItem;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.Resumo;
import com.Projeto.manuelo.servico.RelatorioService;

import br.com.manuelo.dados.Dominio;

@Route(value = "relatorios", layout = MainLayout.class)
@PageTitle("Relatórios")
public class RelatoriosView extends Div {

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(UiKit.PT_BR);
    private static final Pattern TRIMESTRE = Pattern.compile("(\\d)º trimestre (\\d{4})");
    private static final List<String> TIPOS = List.of("Contratos existentes", "Contratos atrasados",
            "Contratos a vencer", "Movimentação financeira");
    private static final List<String> FILTROS = List.of("Todos", "Entrada", "Saída");
    private static final String GRID_EXTRATO =
            "110px 90px 150px minmax(0, 1fr) 120px 130px";

    private final RelatorioService relatorios;
    private final MovimentacaoFinanceiraService movimentacoes;

    private final DatePicker inicio = UiKit.date("Data inicial");
    private final DatePicker fim = UiKit.date("Data final");
    private final ComboBox<String> trimestre = UiKit.select("Trimestre", "Atalho de período",
            opcoesTrimestre());
    private final ComboBox<String> tipo = UiKit.select("Tipo de relatório", "Selecione", TIPOS);
    private final ComboBox<String> filtro = UiKit.select("Filtro", "Todos", FILTROS);
    private final Div kpis = new Div();
    private final Div tabela = new Div();

    public RelatoriosView(RelatorioService relatorios, MovimentacaoFinanceiraService movimentacoes) {
        this.relatorios = relatorios;
        this.movimentacoes = movimentacoes;
        addClassName("page");

        LocalDate hoje = LocalDate.now();
        inicio.setValue(hoje.withDayOfMonth(1));
        fim.setValue(hoje);
        tipo.setValue(TIPOS.get(0));
        filtro.setValue("Todos");
        filtro.setVisible(false);

        add(UiKit.header("Relatórios", "Gere e baixe os relatórios da fundação",
                UiKit.primary("Gerar PDF", e -> gerar())));

        Div acoes = new Div(
                UiKit.secondary("PDF personalizado", e -> abrirPdfPersonalizado()),
                UiKit.secondary("Baixar último PDF", e -> baixarUltimo()));
        acoes.addClassName("form-actions");
        acoes.addClassName("start");

        add(UiKit.card(
                UiKit.cardHeader("Período e tipo",
                        "O trimestre é apenas um atalho; ajuste as datas livremente."),
                UiKit.formGrid(inicio, fim, trimestre, tipo, filtro),
                acoes));

        kpis.addClassName("report-kpis");
        add(kpis);

        tabela.addClassName("table");
        tabela.addClassName("extrato");
        add(UiKit.card(UiKit.cardHeader("Extrato do período", null), tabela));

        inicio.addValueChangeListener(e -> atualizar());
        fim.addValueChangeListener(e -> atualizar());
        tipo.addValueChangeListener(e -> {
            filtro.setVisible(movimentacaoSelecionada());
            atualizar();
        });
        filtro.addValueChangeListener(e -> atualizar());
        trimestre.addValueChangeListener(e -> aplicarTrimestre(e.getValue()));
        atualizar();
    }

    private void aplicarTrimestre(String valor) {
        Matcher matcher = TRIMESTRE.matcher(valor == null ? "" : valor);
        if (!matcher.find()) {
            return;
        }
        int quarter = Integer.parseInt(matcher.group(1));
        int ano = Integer.parseInt(matcher.group(2));
        LocalDate primeiroDia = LocalDate.of(ano, (quarter - 1) * 3 + 1, 1);
        inicio.setValue(primeiroDia);
        fim.setValue(primeiroDia.plusMonths(3).minusDays(1));
    }

    private void atualizar() {
        LocalDate dataInicio = inicio.getValue();
        LocalDate dataFim = fim.getValue();
        if (dataInicio == null || dataFim == null || dataInicio.isAfter(dataFim)) {
            kpis.removeAll();
            tabela.removeAll();
            return;
        }
        String filtroAtual = movimentacaoSelecionada() ? filtro.getValue() : "Todos";
        try {
            Resumo resumo = movimentacoes.resumo(dataInicio, dataFim, filtroAtual, null);
            List<ExtratoItem> itens = movimentacoes.extrato(dataInicio, dataFim, filtroAtual, null);
            renderizarKpis(resumo);
            renderizarExtrato(itens);
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private void renderizarKpis(Resumo resumo) {
        kpis.removeAll();
        kpis.add(kpi("Entradas", resumo.entradas(), "green"));
        kpis.add(kpi("Saídas", resumo.saidas(), "red"));
        kpis.add(kpi("Saldo", resumo.saldo(), null));
    }

    private void renderizarExtrato(List<ExtratoItem> itens) {
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

    private Div cabecalhoExtrato() {
        Div head = new Div();
        head.addClassName("table-head");
        head.getStyle().set("grid-template-columns", GRID_EXTRATO);
        for (String coluna : List.of("Data", "Tipo", "Categoria", "Descrição", "Forma", "Valor")) {
            head.add(new Span(coluna));
        }
        return head;
    }

    private Div linhaExtrato(ExtratoItem item) {
        String tipoLabel = switch (item.tipo() == null ? "" : item.tipo()) {
            case "ENTRADA" -> "Entrada";
            case "SAIDA" -> "Saída";
            case "ESTORNO" -> "Estorno";
            default -> item.tipo();
        };
        String categoria = item.categoria() == null || item.categoria().isBlank()
                ? "—" : Dominio.categoria(item.categoria());
        String forma = item.forma() == null || item.forma().isBlank()
                ? "—" : Dominio.formaPagamento(item.forma());
        Span valor = UiKit.money(item.valorAssinado(), true);
        valor.addClassName("amount");

        Div linha = new Div(
                new Span(UiKit.data(item.data())),
                UiKit.badge(tipoLabel),
                UiKit.muted(categoria),
                new Span(item.descricao() == null ? "" : item.descricao()),
                UiKit.muted(forma),
                valor);
        linha.addClassName("table-row");
        linha.getStyle().set("grid-template-columns", GRID_EXTRATO);
        return linha;
    }

    private void abrirPdfPersonalizado() {
        Dialog modal = new Dialog();
        modal.addClassName("modal");
        modal.setWidth("560px");
        modal.setMaxWidth("94vw");
        modal.setCloseOnOutsideClick(false);

        Div header = UiKit.dialogHeader("PDF personalizado",
                "O período e o tipo selecionados serão usados. Informe uma observação para o arquivo.");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> modal.close()));

        TextField observacao = UiKit.text("Observação", "Ex: relatório gerencial do trimestre");
        Div body = new Div(observacao);
        body.addClassName("modal-body");

        Button cancelar = UiKit.secondary("Cancelar", e -> modal.close());
        Button gerar = UiKit.primary("Gerar PDF", e -> {
            modal.close();
            gerar();
        });

        modal.add(header, body, UiKit.dialogFooter(cancelar, gerar));
        modal.open();
    }

    private void gerar() {
        LocalDate dataInicio = inicio.getValue();
        LocalDate dataFim = fim.getValue();
        if (!validarPeriodo(dataInicio, dataFim)) {
            return;
        }
        String tipoSelecionado = tipo.getValue();
        if (tipoSelecionado == null || tipoSelecionado.isBlank()) {
            UiKit.erro("Selecione o tipo de relatório.");
            return;
        }
        try {
            String id = relatorios.gerar(tipoSelecionado, dataInicio, dataFim,
                    movimentacaoSelecionada() ? filtro.getValue() : "Todos");
            baixar(relatorios.baixar(id));
            atualizar();
            UiKit.aviso("Relatório gerado e baixado.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private void baixarUltimo() {
        try {
            List<RelatorioService.ResumoRelatorio> recentes = relatorios.listarRecentes(1);
            if (recentes.isEmpty()) {
                UiKit.erro("Nenhum relatório foi gerado ainda.");
                return;
            }
            baixar(relatorios.baixar(recentes.get(0).id()));
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private void baixar(RelatorioService.Arquivo arquivo) {
        StreamResource resource = new StreamResource(arquivo.nome(),
                () -> new ByteArrayInputStream(arquivo.conteudo()));
        resource.setContentType(arquivo.mime());
        resource.setHeader("Content-Disposition",
                "attachment; filename=\"" + arquivo.nome().replace("\"", "") + "\"");
        StreamRegistration registro = UI.getCurrent().getSession().getResourceRegistry()
                .registerResource(resource);
        UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
    }

    private boolean validarPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null) {
            inicio.setInvalid(true);
            UiKit.erro("Informe a data inicial do período.");
            return false;
        }
        if (dataFim == null) {
            fim.setInvalid(true);
            UiKit.erro("Informe a data final do período.");
            return false;
        }
        if (dataInicio.isAfter(dataFim)) {
            inicio.setInvalid(true);
            fim.setInvalid(true);
            UiKit.erro("A data inicial não pode ser posterior à data final.");
            return false;
        }
        inicio.setInvalid(false);
        fim.setInvalid(false);
        return true;
    }

    private boolean movimentacaoSelecionada() {
        return "Movimentação financeira".equals(tipo.getValue());
    }

    private Div kpi(String rotulo, BigDecimal valor, String cor) {
        Div label = new Div(rotulo);
        label.addClassName("report-kpi-label");
        Div value = new Div(MOEDA.format(valor == null ? BigDecimal.ZERO : valor));
        value.addClassName("report-kpi-value");
        if (cor != null) {
            value.addClassName(cor);
        }
        Div card = new Div(label, value);
        card.addClassName("report-kpi");
        return card;
    }

    private static List<String> opcoesTrimestre() {
        int ano = LocalDate.now().getYear();
        return List.of("1º trimestre " + ano, "2º trimestre " + ano,
                "3º trimestre " + ano, "4º trimestre " + ano);
    }
}
