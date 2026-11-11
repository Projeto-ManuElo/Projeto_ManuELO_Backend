package br.com.manuelo;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

public class DocumentoDialog extends Dialog {

    public record DadosDocumento(String nome, String tipo, LocalDate data) {
    }

    private final Consumer<DadosDocumento> aoSalvar;
    private final Span titulo = new Span("Adicionar documento");
    private final TextField nome = UiKit.text("Nome do documento", "Ex: Contrato - Ana Ferreira");
    private final ComboBox<String> tipo = UiKit.select("Tipo de documento", "Selecione",
            List.of("Contrato", "Comprovante", "Laudo", "Cadastro"));
    private final ComboBox<String> tipoFinanceiro = UiKit.select("Tipo financeiro", "Selecione",
            List.of("Entrada", "Saída"));
    private final DatePicker data = UiKit.date("Data do documento");

    public DocumentoDialog(Consumer<DadosDocumento> aoSalvar) {
        this.aoSalvar = aoSalvar;
        addClassName("modal");
        setWidth("620px");
        setMaxWidth("94vw");
        setCloseOnOutsideClick(false);

        titulo.addClassName("modal-title");
        Div heading = new Div(titulo);
        heading.addClassName("modal-heading");
        Div header = new Div(heading);
        header.addClassName("modal-header");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        Div body = new Div(
                nome,
                UiKit.formGrid(tipo, tipoFinanceiro),
                data,
                UiKit.upload("Arraste o arquivo aqui ou clique para selecionar", "PDF, JPG ou PNG, até 10 MB"));
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Salvar", e -> salvar());
        add(header, body, UiKit.dialogFooter(cancelar, salvar));
    }

    public void abrir() {
        nome.clear();
        tipo.clear();
        tipoFinanceiro.clear();
        data.clear();
        open();
    }

    private void salvar() {
        nome.setInvalid(nome.isEmpty());
        tipo.setInvalid(tipo.isEmpty());
        if (nome.isEmpty() || tipo.isEmpty()) {
            return;
        }
        aoSalvar.accept(new DadosDocumento(nome.getValue(), tipo.getValue(), data.getValue()));
        close();
    }
}
