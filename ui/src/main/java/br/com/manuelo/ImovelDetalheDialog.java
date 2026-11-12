package br.com.manuelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

import br.com.manuelo.dados.ContratoRepositorio;

public class ImovelDetalheDialog extends Dialog {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy",
            Locale.forLanguageTag("pt-BR"));

    private final DemoData.Imovel imovel;
    private final ContratoRepositorio contratos;
    private final Consumer<DemoData.Imovel> aoEditar;

    public ImovelDetalheDialog(DemoData.Imovel imovel, ContratoRepositorio contratos,
            Consumer<DemoData.Imovel> aoEditar) {
        this.imovel = imovel;
        this.contratos = contratos;
        this.aoEditar = aoEditar;

        addClassName("modal");
        setWidth("620px");
        setMaxWidth("94vw");
        setMaxHeight("92vh");
        setCloseOnOutsideClick(false);

        Div header = UiKit.dialogHeader("Detalhes do imóvel", imovel.endereco());
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        Div body = new Div(cardImovel(), cardContrato());
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button editar = UiKit.primary("Editar", e -> {
            close();
            this.aoEditar.accept(this.imovel);
        });
        Button reformas = UiKit.secondary("Reformas", e -> {
            close();
            UiKit.ir("imoveis/reformas");
        });
        Button fechar = UiKit.secondary("Fechar", e -> close());

        add(header, body, UiKit.dialogFooter(editar, reformas, fechar));
    }

    private Div cardImovel() {
        TextField endereco = somenteLeitura("Endereço", imovel.endereco());
        endereco.addClassName("full");
        TextField tipo = somenteLeitura("Tipo", texto(imovel.tipo()));
        TextField status = somenteLeitura("Status", texto(imovel.status()));
        TextField aluguel = somenteLeitura("Aluguel", dinheiro(imovel.aluguel()));
        return UiKit.card(UiKit.cardHeader("Imóvel", null),
                UiKit.formGrid(endereco, tipo, status, aluguel));
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
        TextField saldoCaucao = somenteLeitura("Saldo de caução", dinheiro(contrato.caucaoSaldo()));

        return UiKit.card(UiKit.cardHeader("Contrato vigente", null),
                UiKit.formGrid(inquilino, periodo, situacao, valor, saldoCaucao));
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
