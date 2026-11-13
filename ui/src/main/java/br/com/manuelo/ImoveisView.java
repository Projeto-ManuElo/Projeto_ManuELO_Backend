package br.com.manuelo;

import java.time.LocalDate;
import java.util.List;

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

import com.Projeto.manuelo.servico.DocumentoService;

import br.com.manuelo.dados.ContratoRepositorio;
import br.com.manuelo.dados.ImovelRepositorio;
import br.com.manuelo.dados.ReformaRepositorio;

@Route(value = "imoveis", layout = MainLayout.class)
@PageTitle("Imóveis")
public class ImoveisView extends Div implements BeforeEnterObserver {

    private static final int PAGINA = 30;

    private final ImovelRepositorio repositorio;
    private final ContratoRepositorio contratos;
    private final DocumentoService documentoService;
    private final ReformaRepositorio reformas;
    private final TextField endereco = UiKit.text("Endereço", "Rua, número, bairro");
    private final ComboBox<String> tipo = UiKit.select("Tipo", "Todos",
            List.of("Apartamento", "Casa", "Comercial", "Terreno"));
    private final ComboBox<String> status = UiKit.select("Status", "Todos",
            List.of("Alugado", "Vago", "Em manutenção"));
    private final TextField inquilino = UiKit.text("Inquilino vinculado", "Buscar por nome ou documento");
    private final Div tabela = new Div();
    private final Div paginacao = new Div();
    private final ImovelDialog dialog;
    private int limite = PAGINA;

    public ImoveisView(ImovelRepositorio repositorio, ContratoRepositorio contratos,
            DocumentoService documentoService, ReformaRepositorio reformas) {
        this.repositorio = repositorio;
        this.contratos = contratos;
        this.documentoService = documentoService;
        this.reformas = reformas;
        addClassName("page");
        dialog = new ImovelDialog(this::salvarImovel);

        Button novo = UiKit.primaryIcon("Novo Imóvel", VaadinIcon.PLUS, e -> dialog.abrirNovo());
        add(UiKit.header("Imóveis", null, novo));

        add(criarFiltros());
        add(criarTabelaCard());
        renderizar();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        limparFiltros();
        limite = PAGINA;
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
                });
        renderizar();
    }

    private Div criarFiltros() {
        tipo.setItemLabelGenerator(item -> item);
        Div acoes = new Div(
                UiKit.secondary("Cancelar", e -> {
                    limparFiltros();
                    limite = PAGINA;
                    renderizar();
                }),
                UiKit.primary("Filtrar", e -> {
                    limite = PAGINA;
                    renderizar();
                }));
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
        paginacao.addClassName("form-actions");
        return UiKit.card(UiKit.cardHeader("Imóveis cadastrados", null), tabela, paginacao);
    }

    private void limparFiltros() {
        endereco.clear();
        tipo.clear();
        status.clear();
        inquilino.clear();
    }

    private void renderizar() {
        tabela.removeAll();
        paginacao.removeAll();
        tabela.add(UiKit.tableHead("cols-imoveis", "Endereço", "Tipo", "Inquilino", "Status"));

        List<Modelos.Imovel> filtrados = repositorio.listar(
                endereco.getValue(), tipo.getValue(), status.getValue(), inquilino.getValue(),
                limite + 1);
        boolean temMais = filtrados.size() > limite;
        if (temMais) {
            filtrados = filtrados.subList(0, limite);
        }

        if (filtrados.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhum imóvel encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        for (Modelos.Imovel imovel : filtrados) {
            Div linha = UiKit.tableRow("cols-imoveis",
                    new Span(imovel.endereco()),
                    UiKit.muted(imovel.tipo()),
                    imovel.inquilino() == null || imovel.inquilino().isBlank()
                            ? UiKit.muted("-")
                            : new Span(imovel.inquilino()),
                    UiKit.badge(imovel.status()));
            linha.addClassName("clickable");
            linha.addClickListener(e -> new ImovelDetalheDialog(imovel, contratos, reformas,
                    this::editarImovel).open());
            tabela.add(linha);
        }

        if (temMais) {
            paginacao.add(UiKit.secondary("Carregar mais", e -> {
                limite += PAGINA;
                renderizar();
            }));
        } else {
            paginacao.add(UiKit.muted("Exibindo todos os imóveis encontrados."));
        }
    }

    private void editarImovel(Modelos.Imovel imovel) {
        dialog.abrirEdicao(imovel);
    }

    private void salvarImovel(ImovelDialog.DadosImovel dados) {
        String id;
        try {
            id = repositorio.salvar(new ImovelRepositorio.Dados(dados.id(), dados.logradouro(),
                    dados.numero(), dados.complemento(), dados.bairro(), dados.cidade(), dados.uf(),
                    dados.cep(), dados.tipo(), dados.status()));
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
            return;
        }
        renderizar();
        UiKit.aviso("Imóvel salvo com sucesso.");

        if (dados.contrato() != null && dados.contrato().conteudo() != null
                && dados.contrato().conteudo().length > 0) {
            String enderecoContrato = dados.logradouro() + ", " + dados.numero();
            try {
                documentoService.salvar("Contrato - " + enderecoContrato, "Contrato",
                        LocalDate.now(), "IMOVEL", id, dados.contrato().nome(),
                        dados.contrato().mime(), dados.contrato().conteudo());
                UiKit.aviso("Contrato anexado ao imóvel.");
            } catch (RuntimeException ex) {
                UiKit.erro("Imóvel salvo, mas o contrato não foi anexado: " + ex.getMessage());
            }
        }
    }
}
