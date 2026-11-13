package br.com.manuelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

import br.com.manuelo.dados.ContratoRepositorio;
import br.com.manuelo.dados.ReformaRepositorio;

public class ImovelDetalheDialog extends Dialog {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy",
            Locale.forLanguageTag("pt-BR"));

    private final Modelos.Imovel imovel;
    private final ContratoRepositorio contratos;
    private final ReformaRepositorio reformas;
    private final Consumer<Modelos.Imovel> aoEditar;

    public ImovelDetalheDialog(Modelos.Imovel imovel, ContratoRepositorio contratos,
            ReformaRepositorio reformas, Consumer<Modelos.Imovel> aoEditar) {
        this.imovel = imovel;
        this.contratos = contratos;
        this.reformas = reformas;
        this.aoEditar = aoEditar;

        addClassName("modal");
        setWidth("620px");
        setMaxWidth("94vw");
        setMaxHeight("92vh");
        setCloseOnOutsideClick(false);

        Div header = UiKit.dialogHeader("Detalhes do imóvel", imovel.endereco());
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        Div body = new Div(cardImovel(), cardContrato(), cardReformas());
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button editar = UiKit.primary("Editar", e -> {
            close();
            this.aoEditar.accept(this.imovel);
        });
        Button reformasButton = UiKit.secondary("Reformas", e -> {
            close();
            UiKit.ir("imoveis/reformas?imovel=" + imovel.id());
        });
        Button fechar = UiKit.secondary("Fechar", e -> close());

        add(header, body, UiKit.dialogFooter(editar, reformasButton, fechar));
    }

    private Div cardImovel() {
        TextField endereco = somenteLeitura("Endereço", imovel.endereco());
        endereco.addClassName("full");
        TextField tipo = somenteLeitura("Tipo", texto(imovel.tipo()));
        TextField status = somenteLeitura("Status", texto(imovel.status()));
        TextField aluguel = somenteLeitura("Aluguel", dinheiro(imovel.aluguel()));
        TextField cidade = somenteLeitura("Cidade", texto(imovel.cidade()));
        TextField uf = somenteLeitura("UF", texto(imovel.uf()));
        TextField cep = somenteLeitura("CEP", texto(imovel.cep()));
        return UiKit.card(UiKit.cardHeader("Imóvel", null),
                UiKit.formGrid(endereco, tipo, status, aluguel, cidade, uf, cep));
    }

    private Div cardContrato() {
        ContratoRepositorio.Detalhe contrato = imovel.id() == null ? null
                : contratos.buscarVigentePorImovel(imovel.id());
        if (contrato == null) {
            Span semContrato = new Span("Sem contrato vigente");
            semContrato.addClassName("muted");
            return UiKit.card(UiKit.cardHeader("Contrato vigente", null), semContrato);
        }

        TextField inquilino = somenteLeitura("Inquilino", texto(contrato.inquilino()));
        inquilino.addClassName("full");
        TextField periodo = somenteLeitura("Período", periodo(contrato.inicio(), contrato.fim()));
        periodo.addClassName("full");
        TextField situacao = somenteLeitura("Situação", texto(contrato.situacao()));
        TextField valor = somenteLeitura("Valor", dinheiro(contrato.aluguel()));
        TextField diaVencimento = somenteLeitura("Dia de vencimento",
                contrato.diaVencimento() > 0 ? String.valueOf(contrato.diaVencimento()) : "");
        TextField saldoCaucao = somenteLeitura("Saldo de caução", dinheiro(contrato.caucaoSaldo()));

        return UiKit.card(UiKit.cardHeader("Contrato vigente", null),
                UiKit.formGrid(inquilino, periodo, situacao, valor, diaVencimento, saldoCaucao));
    }

    private Div cardReformas() {
        Button novo = UiKit.secondary("+ Novo Registro", e -> {
            close();
            UiKit.ir("imoveis/reformas?imovel=" + imovel.id());
        });
        Div tabela = new Div();
        tabela.addClassName("table");

        List<Modelos.Reforma> historico = imovel.id() == null ? List.of()
                : reformas.listar(imovel.id());
        if (historico.isEmpty()) {
            Span vazio = new Span("Nenhuma reforma registrada");
            vazio.addClassName("muted");
            tabela.add(vazio);
        } else {
            for (Modelos.Reforma reforma : historico) {
                tabela.add(UiKit.tableRow("cols-reformas",
                        new Span(UiKit.data(reforma.data())),
                        new Span(reforma.descricao()),
                        UiKit.money(reforma.valor(), false)));
            }
        }
        return UiKit.card(UiKit.cardHeader("Histórico de reformas", null, novo), tabela);
    }

    private String periodo(LocalDate inicio, LocalDate fim) {
        String inicioTexto = inicio == null ? "" : DATA.format(inicio);
        String fimTexto = fim == null ? "sem data final" : DATA.format(fim);
        return inicioTexto + " a " + fimTexto;
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0";
        }
        return "R$ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
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
