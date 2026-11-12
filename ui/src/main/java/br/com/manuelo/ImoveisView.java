package br.com.manuelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.ImovelRepositorio;

@Route(value = "imoveis", layout = MainLayout.class)
@PageTitle("Imóveis")
public class ImoveisView extends Div implements BeforeEnterObserver {

    private final ImovelRepositorio repositorio;
    private List<DemoData.Imovel> imoveis;
    private final TextField endereco = UiKit.text("Endereço", "Rua, número, bairro");
    private final ComboBox<String> tipo = UiKit.select("Tipo", "Todos",
            List.of("Apartamento", "Casa", "Comercial", "Terreno"));
    private final ComboBox<String> status = UiKit.select("Status", "Todos",
            List.of("Alugado", "Vago", "Em manutenção"));
    private final TextField inquilino = UiKit.text("Inquilino vinculado", "Buscar por nome ou documento");
    private final Div tabela = new Div();
    private final ImovelDialog dialog;

    public ImoveisView(ImovelRepositorio repositorio) {
        this.repositorio = repositorio;
        this.imoveis = repositorio.listar();
        addClassName("page");
        dialog = new ImovelDialog(this::salvarImovel);

        Button exportar = UiKit.secondary("Exportar", e -> UiKit.aviso("Exportação gerada com sucesso."));
        Button novo = UiKit.primaryIcon("Novo Imóvel", VaadinIcon.PLUS, e -> dialog.abrirNovo());
        add(UiKit.header("Imóveis", null, exportar, novo));

        add(criarFiltros());
        add(criarTabelaCard());
        renderizar();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        imoveis = repositorio.listar();
        limparFiltros();
        event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("filtro", List.of()).stream().findFirst()
                .ifPresent(filtro -> {
                    switch (filtro) {
                        case "alugado" -> status.setValue("Alugado");
                        case "vago" -> status.setValue("Vago");
                        case "manutencao" -> status.setValue("Em manutenção");
                        default -> {
                        }
                    }
                    renderizar();
                });
    }

    private Div criarFiltros() {
        tipo.setItemLabelGenerator(item -> item);
        Div acoes = new Div(
                UiKit.secondary("Cancelar", e -> {
                    limparFiltros();
                    renderizar();
                }),
                UiKit.primary("Filtrar", e -> renderizar()));
        acoes.addClassName("form-actions");

        Div grid = UiKit.formGrid(
                endereco,
                tipo,
                status,
                inquilino);
        endereco.addClassName("full");
        inquilino.addClassName("full");
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private Div criarTabelaCard() {
        tabela.addClassName("table");
        return UiKit.card(UiKit.cardHeader("Imóveis cadastrados", null), tabela);
    }

    private void limparFiltros() {
        endereco.clear();
        tipo.clear();
        status.clear();
        inquilino.clear();
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-imoveis", "Endereço", "Tipo", "Inquilino", "Status"));

        List<DemoData.Imovel> filtrados = imoveis.stream()
                .filter(this::corresponde)
                .toList();

        if (filtrados.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhum imóvel encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        for (DemoData.Imovel imovel : filtrados) {
            Div linha = UiKit.tableRow("cols-imoveis",
                    new Span(imovel.endereco()),
                    UiKit.muted(imovel.tipo()),
                    imovel.inquilino() == null || imovel.inquilino().isBlank()
                            ? UiKit.muted("-")
                            : new Span(imovel.inquilino()),
                    UiKit.badge(imovel.status()));
            linha.addClassName("clickable");
            linha.addClickListener(e -> dialog.abrirEdicao(imovel));
            tabela.add(linha);
        }
    }

    private boolean corresponde(DemoData.Imovel imovel) {
        String enderecoFiltro = endereco.getValue() == null ? "" : endereco.getValue().trim().toLowerCase(Locale.ROOT);
        String inquilinoFiltro = inquilino.getValue() == null ? "" : inquilino.getValue().trim().toLowerCase(Locale.ROOT);
        if (!enderecoFiltro.isBlank() && !imovel.endereco().toLowerCase(Locale.ROOT).contains(enderecoFiltro)) {
            return false;
        }
        if (!inquilinoFiltro.isBlank()
                && !imovel.inquilino().toLowerCase(Locale.ROOT).contains(inquilinoFiltro)) {
            return false;
        }
        if (tipo.getValue() != null && !tipo.getValue().isBlank() && !tipo.getValue().equals(imovel.tipo())) {
            return false;
        }
        if (status.getValue() != null && !status.getValue().isBlank() && !status.getValue().equals(imovel.status())) {
            return false;
        }
        return true;
    }

    private void salvarImovel(ImovelDialog.DadosImovel dados) {
        try {
            repositorio.salvar(new ImovelRepositorio.Dados(dados.id(), dados.logradouro(),
                    dados.numero(), dados.complemento(), dados.bairro(), dados.cidade(), dados.uf(),
                    dados.cep(), dados.tipo(), dados.status()));
            imoveis = repositorio.listar();
            renderizar();
            UiKit.aviso("Imóvel salvo com sucesso.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }
}
