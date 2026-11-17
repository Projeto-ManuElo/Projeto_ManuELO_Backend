package br.com.manuelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;

import com.Projeto.manuelo.servico.ContratoCadastroService;

import br.com.manuelo.dados.Dominio;
import br.com.manuelo.dados.ImovelRepositorio;

public class ContratoDialog extends Dialog {

    public record DadosContrato(String imovelId, String endereco, String nome, String cpf,
            String email, String telefone, String nomeFiador, String documentoFiador,
            String emailFiador, String telefoneFiador, boolean acaoJudicial,
            String acaoJudicialDescricao, BigDecimal aluguel, int diaVencimento,
            LocalDate vencimento, BigDecimal caucao, String contratoCaminho,
            UiKit.ArquivoSelecionado arquivo) {

        public ContratoCadastroService.Dados paraServico() {
            return new ContratoCadastroService.Dados(imovelId, endereco, nome, cpf, email,
                    telefone, nomeFiador, documentoFiador, emailFiador, telefoneFiador,
                    acaoJudicial, acaoJudicialDescricao, aluguel, diaVencimento, vencimento,
                    caucao, contratoCaminho);
        }
    }

    private final Consumer<DadosContrato> aoSalvar;
    private final Span titulo = new Span("Novo contrato");
    private final ComboBox<ImovelRepositorio.Opcao> imovel = new ComboBox<>("Imóvel existente");
    private final TextField endereco = UiKit.text("Endereço", "Ex: Ap 302. Ed. Aurora");
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel Oliveira dos Santos");
    private final TextField cpf = UiKit.text("CPF", "Ex: 074.xxx.39-xy");
    private final EmailField email = UiKit.email("Email", "Ex: joao@wmail.com");
    private final TextField telefone = UiKit.text("Telefone", "Ex: (79) 99999-9999");
    private final TextField nomeFiador = UiKit.text("Nome do fiador", "Ex: Maria Oliveira dos Santos");
    private final TextField documentoFiador = UiKit.text("CPF/CNPJ do fiador", "Ex: 123.xxx.xxx.123");
    private final EmailField emailFiador = UiKit.email("Email do fiador", "Ex: fiador@wmail.com");
    private final TextField telefoneFiador = UiKit.text("Telefone do fiador", "Ex: (79) 99999-9999");
    private final Checkbox acaoJudicial = new Checkbox("Tem ação judicial");
    private final TextField acaoJudicialDescricao = UiKit.text("Descrição da ação judicial",
            "Ex: Ação de cobrança");
    private final TextField valor = UiKit.text("Valor do aluguel", "R$ 0,00");
    private final IntegerField diaVencimento = new IntegerField("Dia de vencimento");
    private final DatePicker vencimento = UiKit.date("Vencimento do contrato");
    private final TextField caucao = UiKit.text("Caução inicial", "R$ 0,00");
    private final Upload contratoUpload;
    private UiKit.ArquivoSelecionado arquivo;

    public ContratoDialog(List<ImovelRepositorio.Opcao> imoveis, Consumer<DadosContrato> aoSalvar) {
        this.aoSalvar = aoSalvar;

        imovel.setItems(imoveis == null ? List.of() : imoveis);
        imovel.setItemLabelGenerator(ImovelRepositorio.Opcao::rotulo);
        imovel.setPlaceholder("Selecione o imóvel");
        imovel.setWidthFull();
        imovel.setClearButtonVisible(true);

        diaVencimento.setMin(1);
        diaVencimento.setMax(31);
        diaVencimento.setStepButtonsVisible(true);
        diaVencimento.setWidthFull();

        acaoJudicialDescricao.setEnabled(false);
        acaoJudicial.addValueChangeListener(event -> {
            boolean marcado = Boolean.TRUE.equals(event.getValue());
            acaoJudicialDescricao.setEnabled(marcado);
            if (!marcado) {
                acaoJudicialDescricao.clear();
                acaoJudicialDescricao.setInvalid(false);
            }
        });

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

        Span secaoInquilino = new Span("Inquilino");
        secaoInquilino.addClassName("form-label");
        Span secaoFiador = new Span("Fiador");
        secaoFiador.addClassName("form-label");
        Span secaoContrato = new Span("Contrato");
        secaoContrato.addClassName("form-label");

        contratoUpload = UiKit.upload("Arraste o contrato aqui ou clique para selecionar",
                "PDF, JPG ou PNG, até 10 MB", selecionado -> arquivo = selecionado);

        Div body = new Div(
                imovel,
                endereco,
                secaoInquilino,
                nome,
                UiKit.formGrid(cpf, telefone),
                email,
                secaoFiador,
                nomeFiador,
                UiKit.formGrid(documentoFiador, telefoneFiador),
                emailFiador,
                acaoJudicial,
                acaoJudicialDescricao,
                secaoContrato,
                UiKit.formGrid(valor, diaVencimento),
                vencimento,
                caucao,
                contratoUpload);
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button salvar = UiKit.primary("Criar contrato", e -> salvar());
        add(header, body, UiKit.dialogFooter(cancelar, salvar));
    }

