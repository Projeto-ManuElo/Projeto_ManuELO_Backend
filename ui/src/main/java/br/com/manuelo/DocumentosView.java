package br.com.manuelo;

import java.io.ByteArrayInputStream;
import java.util.List;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;

import com.Projeto.manuelo.servico.DocumentoService;

import br.com.manuelo.dados.DocumentoRepositorio;
import br.com.manuelo.dados.DocumentoRepositorio.DocumentoLista;
import br.com.manuelo.dados.Dominio;

@Route(value = "documentos", layout = MainLayout.class)
@PageTitle("Documentos")
public class DocumentosView extends Div {

    private static final int LIMITE = 60;
    private static final List<String> TIPOS = List.of("Contrato", "Comprovante", "Laudo", "Cadastro");

    private final DocumentoRepositorio repositorio;
    private final DocumentoService documentoService;
    private final TextField nomeInquilino = UiKit.text("Nome do inquilino", "nome do inquilino, sobrenome");
    private final ComboBox<String> tipoDocumento = UiKit.select("Tipo de documento", "Todos", TIPOS);
    private final ComboBox<String> tipoFinanceiro = UiKit.select("Tipo financeiro", "Todos",
            List.of("Entrada", "Saída"));
    private final Div lista = new Div();
    private final DocumentoDialog dialog;

    public DocumentosView(DocumentoRepositorio repositorio, DocumentoService documentoService) {
        this.repositorio = repositorio;
        this.documentoService = documentoService;
        this.dialog = new DocumentoDialog(repositorio.opcoes(), this::adicionar);
        addClassName("page");

        Button adicionar = UiKit.primaryIcon("Adicionar documento", VaadinIcon.PLUS, e -> dialog.abrir());
        add(UiKit.header("Documentos", "Envie, organize e acompanhe arquivos vinculados à operação", adicionar));

        add(criarFiltros());
        lista.addClassName("doc-list");
        add(UiKit.card(UiKit.cardHeader("Documento recentes", null), lista));
        renderizar();
    }

    private Div criarFiltros() {
        Div acoes = new Div(
                UiKit.secondary("Cancelar", e -> {
                    limpar();
                    renderizar();
                }),
                UiKit.primary("Filtrar", e -> renderizar()));
        acoes.addClassName("form-actions");

        nomeInquilino.addClassName("full");
        Div grid = UiKit.formGrid(nomeInquilino, tipoDocumento, tipoFinanceiro);
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private void limpar() {
        nomeInquilino.clear();
        tipoDocumento.clear();
        tipoFinanceiro.clear();
    }

    private void renderizar() {
        lista.removeAll();
        List<DocumentoLista> documentos;
        try {
            documentos = repositorio.listar(valor(nomeInquilino.getValue()),
                    valor(tipoDocumento.getValue()), valor(tipoFinanceiro.getValue()), LIMITE);
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
            return;
        }

        if (documentos.isEmpty()) {
            lista.add(UiKit.emptyState("Nenhum documento encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        for (DocumentoLista documento : documentos) {
            Div icone = new Div(VaadinIcon.FILE_TEXT.create());
            icone.addClassName("doc-icon");

            Div titulo = new Div(new Span(documento.nomeArquivo()));
            titulo.addClassName("doc-title");

            String inquilino = documento.nomeInquilino() == null || documento.nomeInquilino().isBlank()
                    ? "Sem inquilino vinculado"
                    : documento.nomeInquilino();
            Div meta = new Div(new Span(inquilino + " • " + documento.meta()));
            meta.addClassName("doc-meta");

            Div copia = new Div(titulo, meta);
            copia.addClassName("doc-copy");

            String rotulo = documento.pendente() ? "Pendente" : Dominio.tipoDocumento(documento.tipo());
            Span badge = UiKit.badge(rotulo);

            Div item = new Div(icone, copia, badge);
            item.addClassName("doc-item");
            item.getElement().setAttribute("data-id", documento.id());
            item.addClickListener(e -> {
                if (documento.pendente()) {
                    abrirClassificacao(documento);
                } else {
                    baixar(documento);
                }
            });
            lista.add(item);
        }
    }

    private void abrirClassificacao(DocumentoLista documento) {
        ComboBox<String> tipo = UiKit.select("Tipo de documento", "Selecione", TIPOS);
        DatePicker data = UiKit.date("Data do documento");

        Dialog modal = new Dialog();
        modal.addClassName("modal");
        modal.setWidth("520px");
        modal.setMaxWidth("94vw");
        modal.setCloseOnOutsideClick(false);

        Div header = UiKit.dialogHeader("Classificar documento", documento.nomeArquivo());
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> modal.close()));

        Button cancelar = UiKit.secondary("Cancelar", e -> modal.close());
        Button classificar = UiKit.primary("Classificar", e -> {
            try {
                if (tipo.getValue() == null || tipo.getValue().isBlank()) {
                    throw new IllegalArgumentException("Selecione o tipo do documento.");
                }
                if (data.getValue() == null) {
                    throw new IllegalArgumentException("Informe a data do documento.");
                }
                documentoService.classificar(documento.id(), tipo.getValue(), data.getValue());
                modal.close();
                renderizar();
                UiKit.aviso("Documento classificado com sucesso.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });

        modal.add(header, UiKit.stack(UiKit.formGrid(tipo, data)),
                UiKit.dialogFooter(cancelar, classificar));
        modal.open();
    }

    private void baixar(DocumentoLista documento) {
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

    private static String valor(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private void adicionar(DocumentoDialog.DadosDocumento dados) {
        try {
            documentoService.salvar(dados.nome(), dados.tipo(), dados.data(),
                    dados.vinculo().tipo(), dados.vinculo().id(), dados.nomeArquivo(),
                    dados.mime(), dados.conteudo());
            renderizar();
            UiKit.aviso("Documento adicionado com sucesso.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }
}
