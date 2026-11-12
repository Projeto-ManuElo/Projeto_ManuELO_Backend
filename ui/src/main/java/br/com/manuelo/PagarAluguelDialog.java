package br.com.manuelo;

import java.math.BigDecimal;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

import br.com.manuelo.dados.ContratoRepositorio;

public class PagarAluguelDialog extends Dialog {

    private final ContratoRepositorio.Detalhe contrato;
    private final ContratoRepositorio contratos;
    private final Runnable atualizar;
    private final TextField valor;
    private final TextField forma;

    public PagarAluguelDialog(ContratoRepositorio.Detalhe contrato, ContratoRepositorio contratos,
            Runnable atualizar) {
        this.contrato = contrato;
        this.contratos = contratos;
        this.atualizar = atualizar;

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

        valor = UiKit.text("Valor do aluguel", "R$ 0,00");
        valor.setValue(dinheiro(contrato.aluguel()));
        forma = UiKit.text("Forma de pagamento", "Ex: pix, boleto e etc...");
        forma.setValue("Pix");
        TextField caucaoInicial = UiKit.text("Caução Inicial", "R$ 0,00");
        caucaoInicial.setValue(dinheiro(contrato.caucaoDepositos()));
        caucaoInicial.setReadOnly(true);
        TextField caucaoAtual = UiKit.text("Caução atual", "R$ 0,00");
        caucaoAtual.setValue(dinheiro(contrato.caucaoSaldo()));
        caucaoAtual.setReadOnly(true);

        Div body = new Div(UiKit.formGrid(valor, forma, caucaoInicial, caucaoAtual));
        body.addClassName("modal-body");

        Button pagar = UiKit.primary("Pagar", e -> pagar(false));
        Button pagarCaucao = UiKit.secondary("Pagar com caução", e -> pagar(true));
        pagarCaucao.addClassName("btn-warning");

        Div footer = UiKit.dialogFooter(pagar, pagarCaucao);
        footer.addClassName("start");
        add(header, body, footer);
    }

    private void pagar(boolean usarCaucao) {
        BigDecimal valorNumerico;
        try {
            String texto = valor.getValue() == null ? "" : valor.getValue().trim();
            valorNumerico = new BigDecimal(texto.replace(".", "").replace(",", "."));
            if (valorNumerico.signum() <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            valor.setInvalid(true);
            UiKit.erro("Informe um valor válido.");
            return;
        }

        String titulo = usarCaucao ? "Pagar com caução?" : "Confirmar pagamento?";
        String texto = usarCaucao
                ? "O valor será descontado do saldo de caução do inquilino."
                : "Você tem certeza que todas as informações estão corretas?";

        UiKit.confirmar(titulo, texto, "Confirmar", true, () -> {
            try {
                contratos.registrarPagamento(contrato.id(), valorNumerico, forma.getValue(), usarCaucao);
                close();
                atualizar.run();
                UiKit.aviso(usarCaucao ? "Pagamento com caução registrado."
                        : "Pagamento registrado com sucesso.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0,00";
        }
        return "R$ " + valor.toPlainString().replace(".", ",");
    }
}
