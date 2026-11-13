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
import com.vaadin.flow.component.upload.Upload;

import br.com.manuelo.dados.DocumentoRepositorio;

public class DocumentoDialog extends Dialog {

    public record DadosDocumento(String nome, String tipo, LocalDate data,
            DocumentoRepositorio.Opcao vinculo, String nomeArquivo, String mime, byte[] conteudo) {
    }

    private final Consumer<DadosDocumento> aoSalvar;
    private final Span titulo = new Span("Adicionar documento");
    private final TextField nome = UiKit.text("Nome do documento", "Ex: Contrato - Ana Ferreira");
    private final ComboBox<String> tipo = UiKit.select("Tipo de documento", "Selecione",
            List.of("Contrato", "Comprovante", "Laudo", "Cadastro", "Nota fiscal"));
    private final DatePicker data = UiKit.date("Data do documento");
    private final ComboBox<DocumentoRepositorio.Opcao> vinculo;
    private final Upload arquivoUpload;
    private UiKit.ArquivoSelecionado arquivo;

    public DocumentoDialog(List<DocumentoRepositorio.Opcao> vinculos, Consumer<DadosDocumento> aoSalvar) {
        this.aoSalvar = aoSalvar;
        vinculo = new ComboBox<>("Vincular a");
        vinculo.setItems(vinculos);
        vinculo.setItemLabelGenerator(DocumentoRepositorio.Opcao::rotulo);
        vinculo.setPlaceholder("Imóvel, inquilino, contrato ou movimentação");
        vinculo.setWidthFull();
        vinculo.setAllowCustomValue(false);
        arquivoUpload = UiKit.upload("Arraste o arquivo aqui ou clique para selecionar",
                "PDF, JPG ou PNG, até 10 MB", this::selecionarArquivo);
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
                UiKit.formGrid(tipo, data),
                vinculo,
                arquivoUpload);
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Salvar", e -> salvar());
        add(header, body, UiKit.dialogFooter(cancelar, salvar));
    }

    public void abrir() {
        nome.clear();
        tipo.clear();
        data.clear();
        vinculo.clear();
        arquivo = null;
        arquivoUpload.clearFileList();
        open();
    }

    private void selecionarArquivo(UiKit.ArquivoSelecionado selecionado) {
        this.arquivo = selecionado;
    }

    private void salvar() {
        nome.setInvalid(nome.isEmpty());
        vinculo.setInvalid(vinculo.getValue() == null);
        boolean temTipo = !tipo.isEmpty();
        boolean temData = data.getValue() != null;
        tipo.setInvalid(temTipo != temData);
        data.setInvalid(temTipo != temData);
        if (nome.isEmpty() || vinculo.getValue() == null) {
            return;
        }
        if (temTipo != temData) {
            UiKit.erro("Informe tipo e data juntos ou deixe ambos em branco para revisão.");
            return;
        }
        if (arquivo == null || arquivo.conteudo() == null || arquivo.conteudo().length == 0) {
            UiKit.erro("Selecione um arquivo PDF, JPG ou PNG.");
            return;
        }
        aoSalvar.accept(new DadosDocumento(nome.getValue(), temTipo ? tipo.getValue() : null,
                data.getValue(), vinculo.getValue(), arquivo.nome(), arquivo.mime(),
                arquivo.conteudo()));
        close();
    }
}
