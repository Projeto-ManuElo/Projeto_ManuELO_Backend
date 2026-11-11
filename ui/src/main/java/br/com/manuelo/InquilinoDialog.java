package br.com.manuelo;

import java.math.BigDecimal;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;

public class InquilinoDialog extends Dialog {

    public record DadosInquilino(String id, String nome, String cpf, String email, String telefone,
            String endereco, String imovel, String status, BigDecimal caucao) {
    }

    private final Consumer<DadosInquilino> aoSalvar;
    private final Span titulo = new Span("Adicionar Inquilino");
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel Oliveira de Souza");
    private final TextField cpf = UiKit.text("CPF", "ex: 123.xxx.xxx.123");
    private final EmailField email = UiKit.email("Email", "ex: jteste@gmail.com");
    private final TextField nomeFiador = UiKit.text("Nome do fiador", "Ex: João Miguel Oliveira de Souza");
    private final TextField cpfFiador = UiKit.text("CPF do fiador", "ex: 123.xxx.xxx.123");
    private final EmailField emailFiador = UiKit.email("Email do fiador", "ex: jteste@gmail.com");
    private final TextField endereco = UiKit.text("Endereço Vinculado", "Buscar por endereço");
    private final TextField telefone = UiKit.text("Telefone", "Ex: (11) 99999-9999");
    private final TextField valorCaucao = UiKit.text("Valor do cação", "R$ 0,00");

    private String idAtual;
    private String statusAtual = "Alugado";
    private String imovelAtual = "";

    public InquilinoDialog(Consumer<DadosInquilino> aoSalvar) {
        this.aoSalvar = aoSalvar;
        addClassName("modal");
        setWidth("780px");
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
        Span recibo = new Span("Recibo do cação");
        recibo.addClassName("form-label");

        Div body = new Div(
                nome,
                UiKit.formGrid(cpf, email),
                nomeFiador,
                UiKit.formGrid(cpfFiador, emailFiador),
                endereco,
                telefone,
                contrato,
                UiKit.upload("Arraste o contrato aqui ou clique para selecionar", "PDF, JPG ou PNG, até 10 MB"),
                valorCaucao,
                recibo,
                UiKit.upload("Arraste o recibo aqui ou clique para selecionar", "PDF, JPG ou PNG, até 10 MB"));
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Salvar", e -> salvar());
        Div footer = UiKit.dialogFooter(cancelar, salvar);
        add(header, body, footer);
    }

    public void abrirNovo() {
        idAtual = null;
        statusAtual = "Alugado";
        imovelAtual = "";
        titulo.setText("Adicionar Inquilino");
        nome.clear();
        cpf.clear();
        email.clear();
        nomeFiador.clear();
        cpfFiador.clear();
        emailFiador.clear();
        endereco.clear();
        telefone.clear();
        valorCaucao.clear();
        open();
    }

    public void abrirEdicao(DemoData.Inquilino inquilino) {
        idAtual = inquilino.id();
        statusAtual = inquilino.status();
        imovelAtual = inquilino.imovel();
        titulo.setText("Editar Inquilino");
        nome.setValue(inquilino.nome());
        cpf.setValue(inquilino.cpf());
        email.setValue(inquilino.email());
        endereco.setValue(inquilino.endereco());
        telefone.setValue(inquilino.telefone());
        valorCaucao.setValue(inquilino.caucao() == null ? "" : inquilino.caucao().toPlainString());
        open();
    }

    private void salvar() {
        nome.setInvalid(nome.isEmpty());
        cpf.setInvalid(cpf.isEmpty());
        email.setInvalid(email.isEmpty());
        if (nome.isEmpty() || cpf.isEmpty() || email.isEmpty()) {
            return;
        }
        BigDecimal caucao = BigDecimal.ZERO;
        if (valorCaucao.getValue() != null && !valorCaucao.getValue().isBlank()) {
            try {
                caucao = new BigDecimal(valorCaucao.getValue().replace(".", "").replace(",", "."));
            } catch (NumberFormatException ignored) {
                caucao = BigDecimal.ZERO;
            }
        }
        aoSalvar.accept(new DadosInquilino(idAtual, nome.getValue(), cpf.getValue(), email.getValue(),
                telefone.getValue(), endereco.getValue(), imovelAtual, statusAtual, caucao));
        close();
    }
}
