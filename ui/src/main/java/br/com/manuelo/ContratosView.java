package br.com.manuelo;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.ContratoRepositorio;

@Route(value = "contratos", layout = MainLayout.class)
@PageTitle("Contratos")
public class ContratosView extends Div implements BeforeEnterObserver {

    private final ContratoRepositorio repositorio;
    private List<DemoData.Contrato> contratos;
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel");
    private final TextField cpf = UiKit.text("CPF", "Ex: xxx.yyy.zzz.xz");
    private final TextField email = UiKit.text("Email", "Ex: joao@wmail.com");
    private final TextField validade = UiKit.text("Validade do contrato", "Ex: xx/xx/xx26");
    private final ComboBox<String> situacao = UiKit.select("Situação", "Ex: Atrasado ou não",
            List.of("Ativo", "A vencer", "Atrasado"));
    private final Div tabela = new Div();
    private final ContratoDialog dialog;

    public ContratosView(ContratoRepositorio repositorio) {
        this.repositorio = repositorio;
        this.contratos = repositorio.listar();
        addClassName("page");
        dialog = new ContratoDialog(this::adicionarContrato);

        Button novo = UiKit.primaryIcon("Novo Contrato", VaadinIcon.PLUS, e -> dialog.abrir());
        add(UiKit.header("Contratos", null, novo));

        add(criarFiltros());
        add(criarTabelaCard());
        renderizar();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        contratos = repositorio.listar();
        limpar();
        event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("filtro", List.of()).stream().findFirst()
                .ifPresent(filtro -> {
                    switch (filtro) {
                        case "atrasados" -> situacao.setValue("Atrasado");
                        case "vencendo" -> situacao.setValue("A vencer");
                        case "ativos" -> situacao.setValue("Ativo");
                        default -> {
                        }
                    }
                    renderizar();
                });
    }

    private Div criarFiltros() {
        Div acoes = new Div(
                UiKit.secondary("Cancelar", e -> {
                    limpar();
                    renderizar();
                }),
                UiKit.primary("Filtrar", e -> renderizar()));
        acoes.addClassName("form-actions");

        nome.addClassName("full");
        Div grid = UiKit.formGrid(nome, cpf, email, validade, situacao);
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private Div criarTabelaCard() {
        tabela.addClassName("table");
        return UiKit.card(UiKit.cardHeader("Contratos recentes", null), tabela);
    }

    private void limpar() {
        nome.clear();
        cpf.clear();
        email.clear();
        validade.clear();
        situacao.clear();
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-contratos", "Nome", "Validade", "Endereço", "CPF", "Email"));

        List<DemoData.Contrato> filtrados = contratos.stream()
                .filter(this::corresponde)
                .toList();

        if (filtrados.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhum contrato encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        for (DemoData.Contrato contrato : filtrados) {
            Span validadeSpan = new Span(UiKit.data(contrato.vencimento()) + "  " + textoDias(contrato.dias()));
            Div linha = UiKit.tableRow("cols-contratos",
                    new Span(contrato.inquilino()),
                    validadeSpan,
                    UiKit.muted(contrato.endereco()),
                    new Span(contrato.cpf()),
                    UiKit.muted(contrato.email()));
            linha.addClassName("clickable");
            linha.addClickListener(e -> UI.getCurrent().navigate(ContratoDetalheView.class,
                    QueryParameters.of("id", contrato.id())));
            tabela.add(linha);
        }
    }

    private String textoDias(int dias) {
        if (dias == 0) {
            return "Hoje";
        }
        return dias > 0 ? "Faltar " + dias + " dias" : "Atrasado " + Math.abs(dias) + " dias";
    }

    private boolean corresponde(DemoData.Contrato contrato) {
        String n = valor(nome).toLowerCase(Locale.ROOT);
        String c = valor(cpf).toLowerCase(Locale.ROOT);
        String e = valor(email).toLowerCase(Locale.ROOT);
        String v = valor(validade).toLowerCase(Locale.ROOT);
        return (n.isBlank() || contrato.inquilino().toLowerCase(Locale.ROOT).contains(n))
                && (c.isBlank() || contrato.cpf().toLowerCase(Locale.ROOT).contains(c))
                && (e.isBlank() || contrato.email().toLowerCase(Locale.ROOT).contains(e))
                && (v.isBlank() || UiKit.data(contrato.vencimento()).contains(v))
                && (situacao.getValue() == null || situacao.getValue().isBlank()
                        || situacao.getValue().equals(contrato.situacao()));
    }

    private String valor(TextField field) {
        return field.getValue() == null ? "" : field.getValue().trim();
    }

    private void adicionarContrato(ContratoDialog.DadosContrato dados) {
        try {
            String id = repositorio.criar(dados);
            contratos = repositorio.listar();
            renderizar();
            UiKit.aviso("Contrato criado com sucesso.");
            UI.getCurrent().navigate(ContratoDetalheView.class, QueryParameters.of("id", id));
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }
}
