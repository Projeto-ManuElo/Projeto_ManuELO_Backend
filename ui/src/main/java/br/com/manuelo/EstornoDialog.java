package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextArea;

import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.ExtratoItem;

public class EstornoDialog extends Dialog {

    private final MovimentacaoFinanceiraService servico;
    private final ExtratoItem movimentacao;
    private final Runnable atualizar;
    private final TextArea justificativa = UiKit.textArea("Justificativa",
            "Descreva o motivo do estorno (obrigatório)");

    public EstornoDialog(MovimentacaoFinanceiraService servico, ExtratoItem movimentacao,
            Runnable atualizar) {
        this.servico = servico;
        this.movimentacao = movimentacao;
        this.atualizar = atualizar;

        addClassName("modal");
        setWidth("560px");
        setMaxWidth("94vw");
        setCloseOnOutsideClick(false);

        Div header = UiKit.dialogHeader("Estornar movimentação",
                "O lançamento original será mantido e um estorno será registrado.");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        justificativa.addClassName("full");
        Div body = new Div(UiKit.formGrid(justificativa));
        body.addClassName("modal-body");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button confirmar = UiKit.primary("Estornar", e -> estornar());

        add(header, body, UiKit.dialogFooter(cancelar, confirmar));
    }

    private void estornar() {
        try {
            String texto = justificativa.getValue();
            if (texto == null || texto.isBlank()) {
                justificativa.setInvalid(true);
                throw new IllegalArgumentException("Informe a justificativa do estorno.");
            }
            servico.estornar(movimentacao.id(), texto);
            close();
            atualizar.run();
            UiKit.aviso("Movimentação estornada com sucesso.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }
}
