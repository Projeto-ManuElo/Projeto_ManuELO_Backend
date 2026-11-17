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
import com.vaadin.flow.component.textfield.TextField;

public class ReformaDialog extends Dialog {

    private final Consumer<DemoData.Reforma> aoSalvar;
    private final DatePicker data = UiKit.date("Data");
    private final TextField descricao = UiKit.text("Descrição", "Ex: reparo hidráulico");
    private final TextField proprietario = UiKit.text("Proprietário do período", "Nome do proprietário");
    private final TextField valor = UiKit.text("Valor", "R$ 0,00");

    public ReformaDialog(Consumer<DemoData.Reforma> aoSalvar) {
        this.aoSalvar = aoSalvar;
        addClassName("modal");
        setWidth("560px");
        setMaxWidth("94vw");
        setCloseOnOutsideClick(false);

        Span titulo = new Span("Novo registro de reforma");
        titulo.addClassName("modal-title");
        Div heading = new Div(titulo);
        heading.addClassName("modal-heading");
        Div header = new Div(heading);
        header.addClassName("modal-header");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        Div body = new Div(UiKit.formGrid(data, valor, descricao, proprietario));
        body.addClassName("modal-body");
        proprietario.setReadOnly(true);
        proprietario.setPlaceholder("Definido pelo contrato vigente");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Salvar", e -> salvar());
        add(header, body, UiKit.dialogFooter(cancelar, salvar));
    }

    public void abrir(String proprietarioAtual) {
        data.clear();
        descricao.clear();
        proprietario.setValue(proprietarioAtual == null ? "" : proprietarioAtual);
        valor.clear();
        open();
    }

    private void salvar() {
        descricao.setInvalid(descricao.isEmpty());
        if (descricao.isEmpty()) {
            return;
        }
        BigDecimal valorNumerico = BigDecimal.ZERO;
        try {
            if (valor.getValue() != null && !valor.getValue().isBlank()) {
                valorNumerico = new BigDecimal(valor.getValue().replace(".", "").replace(",", "."));
            }
        } catch (NumberFormatException ignored) {
            valorNumerico = BigDecimal.ZERO;
        }
        aoSalvar.accept(new DemoData.Reforma(data.getValue() == null ? LocalDate.now() : data.getValue(),
                descricao.getValue(), proprietario.getValue() == null ? "" : proprietario.getValue(),
                valorNumerico));
        close();
    }
}
