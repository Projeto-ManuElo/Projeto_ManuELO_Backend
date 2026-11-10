package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

import java.util.function.Consumer;

public class ImovelDialog extends Dialog {

    public record DadosImovel(String endereco, String tipo, String status, String inquilino) {}

    private final H3 titulo = new H3("Adicionar Imóvel");
    private final TextField endereco = new TextField("Endereço");
    private final ComboBox<String> tipo = new ComboBox<>("Tipo");
    private final ComboBox<String> status = new ComboBox<>("Status");
    private final TextField inquilino = new TextField("Inquilino vinculado");
    private final Button reformas = new Button("Reformas");
    private final Consumer<DadosImovel> aoSalvar;
    private boolean editando = false;

    public ImovelDialog(Consumer<DadosImovel> aoSalvar) {
        this.aoSalvar = aoSalvar;
        setWidth("520px");
        setCloseOnOutsideClick(false);

        titulo.getStyle().set("margin", "0");

        endereco.setPlaceholder("Ex: Rua das Flores, 88");
        endereco.setWidthFull();
        endereco.setRequiredIndicatorVisible(true);

        tipo.setItems("Apartamento", "Casa", "Comercial");
        tipo.setPlaceholder("Escolha um tipo");
        tipo.setWidthFull();
        tipo.setRequiredIndicatorVisible(true);

        status.setItems("Alugado", "Vago", "Em manutenção");
        status.setValue("Vago");
        status.setWidthFull();

        HorizontalLayout linha = new HorizontalLayout(tipo, status);
        linha.setWidthFull();

        inquilino.setPlaceholder("Buscar por nome ou documento");
        inquilino.setWidthFull();

        Span rotuloContrato = new Span("Contrato");
        rotuloContrato.getStyle().set("font-size", "var(--lumo-font-size-s)");

        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            // futuramente: guardar o arquivo (bytes) junto com o imóvel
        }));
        upload.setAcceptedMimeTypes("application/pdf", "image/png", "image/jpeg");
        upload.setMaxFileSize(10 * 1024 * 1024);
        upload.setDropLabel(new Span("Arraste o contrato aqui ou clique para selecionar"));

        reformas.getStyle().set("background", "#1a1f44").set("color", "white");
        reformas.addClickListener(e -> {
            //(DESENVOLVER) abrir a tela de reformas do imóvel
        });

        Button cancelar = new Button("Cancelar", e -> close());
        Button salvar = new Button("Salvar", e -> salvar());
        salvar.getStyle().set("background", "#5b5bf0").set("color", "white");

        HorizontalLayout botoes = new HorizontalLayout(reformas, cancelar, salvar);
        botoes.setWidthFull();
        botoes.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VerticalLayout conteudo = new VerticalLayout(
                titulo, endereco, linha, inquilino, rotuloContrato, upload, botoes);
        conteudo.setPadding(false);
        add(conteudo);
    }


    public void abrirNovo() {
        editando = false;
        titulo.setText("Adicionar Imóvel");
        endereco.clear();
        tipo.clear();
        status.setValue("Vago");
        inquilino.clear();
        reformas.setVisible(false);
        open();
    }

    
    public void abrirEdicao(DadosImovel d) {
        editando = true;
        titulo.setText("Editar Imóvel");
        endereco.setValue(d.endereco() == null ? "" : d.endereco());
        tipo.setValue(d.tipo());
        status.setValue(d.status());
        inquilino.setValue(d.inquilino() == null ? "" : d.inquilino());
        reformas.setVisible(true);
        open();
    }

    public boolean isEditando() {
        return editando;
    }

    private void salvar() {
        endereco.setInvalid(endereco.isEmpty());
        tipo.setInvalid(tipo.isEmpty());
        if (endereco.isEmpty() || tipo.isEmpty()) {
            return;
        }
        aoSalvar.accept(new DadosImovel(
                endereco.getValue(), tipo.getValue(), status.getValue(), inquilino.getValue()));
        close();
    }
}