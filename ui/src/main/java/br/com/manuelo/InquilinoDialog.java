package br.com.manuelo;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;

import br.com.manuelo.dados.ImovelRepositorio;
import br.com.manuelo.dados.InquilinoRepositorio;

public class InquilinoDialog extends Dialog {

    public record DadosInquilino(String id, String nome, String cpf, String email, String telefone,
            String imovelId, BigDecimal aluguel, int diaVencimento, String nomeFiador,
            String documentoFiador, String emailFiador, BigDecimal caucao,
            UiKit.ArquivoSelecionado contratoArquivo, UiKit.ArquivoSelecionado reciboArquivo) {
    }

    private final Consumer<DadosInquilino> aoSalvar;
    private final List<ImovelRepositorio.Opcao> imoveis;
    private final Span titulo = new Span("Adicionar Inquilino");
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel Oliveira de Souza");
    private final TextField cpf = UiKit.text("CPF", "ex: 123.xxx.xxx.123");
    private final EmailField email = UiKit.email("Email", "ex: jteste@gmail.com");
    private final TextField nomeFiador = UiKit.text("Nome do fiador", "Ex: João Miguel Oliveira de Souza");
    private final TextField cpfFiador = UiKit.text("CPF do fiador", "ex: 123.xxx.xxx.123");
    private final EmailField emailFiador = UiKit.email("Email do fiador", "ex: jteste@gmail.com");
    private final ComboBox<ImovelRepositorio.Opcao> imovel;
    private final TextField telefone = UiKit.text("Telefone", "Ex: (11) 99999-9999");
    private final TextField valorAluguel = UiKit.text("Valor do aluguel", "R$ 0,00");
    private final TextField diaVencimento = UiKit.text("Dia de vencimento", "Ex: 5");
    private final TextField valorCaucao = UiKit.text("Valor da caução", "R$ 0,00");
    private final Upload contratoUpload;
    private final Upload reciboUpload;
    private UiKit.ArquivoSelecionado contratoArquivo;
    private UiKit.ArquivoSelecionado reciboArquivo;

    private String idAtual;

    public InquilinoDialog(List<ImovelRepositorio.Opcao> imoveis, Consumer<DadosInquilino> aoSalvar) {
        this.aoSalvar = aoSalvar;
        this.imoveis = imoveis == null ? List.of() : List.copyOf(imoveis);
        imovel = new ComboBox<>("Imóvel vinculado");
        imovel.setItems(this.imoveis);
        imovel.setItemLabelGenerator(ImovelRepositorio.Opcao::rotulo);
        imovel.setPlaceholder("Selecione o imóvel");
        imovel.setWidthFull();
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
        Span recibo = new Span("Recibo da caução");
        recibo.addClassName("form-label");

        contratoUpload = UiKit.upload("Arraste o contrato aqui ou clique para selecionar",
                "PDF, JPG ou PNG, até 10 MB", arquivo -> contratoArquivo = arquivo);
        reciboUpload = UiKit.upload("Arraste o recibo aqui ou clique para selecionar",
                "PDF, JPG ou PNG, até 10 MB", arquivo -> reciboArquivo = arquivo);

        Div body = new Div(
                nome,
                UiKit.formGrid(cpf, email),
                nomeFiador,
                UiKit.formGrid(cpfFiador, emailFiador),
                imovel,
                telefone,
                contrato,
                UiKit.formGrid(valorAluguel, diaVencimento),
                contratoUpload,
                valorCaucao,
                recibo,
                reciboUpload);
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Salvar", e -> salvar());
        Div footer = UiKit.dialogFooter(cancelar, salvar);
        add(header, body, footer);
    }

