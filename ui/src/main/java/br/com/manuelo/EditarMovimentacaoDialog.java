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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;

import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService;
import com.Projeto.manuelo.servico.MovimentacaoFinanceiraService.ExtratoItem;

import br.com.manuelo.dados.Dominio;

public class EditarMovimentacaoDialog extends Dialog {

    private static final List<String> FORMAS = List.of("Pix", "Boleto", "Dinheiro", "Transferência");

    private final MovimentacaoFinanceiraService servico;
    private final ExtratoItem movimentacao;
    private final Runnable atualizar;
    private final TextField valor;
    private final DatePicker data = UiKit.date("Data");
    private final TextField descricao;
    private final ComboBox<String> forma = UiKit.select("Forma de pagamento",
            "Selecione a forma de pagamento", FORMAS);
    private final TextField favorecido = UiKit.text("Favorecido", "Ex.: fornecedor ou prestador");
    private final TextArea justificativa = UiKit.textArea("Justificativa",
            "Descreva o motivo da correção (obrigatório)");
    private final Upload upload;
    private UiKit.ArquivoSelecionado comprovante;

    public EditarMovimentacaoDialog(MovimentacaoFinanceiraService servico, ExtratoItem movimentacao,
            Runnable atualizar) {
        this.servico = servico;
        this.movimentacao = movimentacao;
        this.atualizar = atualizar;

        addClassName("modal");
        setWidth("680px");
        setMaxWidth("94vw");
        setCloseOnOutsideClick(false);

        Div header = UiKit.dialogHeader("Editar movimentação",
                "A correção registra um estorno e uma nova movimentação.");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        valor = UiKit.text("Valor", "R$ 0,00");
        valor.setValue(dinheiro(movimentacao.valorAssinado()));
        data.setValue(movimentacao.data());
        descricao = UiKit.text("Descrição", "Ex.: reparo hidráulico - Ed. Aurora");
        descricao.setValue(movimentacao.descricao() == null ? "" : movimentacao.descricao());
        if (movimentacao.forma() != null && !movimentacao.forma().isBlank()) {
            forma.setValue(Dominio.formaPagamento(movimentacao.forma()));
        }
        favorecido.addClassName("full");
        justificativa.addClassName("full");

        upload = UiKit.upload("Arraste um novo comprovante aqui ou clique para selecionar",
                "Opcional para entradas; obrigatório para corrigir uma saída",
                arquivo -> comprovante = arquivo);
        Div comprovanteBox = new Div(new Span("Comprovante"), upload);
        comprovanteBox.addClassName("full");
        comprovanteBox.addClassName("stack");

        Div body = new Div(UiKit.formGrid(valor, data, descricao, forma, favorecido, comprovanteBox,
                justificativa));
        body.addClassName("modal-body");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Salvar correção", e -> salvar());

        add(header, body, UiKit.dialogFooter(cancelar, salvar));
    }

    private void salvar() {
        try {
            BigDecimal valorNumerico = valor();
            if (descricao.isEmpty()) {
                descricao.setInvalid(true);
                throw new IllegalArgumentException("Informe a descrição da movimentação.");
            }
            if (forma.getValue() == null || forma.getValue().isBlank()) {
                forma.setInvalid(true);
                throw new IllegalArgumentException("Selecione a forma de pagamento.");
            }
            String textoJustificativa = justificativa.getValue();
            if (textoJustificativa == null || textoJustificativa.isBlank()) {
                justificativa.setInvalid(true);
                throw new IllegalArgumentException("Informe a justificativa da correção.");
            }
            UiKit.ArquivoSelecionado arquivo = comprovante;
            if ("SAIDA".equals(movimentacao.tipo())
                    && (arquivo == null || arquivo.conteudo() == null
                            || arquivo.conteudo().length == 0)) {
                throw new IllegalArgumentException(
                        "Anexe o comprovante para corrigir uma saída (PDF, JPG ou PNG).");
            }

            servico.corrigir(movimentacao.id(), null, null, valorNumerico, data.getValue(),
                    descricao.getValue(), forma.getValue(), favorecido.getValue(),
                    arquivo == null ? null : arquivo.nome(),
                    arquivo == null ? null : arquivo.mime(),
                    arquivo == null ? null : arquivo.conteudo(),
                    textoJustificativa);
            close();
            atualizar.run();
            UiKit.aviso("Movimentação corrigida com sucesso.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private BigDecimal valor() {
        try {
            String texto = valor.getValue() == null ? "" : valor.getValue().trim()
                    .replace("R$", "").replace(" ", "");
            BigDecimal numero = new BigDecimal(texto.replace(".", "").replace(",", "."));
            if (numero.signum() <= 0) {
                throw new NumberFormatException();
            }
            return numero;
        } catch (NumberFormatException ex) {
            valor.setInvalid(true);
            throw new IllegalArgumentException("Informe um valor válido.");
        }
    }

    private String dinheiro(BigDecimal valorMovimentacao) {
        if (valorMovimentacao == null) {
            return "R$ 0,00";
        }
        return "R$ " + valorMovimentacao.abs().setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString().replace(".", ",");
    }
}
