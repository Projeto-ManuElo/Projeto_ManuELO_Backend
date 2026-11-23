package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ArquiteturaViewsTest {

    private static final Pattern REPOSITORIO = Pattern.compile("\\b\\w*Repositorio\\b");

    @Test
    void viewsNaoDefinemGridTemplateColumnsInline() throws IOException {
        Path raiz = Path.of("ui/src/main/java");
        assertThat(raiz).isDirectory();

        String proibido = "getStyle().set(\"grid-template-columns\"";
        List<String> violacoes = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            arquivos.filter(arquivo -> arquivo.toString().endsWith(".java"))
                    .sorted()
                    .forEach(arquivo -> {
                        try {
                            List<String> linhas = Files.readAllLines(arquivo);
                            for (int i = 0; i < linhas.size(); i++) {
                                if (linhas.get(i).contains(proibido)) {
                                    violacoes.add(raiz.relativize(arquivo) + ":" + (i + 1));
                                }
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        }

        assertThat(violacoes)
                .as("A-92: views/dialogs não podem fixar grid-template-columns inline; "
                        + "use as classes de tema existentes")
                .isEmpty();
    }

    @Test
    void downloadsDeArquivoUsamAnchorENaoPageOpen() throws IOException {
        Path raiz = Path.of("ui/src/main/java/br/com/manuelo");
        List<String> violacoes = new ArrayList<>();
        List<String> alvos = List.of("RelatoriosView.java", "ExportarExtratoDialog.java",
                "MovimentacaoFinanceiraView.java", "DocumentosView.java",
                "ContratoDetalheView.java");
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            arquivos.filter(arquivo -> alvos.contains(arquivo.getFileName().toString()))
                    .sorted()
                    .forEach(arquivo -> {
                        try {
                            if (Files.readString(arquivo).contains("getPage().open")) {
                                violacoes.add(raiz.relativize(arquivo).toString());
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        }

        assertThat(violacoes)
                .as("E01/E07: downloads devem usar UiKit.download (Anchor + clique), "
                        + "não Page.open após o round-trip do servidor")
                .isEmpty();
    }

    @Test
    void cadastroViewUsaResultadoDetalhadoDoServico() throws IOException {
        String conteudo = Files.readString(
                Path.of("ui/src/main/java/br/com/manuelo/CadastroView.java"));

        assertThat(conteudo)
                .as("O01: CadastroView deve usar cadastrarComResultado e distinguir "
                        + "EMAIL_EM_USO de DADOS_INVALIDOS")
                .contains("cadastrarComResultado")
                .contains("EMAIL_EM_USO")
                .contains("DADOS_INVALIDOS");
    }

    @Test
    void documentosRecentesOrdenamPorEnviadoEmEId() throws IOException {
        String conteudo = Files.readString(
                Path.of("ui/src/main/java/br/com/manuelo/dados/DocumentoRepositorio.java"));

        assertThat(conteudo)
                .as("O02: documentos recentes devem ordenar por enviado_em DESC "
                        + "e desempatar de forma determinística (nome_arquivo, id)")
                .contains("ORDER BY enviado_em DESC, nome_arquivo ASC, id ASC");
    }

    @Test
    void viewsEDialogosNaoDependemDeRepositoriosJdbc() throws IOException {
        Path raiz = Path.of("ui/src/main/java/br/com/manuelo");
        assertThat(raiz).isDirectory();

        List<String> violacoes = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            arquivos.filter(arquivo -> arquivo.toString().endsWith(".java"))
                    .filter(arquivo -> !arquivo.startsWith(raiz.resolve("dados")))
                    .forEach(arquivo -> {
                        try {
                            String conteudo = Files.readString(arquivo);
                            if (REPOSITORIO.matcher(conteudo).find()) {
                                violacoes.add(raiz.relativize(arquivo).toString());
                            }
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        }

        assertThat(violacoes)
                .as("views/dialogs devem usar serviços, não repositórios")
                .isEmpty();
    }
}
