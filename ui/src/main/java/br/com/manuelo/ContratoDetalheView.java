package br.com.manuelo;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.textfield.TextField;

import br.com.manuelo.dados.ContratoRepositorio;

@Route(value = "contratos/detalhe", layout = MainLayout.class)
@PageTitle("Detalhes do contrato")
public class ContratoDetalheView extends Div implements BeforeEnterObserver {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy",
            Locale.forLanguageTag("pt-BR"));

    private final ContratoRepositorio repositorio;
    private final ContratoDialog novoContrato;
    private ContratoRepositorio.Detalhe contrato;

    public ContratoDetalheView(ContratoRepositorio repositorio) {
        this.repositorio = repositorio;
        this.novoContrato = new ContratoDialog(this::criarContrato);
        addClassName("page");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String id = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("id", List.of()).stream().findFirst().orElse("");
        contrato = id.isBlank() ? null : repositorio.buscar(id);
        if (contrato == null) {
            List<DemoData.Contrato> todos = repositorio.listar();
            if (!todos.isEmpty()) {
                contrato = repositorio.buscar(todos.get(0).id());
            }
        }
        renderizar();
    }

    private void criarContrato(ContratoDialog.DadosContrato dados) {
        try {
            String id = repositorio.criar(dados);
            UiKit.aviso("Contrato criado com sucesso.");
            UI.getCurrent().navigate(ContratoDetalheView.class, QueryParameters.of("id", id));
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private void renderizar() {
        removeAll();
        if (contrato == null) {
            add(UiKit.header("Contratos", null,
                    UiKit.secondary("Novo contrato", e -> novoContrato.abrir())));
            add(UiKit.emptyState("Nenhum contrato encontrado",
                    "Cadastre um contrato para visualizar os detalhes."));
            return;
        }

        Button novo = UiKit.secondary("Novo contrato", e -> novoContrato.abrir());
        Button pagar = UiKit.primary("Pagar o aluguel",
                e -> new PagarAluguelDialog(contrato, repositorio, this::atualizar).open());
        add(UiKit.header("Contratos", null, novo, pagar));

        add(cardInquilino());
        add(cardContrato());
    }

    private void atualizar() {
        contrato = repositorio.buscar(contrato.id());
        renderizar();
    }

    private Div cardInquilino() {
        TextField nome = somenteLeitura("Nome", contrato.inquilino());
        nome.addClassName("full");
        TextField endereco = somenteLeitura("Endereço", contrato.endereco());
        TextField cpf = somenteLeitura("CPF", contrato.documento());
        TextField email = somenteLeitura("Email", contrato.email());

        String acao = contrato.acaoJudicial()
                ? (contrato.acaoJudicialDescricao() == null ? "Sim" : contrato.acaoJudicialDescricao())
                : "";
        TextField acaoJudicial = somenteLeitura("Tem ação judicial", acao);
        acaoJudicial.setPlaceholder("Se sim, qual?");

        TextField caucaoInicial = somenteLeitura("Caução Inicial",
                dinheiro(contrato.caucaoDepositos()));
        TextField caucaoAtual = somenteLeitura("Caução atual", dinheiro(contrato.caucaoSaldo()));
        TextField periodo = somenteLeitura("Vigência", periodo());
        periodo.addClassName("full");

        Div grid = UiKit.formGrid(nome, endereco, cpf, email, acaoJudicial, caucaoInicial, caucaoAtual,
                periodo);

        Div tituloFiador = new Div();
        tituloFiador.addClassName("section-title");
        tituloFiador.add(new Span("Fiador"));

        TextField fiadorNome = somenteLeitura("Nome", texto(contrato.fiadorNome()));
        fiadorNome.addClassName("full");
        TextField fiadorEmail = somenteLeitura("Email", texto(contrato.fiadorEmail()));
        fiadorEmail.addClassName("full");
        TextField fiadorNumero = somenteLeitura("Numero", texto(contrato.fiadorTelefone()));
        TextField fiadorCpf = somenteLeitura("CPF", texto(contrato.fiadorDocumento()));

        Div gridFiador = UiKit.formGrid(fiadorNome, fiadorEmail, fiadorNumero, fiadorCpf);
        gridFiador.addClassName("fiador-grid");

        return UiKit.card(UiKit.cardHeader("Informações do Inquilino", null), grid,
                tituloFiador, gridFiador);
    }

    private Div cardContrato() {
        Div card = UiKit.card();
        card.add(UiKit.cardHeader("Contrato referente", null));
        card.add(UiKit.upload("Aqui vai ficar o contrato do inquilino", "PDF, JPG ou PNG, até 10 MB"));
        return card;
    }

    private String periodo() {
        String inicio = contrato.inicio() == null ? "" : DATA.format(contrato.inicio());
        String fim = contrato.fim() == null ? "sem data final" : DATA.format(contrato.fim());
        return inicio + " a " + fim;
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0";
        }
        return "R$ " + valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private TextField somenteLeitura(String label, String valor) {
        TextField field = UiKit.text(label, "");
        field.setValue(valor == null ? "" : valor);
        field.setReadOnly(true);
        return field;
    }
}
