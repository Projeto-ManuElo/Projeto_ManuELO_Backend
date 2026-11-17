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
import com.vaadin.flow.component.upload.Upload;

public class ImovelDialog extends Dialog {

    public record DadosImovel(String id, String logradouro, String numero, String complemento,
            String bairro, String cidade, String uf, String cep, String tipo, String status,
            BigDecimal aluguel, String observacoes, UiKit.ArquivoSelecionado contrato) {
    }

    private final Consumer<DadosImovel> aoSalvar;
    private final TextField endereco = UiKit.text("Endereço", "Ex: Rua das Flores, 88");
    private final ComboBox<String> tipo = UiKit.select("Tipo", "Escolha um tipo",
            List.of("Apartamento", "Casa", "Comercial", "Terreno"));
    private final TextField status = UiKit.text("Status", "");
    private final TextField aluguel = UiKit.text("Aluguel atual", "");
    private final TextField inquilino = UiKit.text("Inquilino vinculado", "Buscar por nome ou documento");
    private final Upload contratoUpload;
    private final Button reformas = UiKit.secondary("Reformas", e -> {
        close();
        UiKit.ir("imoveis/reformas");
    });
    private final Span titulo = new Span("Adicionar Imóvel");

    private String idAtual;
    private BigDecimal aluguelAtual = BigDecimal.ZERO;
    private String observacoesAtuais = "";
    private String enderecoOriginal = "";
    private String logradouroAtual = "";
    private String numeroAtual = "";
    private String complementoAtual = "";
    private String bairroAtual = "Centro";
    private String cidadeAtual = "Aracaju";
    private String ufAtual = "SE";
    private String cepAtual = "";
    private UiKit.ArquivoSelecionado contrato;

    public ImovelDialog(Consumer<DadosImovel> aoSalvar) {
        this.aoSalvar = aoSalvar;
        contratoUpload = UiKit.upload("Arraste o contrato aqui ou clique para selecionar",
                "PDF, JPG ou PNG, até 10 MB", this::selecionarContrato);
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

        inquilino.setReadOnly(true);
        inquilino.setPlaceholder("Definido pelo contrato vigente");
        status.setReadOnly(true);
        aluguel.setReadOnly(true);

        Span rotuloContrato = new Span("Contrato");
        rotuloContrato.addClassName("form-label");

        Div body = new Div(
                endereco,
                UiKit.formGrid(tipo, status, aluguel),
                inquilino,
                rotuloContrato,
                contratoUpload);
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
        enderecoOriginal = "";
        logradouroAtual = "";
        numeroAtual = "";
        complementoAtual = "";
        bairroAtual = "Centro";
        cidadeAtual = "Aracaju";
        ufAtual = "SE";
        cepAtual = "";
        titulo.setText("Adicionar Imóvel");
        endereco.clear();
        tipo.clear();
        status.setValue("Vago");
        aluguel.setValue("R$ 0,00");
        inquilino.clear();
        contrato = null;
        contratoUpload.clearFileList();
        reformas.setVisible(false);
        open();
    }

    public void abrirEdicao(Modelos.Imovel imovel) {
        idAtual = imovel.id();
        titulo.setText("Editar Imóvel");
        aluguelAtual = imovel.aluguel();
        observacoesAtuais = imovel.observacoes();
        logradouroAtual = imovel.logradouro();
        numeroAtual = imovel.numero();
        complementoAtual = imovel.complemento() == null ? "" : imovel.complemento();
        bairroAtual = imovel.bairro();
        cidadeAtual = imovel.cidade();
        ufAtual = imovel.uf();
        cepAtual = imovel.cep() == null ? "" : imovel.cep();
        enderecoOriginal = imovel.endereco();
        endereco.setValue(enderecoOriginal == null ? "" : enderecoOriginal);
        tipo.setValue(imovel.tipo());
        status.setValue(imovel.status());
        aluguel.setValue(dinheiro(imovel.aluguel()));
        inquilino.setValue(imovel.inquilino() == null ? "" : imovel.inquilino());
        contrato = null;
        contratoUpload.clearFileList();
        reformas.setVisible(true);
        open();
    }

    private void selecionarContrato(UiKit.ArquivoSelecionado selecionado) {
        this.contrato = selecionado;
    }

    private void salvar() {
        endereco.setInvalid(endereco.isEmpty());
        tipo.setInvalid(tipo.isEmpty());
        if (endereco.isEmpty() || tipo.isEmpty()) {
            return;
        }

        String logradouro = logradouroAtual;
        String numero = numeroAtual;
        String complemento = complementoAtual;
        String bairro = bairroAtual;
        String texto = endereco.getValue().trim();
        if (!texto.equals(enderecoOriginal)) {
            String[] partes = br.com.manuelo.dados.ImovelRepositorio.separarEndereco(texto);
            logradouro = partes[0];
            numero = partes[1];
            complemento = partes[2] == null ? "" : partes[2];
            bairro = partes[3];
        }

        aoSalvar.accept(new DadosImovel(idAtual, logradouro, numero, complemento, bairro,
                cidadeAtual, ufAtual, cepAtual, tipo.getValue(),
                status.getValue() == null || status.getValue().isBlank() ? "Vago" : status.getValue(),
                aluguelAtual, observacoesAtuais, contrato));
        close();
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0,00";
        }
        return String.format(UiKit.PT_BR, "R$ %.2f", valor);
    }
}
