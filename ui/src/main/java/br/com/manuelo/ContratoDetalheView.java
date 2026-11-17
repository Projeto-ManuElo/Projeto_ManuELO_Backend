package br.com.manuelo;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;

import com.Projeto.manuelo.servico.CaucaoService;
import com.Projeto.manuelo.servico.ContratoCadastroService;
import com.Projeto.manuelo.servico.DocumentoService;
import com.Projeto.manuelo.servico.PagamentoAluguelService;

import br.com.manuelo.dados.ContratoRepositorio;
import br.com.manuelo.dados.DocumentoRepositorio;
import br.com.manuelo.dados.DocumentoRepositorio.DocumentoVinculo;
import br.com.manuelo.dados.ImovelRepositorio;

@Route(value = "contratos/detalhe", layout = MainLayout.class)
@PageTitle("Detalhes do contrato")
public class ContratoDetalheView extends Div implements BeforeEnterObserver {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy",
            Locale.forLanguageTag("pt-BR"));

    private final ContratoRepositorio repositorio;
    private final ContratoCadastroService cadastro;
    private final PagamentoAluguelService pagamentos;
    private final DocumentoRepositorio documentos;
    private final DocumentoService documentoService;
    private final CaucaoService caucao;
    private final ContratoDialog novoContrato;
    private ContratoRepositorio.Detalhe contrato;

    public ContratoDetalheView(ContratoRepositorio repositorio, ContratoCadastroService cadastro,
            PagamentoAluguelService pagamentos, ImovelRepositorio imoveis,
            DocumentoRepositorio documentos, DocumentoService documentoService,
            CaucaoService caucao) {
        this.repositorio = repositorio;
        this.cadastro = cadastro;
        this.pagamentos = pagamentos;
        this.documentos = documentos;
        this.documentoService = documentoService;
        this.caucao = caucao;
        this.novoContrato = new ContratoDialog(imoveis.opcoes(), this::criarContrato);
        addClassName("page");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String id = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("id", List.of()).stream().findFirst().orElse("");
        contrato = id.isBlank() ? null : repositorio.buscar(id);
        if (contrato == null) {
            List<Modelos.Contrato> todos = repositorio.listar();
            if (!todos.isEmpty()) {
                contrato = repositorio.buscar(todos.get(0).id());
            }
        }
        renderizar();
    }

    private void criarContrato(ContratoDialog.DadosContrato dados) {
        try {
            String id = cadastro.criar(dados.paraServico());
            UiKit.ArquivoSelecionado arquivo = dados.arquivo();
            if (arquivo != null && arquivo.conteudo() != null && arquivo.conteudo().length > 0) {
                documentoService.salvar("Contrato - " + dados.nome(), "Contrato",
                        LocalDate.now(), "CONTRATO", id, arquivo.nome(), arquivo.mime(),
                        arquivo.conteudo());
            }
            UiKit.aviso("Contrato criado com sucesso.");
            UI.getCurrent().navigate(ContratoDetalheView.class, QueryParameters.of("id", id));
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private void renderizar() {
        removeAll();
        if (contrato == null) {
            add(UiKit.header("Contratos", null,
                    UiKit.secondary("Novo contrato", e -> novoContrato.abrir())));
            add(UiKit.emptyState("Nenhum contrato encontrado",
                    "Cadastre um contrato para visualizar os detalhes."));
            return;
        }

        Button novo = UiKit.secondary("Novo contrato", e -> novoContrato.abrir());
        Button pagar = UiKit.primary("Pagar o aluguel",
                e -> new PagarAluguelDialog(contrato, pagamentos, this::atualizar).open());
        add(UiKit.header("Contratos", null, novo, pagar));

        add(cardInquilino());
        add(cardContrato());
    }

    private void atualizar() {
        contrato = repositorio.buscar(contrato.id());
        renderizar();
    }

    private Div cardInquilino() {
        TextField nome = somenteLeitura("Nome", contrato.inquilino());
        nome.addClassName("full");
        TextField endereco = somenteLeitura("Endereço", contrato.endereco());
        TextField cpf = somenteLeitura("CPF", contrato.documento());
        TextField email = somenteLeitura("Email", contrato.email());

        String acao = contrato.acaoJudicial()
                ? (contrato.acaoJudicialDescricao() == null ? "Sim" : contrato.acaoJudicialDescricao())
                : "";
        TextField acaoJudicial = somenteLeitura("Tem ação judicial", acao);
        acaoJudicial.setPlaceholder("Se sim, qual?");

        TextField caucaoInicial = somenteLeitura("Caução Inicial",
                dinheiro(contrato.caucaoDepositos()));
        TextField caucaoAtual = somenteLeitura("Caução atual", dinheiro(contrato.caucaoSaldo()));
        TextField periodo = somenteLeitura("Vigência", periodo());
        periodo.addClassName("full");

        Div grid = UiKit.formGrid(nome, endereco, cpf, email, acaoJudicial, caucaoInicial, caucaoAtual,
                periodo);

        Div tituloFiador = new Div();
        tituloFiador.addClassName("section-title");
        tituloFiador.add(new Span("Fiador"));

        TextField fiadorNome = somenteLeitura("Nome", texto(contrato.fiadorNome()));
        fiadorNome.addClassName("full");
        TextField fiadorEmail = somenteLeitura("Email", texto(contrato.fiadorEmail()));
        fiadorEmail.addClassName("full");
        TextField fiadorNumero = somenteLeitura("Numero", texto(contrato.fiadorTelefone()));
        TextField fiadorCpf = somenteLeitura("CPF", texto(contrato.fiadorDocumento()));

        Div gridFiador = UiKit.formGrid(fiadorNome, fiadorEmail, fiadorNumero, fiadorCpf);
        gridFiador.addClassName("fiador-grid");

        return UiKit.card(UiKit.cardHeader("Informações do Inquilino", null), grid,
                tituloFiador, gridFiador);
    }

    private Div cardContrato() {
        Div card = UiKit.card();
        card.add(UiKit.cardHeader("Contrato referente", null));

        List<DocumentoVinculo> anexos = documentos.listarPorVinculo("CONTRATO", contrato.id());
        if (anexos.isEmpty()) {
            card.add(UiKit.muted("Nenhum documento anexado a este contrato."));
        } else {
            for (DocumentoVinculo anexo : anexos) {
                card.add(UiKit.secondary("Baixar " + anexo.nomeArquivo(), e -> baixar(anexo)));
            }
        }

        Div acoesCaucao = new Div(
                UiKit.secondary("Depositar caução", e -> abrirCaucao(true)),
                UiKit.secondary("Devolver caução", e -> abrirCaucao(false)));
        acoesCaucao.addClassName("form-actions");
        acoesCaucao.addClassName("start");

        Div acoesJudiciais = new Div(
                UiKit.secondary("Editar ação judicial", e -> abrirAcaoJudicial()));
        acoesJudiciais.addClassName("form-actions");
        acoesJudiciais.addClassName("start");

        card.add(acoesCaucao, acoesJudiciais);
        return card;
    }

    private void abrirAcaoJudicial() {
        Dialog modal = new Dialog();
        modal.addClassName("modal");
        modal.setWidth("560px");
        modal.setMaxWidth("94vw");
        modal.setCloseOnOutsideClick(false);

        Div header = UiKit.dialogHeader("Editar ação judicial", contrato.inquilino());
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> modal.close()));

        Checkbox possui = new Checkbox("Possui ação judicial");
        possui.setValue(contrato.acaoJudicial());
        TextField descricao = UiKit.text("Descrição da ação judicial",
                "Ex: Ação de cobrança");
        descricao.setValue(texto(contrato.acaoJudicialDescricao()));
        descricao.setEnabled(contrato.acaoJudicial());
        possui.addValueChangeListener(event -> {
            boolean marcado = Boolean.TRUE.equals(event.getValue());
            descricao.setEnabled(marcado);
            if (!marcado) {
                descricao.clear();
                descricao.setInvalid(false);
            }
        });

        Div body = new Div(possui, descricao);
        body.addClassName("modal-body");
        body.addClassName("stack");

        Button cancelar = UiKit.secondary("Cancelar", e -> modal.close());
        Button salvar = UiKit.primary("Salvar", e -> {
            boolean temAcao = Boolean.TRUE.equals(possui.getValue());
            boolean semDescricao = temAcao && (descricao.getValue() == null
                    || descricao.getValue().isBlank());
            descricao.setInvalid(semDescricao);
            if (semDescricao) {
                UiKit.erro("Informe a descrição da ação judicial.");
                return;
            }
            try {
                cadastro.atualizarAcaoJudicial(contrato.id(), temAcao,
                        temAcao ? descricao.getValue() : null);
                modal.close();
                atualizar();
                UiKit.aviso("Ação judicial atualizada.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });

        modal.add(header, body, UiKit.dialogFooter(cancelar, salvar));
        modal.open();
    }

    private void abrirCaucao(boolean deposito) {
        Dialog modal = new Dialog();
        modal.addClassName("modal");
        modal.setWidth("520px");
        modal.setMaxWidth("94vw");
        modal.setCloseOnOutsideClick(false);

        String titulo = deposito ? "Depositar caução" : "Devolver caução";
        Div header = UiKit.dialogHeader(titulo,
                "Saldo atual: " + dinheiro(contrato.caucaoSaldo()));
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> modal.close()));

        TextField valor = UiKit.text("Valor", "R$ 0,00");
        DatePicker data = UiKit.date("Data");
        data.setValue(LocalDate.now());

        Div body = new Div(UiKit.formGrid(valor, data));
        body.addClassName("modal-body");

        Button cancelar = UiKit.secondary("Cancelar", e -> modal.close());
        Button confirmar = UiKit.primary(deposito ? "Depositar" : "Devolver", e -> {
            BigDecimal valorNumerico = moeda(valor.getValue());
            if (valorNumerico == null || valorNumerico.signum() <= 0) {
                valor.setInvalid(true);
                UiKit.erro("Informe um valor maior que zero.");
                return;
            }
            try {
                if (deposito) {
                    caucao.depositar(contrato.id(), valorNumerico, data.getValue());
                } else {
                    caucao.devolver(contrato.id(), valorNumerico, data.getValue());
                }
                modal.close();
                atualizar();
                UiKit.aviso(deposito ? "Depósito de caução registrado."
                        : "Devolução de caução registrada.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });

        modal.add(header, body, UiKit.dialogFooter(cancelar, confirmar));
        modal.open();
    }

    private void baixar(DocumentoVinculo documento) {
        try {
            DocumentoService.Arquivo arquivo = documentoService.baixar(documento.id());
            StreamResource resource = new StreamResource(arquivo.nome(),
                    () -> new ByteArrayInputStream(arquivo.conteudo()));
            resource.setContentType(arquivo.mime());
            resource.setHeader("Content-Disposition",
                    "attachment; filename=\"" + arquivo.nome().replace("\"", "") + "\"");
            StreamRegistration registro = UI.getCurrent().getSession().getResourceRegistry()
                    .registerResource(resource);
            UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
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

    private String periodo() {
        String inicio = contrato.inicio() == null ? "" : DATA.format(contrato.inicio());
        String fim = contrato.fim() == null ? "sem data final" : DATA.format(contrato.fim());
        return inicio + " a " + fim;
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "R$ 0";
        }
        return "R$ " + valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private TextField somenteLeitura(String label, String valor) {
        TextField field = UiKit.text(label, "");
        field.setValue(valor == null ? "" : valor);
        field.setReadOnly(true);
        return field;
    }
}