    public void abrirNovo() {
        idAtual = null;
        titulo.setText("Adicionar Inquilino");
        nome.clear();
        cpf.clear();
        email.clear();
        nomeFiador.clear();
        cpfFiador.clear();
        emailFiador.clear();
        imovel.clear();
        telefone.clear();
        valorAluguel.clear();
        diaVencimento.setValue("5");
        valorCaucao.clear();
        contratoArquivo = null;
        reciboArquivo = null;
        contratoUpload.clearFileList();
        reciboUpload.clearFileList();
        open();
    }

    public void abrirEdicao(Modelos.Inquilino inquilino, InquilinoRepositorio.DadosEdicao detalhe) {
        idAtual = inquilino.id();
        titulo.setText("Editar Inquilino");
        nome.setValue(inquilino.nome());
        cpf.setValue(inquilino.cpf());
        email.setValue(inquilino.email());
        telefone.setValue(inquilino.telefone());
        nomeFiador.clear();
        cpfFiador.clear();
        emailFiador.clear();
        imovel.clear();
        valorAluguel.clear();
        diaVencimento.clear();
        valorCaucao.clear();
        if (detalhe != null) {
            nomeFiador.setValue(detalhe.nomeFiador() == null ? "" : detalhe.nomeFiador());
            cpfFiador.setValue(detalhe.documentoFiador() == null ? "" : detalhe.documentoFiador());
            emailFiador.setValue(detalhe.emailFiador() == null ? "" : detalhe.emailFiador());
            if (detalhe.imovelId() != null) {
                imoveis.stream()
                        .filter(opcao -> detalhe.imovelId().equals(opcao.id()))
                        .findFirst()
                        .ifPresent(imovel::setValue);
            }
            if (detalhe.aluguel() != null) {
                valorAluguel.setValue(detalhe.aluguel().toPlainString().replace(".", ","));
            }
            if (detalhe.diaVencimento() > 0) {
                diaVencimento.setValue(String.valueOf(detalhe.diaVencimento()));
            }
            if (detalhe.caucao() != null) {
                valorCaucao.setValue(detalhe.caucao().toPlainString().replace(".", ","));
            }
        }
        contratoArquivo = null;
        reciboArquivo = null;
        contratoUpload.clearFileList();
        reciboUpload.clearFileList();
        open();
    }

    private void salvar() {
        boolean novo = idAtual == null || idAtual.isBlank();
        BigDecimal aluguel = moeda(valorAluguel.getValue());
        Integer dia = dia(diaVencimento.getValue());
        boolean temContrato = imovel.getValue() != null || aluguel != null;

        nome.setInvalid(nome.isEmpty());
        cpf.setInvalid(cpf.isEmpty());
        email.setInvalid(email.isEmpty());
        boolean imovelInvalido = (novo || temContrato) && imovel.getValue() == null;
        boolean aluguelInvalido = (novo || temContrato)
                && (aluguel == null || aluguel.signum() <= 0);
        boolean diaInvalido = (novo || temContrato) && (dia == null || dia < 1 || dia > 31);
        imovel.setInvalid(imovelInvalido);
        valorAluguel.setInvalid(aluguelInvalido);
        diaVencimento.setInvalid(diaInvalido);

        if (nome.isEmpty() || cpf.isEmpty() || email.isEmpty()
                || imovelInvalido || aluguelInvalido || diaInvalido) {
            return;
        }

        BigDecimal caucao = moeda(valorCaucao.getValue());
        if (caucao == null) {
            caucao = BigDecimal.ZERO;
        }
        String imovelId = imovel.getValue() == null ? null : imovel.getValue().id();
        aoSalvar.accept(new DadosInquilino(idAtual, nome.getValue(), cpf.getValue(), email.getValue(),
                telefone.getValue(), imovelId, aluguel, dia == null ? 5 : dia,
                nomeFiador.getValue(), cpfFiador.getValue(), emailFiador.getValue(), caucao,
                contratoArquivo, reciboArquivo));
        close();
    }

    private Integer dia(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(texto.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal moeda(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(texto.replace("R$", "").replace(" ", "")
                    .replace(".", "").replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
