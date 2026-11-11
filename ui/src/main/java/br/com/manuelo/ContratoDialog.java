package br.com.manuelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;

public class ContratoDialog extends Dialog {

    public record DadosContrato(String nome, String endereco, String cpf, String email,
            LocalDate vencimento, BigDecimal aluguel) {
    }

    private final Consumer<DadosContrato> aoSalvar;
    private final Span titulo = new Span("Novo contrato");
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel Oliveira dos Santos");
    private final TextField endereco = UiKit.text("Endereço", "Ex: Ap 302. Ed. Aurora");
    private final TextField cpf = UiKit.text("CPF", "Ex: 074.xxx.39-xy");
    private final EmailField email = UiKit.email("Email", "Ex: joao@wmail.com");
    private final TextField valor = UiKit.text("Valor do aluguel", "R$ 0,00");
    private final DatePicker vencimento = UiKit.date("Vencimento do contrato");

    public ContratoDialog(Consumer<DadosContrato> aoSalvar) {
        this.aoSalvar = aoSalvar;
        addClassName("modal");
        setWidth("680px");
        setMaxWidth("94vw");
        setMaxHeight("92vh");
        setCloseOnOutsideClick(false);

        titulo.addClassName("modal-title");
        Div heading = new Div(titulo);
        heading.addClassName("modal-heading");
        Div header = new Div(heading);
        header.addClassName("modal-header");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        Span contrato = new Span("Contrato");
        contrato.addClassName("form-label");

        Div body = new Div(
                nome,
                UiKit.formGrid(endereco, cpf),
                email,
                UiKit.formGrid(valor, vencimento),
                contrato,
                UiKit.upload("Arraste o contrato aqui ou clique para selecionar", "PDF, JPG ou PNG, até 10 MB"));
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Criar contrato", e -> salvar());
        add(header, body, UiKit.dialogFooter(cancelar, salvar));
    }

    public void abrir() {
        titulo.setText("Novo contrato");
        nome.clear();
        endereco.clear();
        cpf.clear();
        email.clear();
        valor.clear();
        vencimento.clear();
        open();
    }

    private void salvar() {
        nome.setInvalid(nome.isEmpty());
        cpf.setInvalid(cpf.isEmpty());
        if (nome.isEmpty() || cpf.isEmpty()) {
            return;
        }
        BigDecimal aluguel = BigDecimal.ZERO;
        if (valor.getValue() != null && !valor.getValue().isBlank()) {
            try {
                aluguel = new BigDecimal(valor.getValue().replace(".", "").replace(",", "."));
            } catch (NumberFormatException ignored) {
                aluguel = BigDecimal.ZERO;
            }
        }
        aoSalvar.accept(new DadosContrato(nome.getValue(), endereco.getValue(), cpf.getValue(),
                email.getValue(), vencimento.getValue(), aluguel));
        close();
    }
}
