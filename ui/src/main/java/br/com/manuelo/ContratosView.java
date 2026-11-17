package br.com.manuelo;

import java.time.LocalDate;
import java.util.List;

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
import br.com.manuelo.dados.ImovelRepositorio;

import com.Projeto.manuelo.servico.ContratoCadastroService;
import com.Projeto.manuelo.servico.DocumentoService;

@Route(value = "contratos", layout = MainLayout.class)
@PageTitle("Contratos")
public class ContratosView extends Div implements BeforeEnterObserver {

    private final ContratoRepositorio repositorio;
    private final ContratoCadastroService cadastro;
    private final DocumentoService documentoService;
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel");
    private final TextField cpf = UiKit.text("CPF", "Ex: xxx.yyy.zzz.xz");
    private final TextField imovel = UiKit.text("Imóvel", "Ex: Aurora");
    private final TextField validade = UiKit.text("Validade do contrato", "Ex: xx/xx/xx26");
    private final ComboBox<String> situacao = UiKit.select("Situação", "Ex: Atrasado ou não",
            List.of("Ativo", "A vencer", "Atrasado"));
    private final Div tabela = new Div();
    private final ContratoDialog dialog;

    public ContratosView(ContratoRepositorio repositorio, ContratoCadastroService cadastro,
            ImovelRepositorio imoveis, DocumentoService documentoService) {
        this.repositorio = repositorio;
        this.cadastro = cadastro;
        this.documentoService = documentoService;
        addClassName("page");
        dialog = new ContratoDialog(imoveis.opcoes(), this::adicionarContrato);

        Button novo = UiKit.primaryIcon("Novo Contrato", VaadinIcon.PLUS, e -> dialog.abrir());
        add(UiKit.header("Contratos", null, novo));

        add(criarFiltros());
        add(criarTabelaCard());
        renderizar();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
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
                });
        renderizar();
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
        Div grid = UiKit.formGrid(nome, cpf, imovel, validade, situacao);
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private Div criarTabelaCard() {
        tabela.addClassName("table");
        return UiKit.card(UiKit.cardHeader("Contratos recentes", null), tabela);
    }

    private void limpar() {
        nome.clear();
        cpf.clear();
        imovel.clear();
        validade.clear();
        situacao.clear();
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-contratos", "Nome", "Validade", "Endereço", "CPF", "Email"));

        List<Modelos.Contrato> contratos = repositorio.listar(valor(nome), valor(cpf),
                valor(imovel), valor(validade), situacao.getValue());

        if (contratos.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhum contrato encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        for (Modelos.Contrato contrato : contratos) {
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
        if (dias > 0) {
            return dias == 1 ? "Falta 1 dia" : "Faltam " + dias + " dias";
        }
        int atraso = Math.abs(dias);
        return atraso == 1 ? "Atrasado há 1 dia" : "Atrasado há " + atraso + " dias";
    }

    private String valor(TextField field) {
        return field.getValue() == null ? "" : field.getValue().trim();
    }

    private void adicionarContrato(ContratoDialog.DadosContrato dados) {
        try {
            String id = cadastro.criar(dados.paraServico());
            UiKit.ArquivoSelecionado arquivo = dados.arquivo();
            if (arquivo != null && arquivo.conteudo() != null && arquivo.conteudo().length > 0) {
                documentoService.salvar("Contrato - " + dados.nome(), "Contrato",
                        LocalDate.now(), "CONTRATO", id, arquivo.nome(), arquivo.mime(),
                        arquivo.conteudo());
            }
            renderizar();
            UiKit.aviso("Contrato criado com sucesso.");
            UI.getCurrent().navigate(ContratoDetalheView.class, QueryParameters.of("id", id));
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }
}
