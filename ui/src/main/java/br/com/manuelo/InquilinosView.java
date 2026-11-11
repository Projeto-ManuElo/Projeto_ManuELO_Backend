package br.com.manuelo;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "inquilinos", layout = MainLayout.class)
@PageTitle("Inquilinos")
public class InquilinosView extends Div {

    private final List<DemoData.Inquilino> inquilinos = DemoData.inquilinos();
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel");
    private final TextField cpf = UiKit.text("CPF", "Ex: xxx.yyy.zzz.xz");
    private final TextField email = UiKit.text("Email", "Ex: joao@wmail.com");
    private final Div tabela = new Div();
    private final InquilinoDialog dialog;

    public InquilinosView() {
        addClassName("page");
        dialog = new InquilinoDialog(this::salvar);

        Button novo = UiKit.primaryIcon("Novo Inquilino", VaadinIcon.PLUS, e -> dialog.abrirNovo());
        add(UiKit.header("Inquilinos", null, novo));

        add(criarFiltros());
        add(criarTabelaCard());
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
        Div grid = UiKit.formGrid(nome, cpf, email);
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private Div criarTabelaCard() {
        tabela.addClassName("table");
        return UiKit.card(UiKit.cardHeader("Inquilinos Cadastrados", null), tabela);
    }

    private void limpar() {
        nome.clear();
        cpf.clear();
        email.clear();
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-inquilinos", "Nome", "CPF", "Email"));

        List<DemoData.Inquilino> filtrados = inquilinos.stream()
                .filter(this::corresponde)
                .toList();

        if (filtrados.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhum inquilino encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        for (DemoData.Inquilino inquilino : filtrados) {
            Div linha = UiKit.tableRow("cols-inquilinos",
                    new Span(inquilino.nome()),
                    new Span(inquilino.cpf()),
                    UiKit.muted(inquilino.email()));
            linha.addClassName("clickable");
            linha.addClickListener(e -> dialog.abrirEdicao(inquilino));
            tabela.add(linha);
        }
    }

    private boolean corresponde(DemoData.Inquilino inquilino) {
        String n = nome.getValue() == null ? "" : nome.getValue().trim().toLowerCase(Locale.ROOT);
        String c = cpf.getValue() == null ? "" : cpf.getValue().trim().toLowerCase(Locale.ROOT);
        String e = email.getValue() == null ? "" : email.getValue().trim().toLowerCase(Locale.ROOT);
        return (n.isBlank() || inquilino.nome().toLowerCase(Locale.ROOT).contains(n))
                && (c.isBlank() || inquilino.cpf().toLowerCase(Locale.ROOT).contains(c))
                && (e.isBlank() || inquilino.email().toLowerCase(Locale.ROOT).contains(e));
    }

    private void salvar(InquilinoDialog.DadosInquilino dados) {
        DemoData.Inquilino novo = new DemoData.Inquilino(
                dados.id() == null ? String.valueOf(System.currentTimeMillis()) : dados.id(),
                dados.nome(), dados.cpf(), dados.email(), dados.telefone(),
                dados.endereco(), dados.imovel(), dados.status(), dados.caucao());
        boolean atualizado = false;
        for (int i = 0; i < inquilinos.size(); i++) {
            if (inquilinos.get(i).id().equals(novo.id())) {
                inquilinos.set(i, novo);
                atualizado = true;
                break;
            }
        }
        if (!atualizado) {
            inquilinos.add(novo);
        }
        renderizar();
        UiKit.aviso("Inquilino salvo com sucesso.");
    }
}