    public void abrir() {
        titulo.setText("Novo contrato");
        imovel.clear();
        endereco.clear();
        nome.clear();
        cpf.clear();
        email.clear();
        telefone.clear();
        nomeFiador.clear();
        documentoFiador.clear();
        emailFiador.clear();
        telefoneFiador.clear();
        acaoJudicial.setValue(false);
        acaoJudicialDescricao.clear();
        acaoJudicialDescricao.setEnabled(false);
        valor.clear();
        diaVencimento.setValue(5);
        vencimento.clear();
        caucao.clear();
        arquivo = null;
        contratoUpload.clearFileList();
        open();
    }

    private void salvar() {
        String imovelId = imovel.getValue() == null ? null : imovel.getValue().id();
        boolean semImovel = imovelId == null && (endereco.getValue() == null
                || endereco.getValue().isBlank());

        nome.setInvalid(nome.isEmpty());
        cpf.setInvalid(cpf.isEmpty());
        imovel.setInvalid(semImovel);
        endereco.setInvalid(semImovel);

        BigDecimal aluguel = moeda(valor.getValue());
        boolean aluguelInvalido = aluguel == null || aluguel.signum() <= 0;
        valor.setInvalid(aluguelInvalido);

        Integer dia = diaVencimento.getValue();
        boolean diaInvalido = dia == null || dia < 1 || dia > 31;
        diaVencimento.setInvalid(diaInvalido);

        boolean temAcao = Boolean.TRUE.equals(acaoJudicial.getValue());
        boolean acaoSemDescricao = temAcao && (acaoJudicialDescricao.getValue() == null
                || acaoJudicialDescricao.getValue().isBlank());
        acaoJudicialDescricao.setInvalid(acaoSemDescricao);

        BigDecimal valorCaucao = moeda(caucao.getValue());
        if (valorCaucao == null) {
            valorCaucao = BigDecimal.ZERO;
        }
        boolean caucaoInvalida = valorCaucao.signum() < 0;
        caucao.setInvalid(caucaoInvalida);

        String documento = Dominio.soDigitos(documentoFiador.getValue());
        String nomeDoFiador = texto(nomeFiador.getValue());
        boolean fiadorSemDocumento = nomeDoFiador != null && documento.isEmpty();
        boolean fiadorSemNome = !documento.isEmpty() && nomeDoFiador == null;
        documentoFiador.setInvalid(fiadorSemDocumento);
        nomeFiador.setInvalid(fiadorSemNome);

        if (nome.isEmpty() || cpf.isEmpty() || semImovel || aluguelInvalido || diaInvalido
                || acaoSemDescricao || caucaoInvalida || fiadorSemDocumento || fiadorSemNome) {
            return;
        }

        aoSalvar.accept(new DadosContrato(imovelId, endereco.getValue(), nome.getValue(),
                cpf.getValue(), email.getValue(), telefone.getValue(), nomeDoFiador,
                documentoFiador.getValue(), emailFiador.getValue(), telefoneFiador.getValue(),
                temAcao, temAcao ? acaoJudicialDescricao.getValue() : null, aluguel, dia,
                vencimento.getValue(), valorCaucao, null, arquivo));
        close();
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

    private String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
