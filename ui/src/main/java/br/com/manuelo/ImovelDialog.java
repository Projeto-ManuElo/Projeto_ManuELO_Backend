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
import com.vaadin.flow.component.textfield.TextField;

public class ImovelDialog extends Dialog {

    public record DadosImovel(String id, String endereco, String tipo, String status,
            String inquilino, BigDecimal aluguel, String observacoes) {
    }

    private final Consumer<DadosImovel> aoSalvar;
    private final TextField endereco = UiKit.text("Endereço", "Ex: Rua das Flores, 88");
    private final ComboBox<String> tipo = UiKit.select("Tipo", "Escolha um tipo",
            List.of("Apartamento", "Casa", "Comercial", "Terreno"));
    private final ComboBox<String> status = UiKit.select("Status", "Selecione",
            List.of("Alugado", "Vago", "Em manutenção"));
    private final TextField inquilino = UiKit.text("Inquilino vinculado", "Buscar por nome ou documento");
    private final Button reformas = UiKit.secondary("Reformas", e -> {
        close();
        UiKit.ir("imoveis/reformas");
    });
    private final Span titulo = new Span("Adicionar Imóvel");

    private String idAtual;
    private BigDecimal aluguelAtual = BigDecimal.ZERO;
    private String observacoesAtuais = "";

    public ImovelDialog(Consumer<DadosImovel> aoSalvar) {
        this.aoSalvar = aoSalvar;
        addClassName("modal");
        setWidth("620px");
        setMaxWidth("94vw");
        setMaxHeight("92vh");
        setCloseOnOutsideClick(false);

        titulo.addClassName("modal-title");
        Div heading = new Div(titulo);
        heading.addClassName("modal-heading");
        Div header = new Div(heading);
        header.addClassName("modal-header");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        Span rotuloContrato = new Span("Contrato");
        rotuloContrato.addClassName("form-label");

        Div body = new Div(
                endereco,
                UiKit.formGrid(tipo, status),
                inquilino,
                rotuloContrato,
                UiKit.upload("Arraste o contrato aqui ou clique para selecionar", "PDF, JPG ou PNG, até 10 MB"));
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Salvar", e -> salvar());
        Div footer = UiKit.dialogFooter(reformas, cancelar, salvar);

        add(header, body, footer);
    }

    public void abrirNovo() {
        idAtual = null;
        aluguelAtual = BigDecimal.ZERO;
        observacoesAtuais = "";
        titulo.setText("Adicionar Imóvel");
        endereco.clear();
        tipo.clear();
        status.setValue("Vago");
        inquilino.clear();
        reformas.setVisible(false);
        open();
    }

    public void abrirEdicao(DemoData.Imovel imovel) {
        idAtual = imovel.id();
        titulo.setText("Editar Imóvel");
        aluguelAtual = imovel.aluguel();
        observacoesAtuais = imovel.observacoes();
        endereco.setValue(imovel.endereco() == null ? "" : imovel.endereco());
        tipo.setValue(imovel.tipo());
        status.setValue(imovel.status());
        inquilino.setValue(imovel.inquilino() == null ? "" : imovel.inquilino());
        reformas.setVisible(true);
        open();
    }

    private void salvar() {
        endereco.setInvalid(endereco.isEmpty());
        tipo.setInvalid(tipo.isEmpty());
        if (endereco.isEmpty() || tipo.isEmpty()) {
            return;
        }
        aoSalvar.accept(new DadosImovel(idAtual, endereco.getValue(), tipo.getValue(),
                status.getValue() == null || status.getValue().isBlank() ? "Vago" : status.getValue(),
                inquilino.getValue(), aluguelAtual, observacoesAtuais));
        close();
    }
}
