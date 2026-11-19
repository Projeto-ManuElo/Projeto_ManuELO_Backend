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
