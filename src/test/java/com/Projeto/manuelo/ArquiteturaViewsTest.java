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
