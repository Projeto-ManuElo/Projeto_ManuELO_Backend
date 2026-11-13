package br.com.manuelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

import br.com.manuelo.dados.ContratoRepositorio;

import com.Projeto.manuelo.servico.PagamentoAluguelService;

public class PagarAluguelDialog extends Dialog {

    private final ContratoRepositorio.Detalhe contrato;
    private final PagamentoAluguelService pagamentos;
    private final Runnable atualizar;
    private final TextField valor;
    private final ComboBox<String> forma;
    private final DatePicker competencia;

    public PagarAluguelDialog(ContratoRepositorio.Detalhe contrato, PagamentoAluguelService pagamentos,
            Runnable atualizar) {
        this.contrato = contrato;
        this.pagamentos = pagamentos;
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
        forma = UiKit.select("Forma de pagamento", "Selecione a forma de pagamento",
                List.of("Pix", "Boleto", "Dinheiro", "Transferência"));
        forma.setValue("Pix");
        competencia = UiKit.date("Competência");
        competencia.setValue(LocalDate.now().withDayOfMonth(1));
        competencia.setHelperText("Será normalizada para o primeiro dia do mês.");
        TextField caucaoInicial = UiKit.text("Caução Inicial", "R$ 0,00");
        caucaoInicial.setValue(dinheiro(contrato.caucaoDepositos()));
        caucaoInicial.setReadOnly(true);
        TextField caucaoAtual = UiKit.text("Caução atual", "R$ 0,00");
        caucaoAtual.setValue(dinheiro(contrato.caucaoSaldo()));
        caucaoAtual.setReadOnly(true);

        Div body = new Div(UiKit.formGrid(valor, forma, competencia, caucaoInicial, caucaoAtual));
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
            String texto = valor.getValue() == null ? "" : valor.getValue().trim()
                    .replace("R$", "").replace(" ", "");
            valorNumerico = new BigDecimal(texto.replace(".", "").replace(",", "."));
            if (valorNumerico.signum() <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            valor.setInvalid(true);
            UiKit.erro("Informe um valor válido.");
            return;
        }

        LocalDate competenciaValor = competencia.getValue();
        if (competenciaValor == null || competenciaValor.withDayOfMonth(1)
                .isAfter(LocalDate.now().withDayOfMonth(1))) {
            competencia.setInvalid(true);
            UiKit.erro("Informe uma competência válida e não futura.");
            return;
        }

        String titulo = usarCaucao ? "Pagar com caução?" : "Confirmar pagamento?";
        String texto = usarCaucao
                ? "O valor será descontado do saldo de caução do inquilino."
                : "Você tem certeza que todas as informações estão corretas?";

        UiKit.confirmar(titulo, texto, "Confirmar", true, () -> {
            try {
                pagamentos.registrar(contrato.id(), competenciaValor, valorNumerico,
                        forma.getValue(), usarCaucao);
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
