package br.com.manuelo;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.DocumentoRepositorio;

@Route(value = "documentos", layout = MainLayout.class)
@PageTitle("Documentos")
public class DocumentosView extends Div {

    private final DocumentoRepositorio repositorio;
    private List<DemoData.Documento> documentos;
    private final TextField nome = UiKit.text("Nome", "nome do inquilino, sobrenome");
    private final ComboBox<String> tipoDocumento = UiKit.select("Tipo de documento", "Todos",
            List.of("Contrato", "Comprovante", "Laudo", "Cadastro"));
    private final ComboBox<String> tipoFinanceiro = UiKit.select("Tipo financeiro", "Todos",
            List.of("Entrada", "Saída"));
    private final TextField nomeArquivo = UiKit.text("Nome", "nome do inquilino, sobrenome");
    private final Div lista = new Div();
    private final DocumentoDialog dialog;

    public DocumentosView(DocumentoRepositorio repositorio) {
        this.repositorio = repositorio;
        this.documentos = repositorio.listar(60);
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

        nome.addClassName("full");
        nomeArquivo.addClassName("full");
        Div grid = UiKit.formGrid(nome, tipoDocumento, tipoFinanceiro, nomeArquivo);
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private void limpar() {
        nome.clear();
        tipoDocumento.clear();
        tipoFinanceiro.clear();
        nomeArquivo.clear();
    }

    private void renderizar() {
        lista.removeAll();
        List<DemoData.Documento> filtrados = documentos.stream()
                .filter(this::corresponde)
                .toList();

        if (filtrados.isEmpty()) {
            lista.add(UiKit.emptyState("Nenhum documento encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        for (DemoData.Documento documento : filtrados) {
            Div icone = new Div(VaadinIcon.FILE_TEXT.create());
            icone.addClassName("doc-icon");

            Div titulo = new Div(new Span(documento.nome()));
            titulo.addClassName("doc-title");
            Div meta = new Div(new Span(documento.meta()));
            meta.addClassName("doc-meta");
            Div copia = new Div(titulo, meta);
            copia.addClassName("doc-copy");

            Span badge = "Pendente".equals(documento.situacao())
                    ? UiKit.badge("Pendente")
                    : UiKit.badge(documento.tipo());

            Div item = new Div(icone, copia, badge);
            item.addClassName("doc-item");
            item.addClickListener(e -> UiKit.aviso("Visualização de documentos em preparação."));
            lista.add(item);
        }
    }

    private boolean corresponde(DemoData.Documento documento) {
        String n = nome.getValue() == null ? "" : nome.getValue().trim().toLowerCase(Locale.ROOT);
        String a = nomeArquivo.getValue() == null ? "" : nomeArquivo.getValue().trim().toLowerCase(Locale.ROOT);
        return (n.isBlank() || documento.nome().toLowerCase(Locale.ROOT).contains(n))
                && (a.isBlank() || documento.nome().toLowerCase(Locale.ROOT).contains(a))
                && (tipoDocumento.getValue() == null || tipoDocumento.getValue().isBlank()
                        || tipoDocumento.getValue().equals(documento.tipo()));
    }

    private void adicionar(DocumentoDialog.DadosDocumento dados) {
        try {
            repositorio.salvar(dados.nome(), dados.tipo(), dados.data(), dados.vinculo());
            documentos = repositorio.listar(60);
            renderizar();
            UiKit.aviso("Documento adicionado com sucesso.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }
}
