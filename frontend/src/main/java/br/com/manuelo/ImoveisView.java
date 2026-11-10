package br.com.manuelo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;

@Route(value = "imoveis", layout = MainLayout.class)
@PageTitle("Imóveis")
public class ImoveisView extends VerticalLayout implements BeforeEnterObserver {

    record Imovel(String endereco, String tipo, String inquilino, String status) {}

    private final Grid<Imovel> grid = new Grid<>();
    private final TextField endereco = new TextField("Endereço");
    private final ComboBox<String> tipo = new ComboBox<>("Tipo");
    private final ComboBox<String> status = new ComboBox<>("Status");
    private final TextField inquilino = new TextField("Inquilino vinculado");
    private final ImovelDialog dialog = new ImovelDialog(this::salvarImovel);

    public ImoveisView() {
        setSizeFull();
        setPadding(true);
        getStyle().set("background", "#fff9e8").set("flex", "1").set("overflow", "auto");

        add(criarTopo(), criarFiltros(), criarTabela());

        atualizarImoveis(new ArrayList<>());    }

    // Lê o ?filtro=... da URL e preenche o campo Status
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        limparFiltros();
        event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("filtro", List.of()).stream().findFirst()
                .ifPresent(f -> {
                    switch (f) {
                        case "alugado" -> status.setValue("Alugado");
                        case "vago" -> status.setValue("Vago");
                        case "manutencao" -> status.setValue("Em manutenção");
                        default -> { }
                    }
                    aplicarFiltros();
                });
    }

    private Component criarTopo() {
        H2 titulo = new H2("Imóveis");
        Button exportar = new Button("Exportar");
        Button novo = new Button("+ Novo Imóvel", e -> dialog.abrirNovo());
        novo.getStyle().set("background", "#5b5bf0").set("color", "white");

        HorizontalLayout topo = new HorizontalLayout(titulo, new HorizontalLayout(exportar, novo));
        topo.setWidthFull();
        topo.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topo.setAlignItems(FlexComponent.Alignment.CENTER);
        return topo;
    }

    private Component criarFiltros() {
        H2 titulo = new H2("Filtros");
        titulo.getStyle().set("font-size", "1rem").set("margin", "0");

        endereco.setPlaceholder("Rua, número, bairro");
        endereco.setWidthFull();

        tipo.setItems("Apartamento", "Casa", "Comercial");
        tipo.setPlaceholder("Todos");
        tipo.setWidthFull();

        status.setItems("Alugado", "Vago", "Em manutenção");
        status.setPlaceholder("Todos");
        status.setWidthFull();

        HorizontalLayout linha = new HorizontalLayout(tipo, status);
        linha.setWidthFull();

        inquilino.setPlaceholder("Buscar por nome ou documento");
        inquilino.setWidthFull();

        Button cancelar = new Button("Cancelar", e -> limparFiltros());
        Button filtrar = new Button("Filtrar", e -> aplicarFiltros());
        filtrar.getStyle().set("background", "#5b5bf0").set("color", "white");

        HorizontalLayout botoes = new HorizontalLayout(cancelar, filtrar);
        botoes.setWidthFull();
        botoes.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VerticalLayout caixa = new VerticalLayout(titulo, endereco, linha, inquilino, botoes);
        caixa.setWidthFull();
        caixa.getStyle().set("background", "white").set("border-radius", "10px");
        return caixa;
    }

    private Component criarTabela() {
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.addColumn(Imovel::endereco).setHeader("Endereço");
        grid.addColumn(Imovel::tipo).setHeader("Tipo");
        grid.addColumn(i -> i.inquilino() == null || i.inquilino().isBlank() ? "-" : i.inquilino())
                .setHeader("Inquilino");
        grid.addComponentColumn(i -> badge(i.status())).setHeader("Status");
        grid.setEmptyStateText("Nenhum imóvel cadastrado");
        grid.setHeight("300px");

        // Clicar na linha abre o modal de edição
        grid.addItemClickListener(e -> {
            Imovel i = e.getItem();
            dialog.abrirEdicao(new ImovelDialog.DadosImovel(
                    i.endereco(), i.tipo(), i.status(), i.inquilino()));
        });

        H2 titulo = new H2("Imóveis cadastrados");
        titulo.getStyle().set("font-size", "1rem").set("margin", "0");

        VerticalLayout caixa = new VerticalLayout(titulo, grid);
        caixa.setWidthFull();
        caixa.getStyle().set("background", "white").set("border-radius", "10px");
        return caixa;
    }

    private void limparFiltros() {
        endereco.clear();
        tipo.clear();
        status.clear();
        inquilino.clear();
    }

    //filtrar de verdade quando houver banco
    private void aplicarFiltros() {
        // futuramente: buscar usando endereco, tipo, status e inquilino
    }

    // Chamado pelo botão Salvar do modal
    private void salvarImovel(ImovelDialog.DadosImovel d) {
        if (dialog.isEditando()) {
            //atualizar o imóvel existente no banco
        } else {
            //criar um novo imóvel no banco
        }
    }

    // Ponto de ligação com o backend
    public void atualizarImoveis(List<Imovel> lista) {
        grid.setItems(lista);
    }

    private Span badge(String texto) {
        String cor = switch (texto) {
            case "Alugado" -> "#00a884";
            case "Vago" -> "#f59e0b";
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