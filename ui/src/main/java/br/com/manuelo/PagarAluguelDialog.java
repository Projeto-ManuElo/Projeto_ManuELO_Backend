package br.com.manuelo;

import java.math.BigDecimal;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

public class PagarAluguelDialog extends Dialog {

    public PagarAluguelDialog(DemoData.Contrato contrato) {
        addClassName("modal");
        setWidth("680px");
        setMaxWidth("94vw");
        setCloseOnOutsideClick(false);

        Span titulo = new Span("Informações do aluguel");
        titulo.addClassName("modal-title");
        Div heading = new Div(titulo);
        heading.addClassName("modal-heading");
        Div header = new Div(heading);
        header.addClassName("modal-header");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        TextField valor = UiKit.text("Valor do aluguel", "R$ 0,00");
        valor.setValue(contrato.aluguel() == null ? "" : contrato.aluguel().toPlainString());
        TextField forma = UiKit.text("Forma de pagamento", "Ex: pix, boleto e etc...");
        TextField caucaoInicial = UiKit.text("Caução Inicial", "R$ 800");
        TextField caucaoAtual = UiKit.text("Caução atual", "R$ 400");

        Div body = new Div(UiKit.formGrid(valor, forma, caucaoInicial, caucaoAtual));
        body.addClassName("modal-body");

        Button pagar = UiKit.primary("Pagar", e -> {
            close();
            UiKit.confirmar("Confirmar pagamento?", "Você tem certeza que todas as informações estão corretas?",
                    "Confirmar", true, () -> UiKit.aviso("Pagamento registrado com sucesso."));
        });
        Button pagarCaucao = UiKit.secondary("Pagar com caução", e -> {
            close();
            UiKit.confirmar("Pagar com caução?", "O valor será descontado do saldo de caução do inquilino.",
                    "Confirmar", true, () -> UiKit.aviso("Pagamento com caução registrado."));
        });
        pagarCaucao.addClassName("btn-warning");

        Div footer = UiKit.dialogFooter(pagar, pagarCaucao);
        footer.addClassName("start");
        add(header, body, footer);
    }
}
