package br.com.manuelo;

import java.math.BigDecimal;
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
import com.vaadin.flow.component.upload.Upload;

public class ReformaDialog extends Dialog {

    public record DadosReforma(LocalDate data, String descricao, String proprietario,
            BigDecimal valor, String formaPagamento, UiKit.ArquivoSelecionado comprovante) {
    }

    private final Consumer<DadosReforma> aoSalvar;
    private final DatePicker data = UiKit.date("Data");
    private final TextField descricao = UiKit.text("Descrição", "Ex: reparo hidráulico");
    private final TextField proprietario = UiKit.text("Proprietário do período", "Nome do proprietário");
    private final TextField valor = UiKit.text("Valor", "R$ 0,00");
    private final ComboBox<String> forma = UiKit.select("Forma de pagamento", "Selecione",
            List.of("PIX", "Boleto", "Transferência", "Dinheiro"));
    private final Upload comprovanteUpload;
    private UiKit.ArquivoSelecionado comprovante;

    public ReformaDialog(Consumer<DadosReforma> aoSalvar) {
        this.aoSalvar = aoSalvar;
        comprovanteUpload = UiKit.upload("Arraste o comprovante aqui ou clique para selecionar",
                "PDF, JPG ou PNG, até 10 MB", this::selecionarComprovante);
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

        Div body = new Div(UiKit.formGrid(data, valor, forma, descricao, proprietario),
                comprovanteUpload);
        body.addClassName("modal-body");
        body.addClassName("stack");
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
        valor.setInvalid(false);
        forma.clear();
        comprovante = null;
        comprovanteUpload.clearFileList();
        open();
    }

    private void selecionarComprovante(UiKit.ArquivoSelecionado selecionado) {
        this.comprovante = selecionado;
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
        if (valorNumerico.signum() <= 0) {
            valor.setInvalid(true);
            UiKit.erro("Informe um valor maior que zero.");
            return;
        }
        valor.setInvalid(false);
        if (forma.getValue() == null || forma.getValue().isBlank()) {
            forma.setInvalid(true);
            UiKit.erro("Selecione a forma de pagamento.");
            return;
        }
        forma.setInvalid(false);
        if (comprovante == null || comprovante.conteudo() == null
                || comprovante.conteudo().length == 0) {
            UiKit.erro("Anexe o comprovante da reforma (PDF, JPG ou PNG).");
            return;
        }
        aoSalvar.accept(new DadosReforma(
                data.getValue() == null ? LocalDate.now() : data.getValue(),
                descricao.getValue(), proprietario.getValue() == null ? "" : proprietario.getValue(),
                valorNumerico, forma.getValue(), comprovante));
        close();
    }
}
