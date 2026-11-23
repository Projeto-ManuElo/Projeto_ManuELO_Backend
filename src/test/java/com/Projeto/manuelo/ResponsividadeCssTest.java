package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * E02/FE49: a interface precisa ser utilizável no celular. Este teste-guarda
 * confere os breakpoints e os tratamentos que evitam estouro de largura nas
 * telas (tabelas largas, ações de cabeçalho, modais e fluxo de autenticação).
 *
 * <p>No HEAD anterior à correção o CSS não tinha o breakpoint de 520px nem
 * rolagem horizontal de tabela; por isso o teste falhava pelo motivo certo.</p>
 */
class ResponsividadeCssTest {

    private static final Path CSS =
            Path.of("ui/src/main/resources/META-INF/resources/styles.css");

    @Test
    void cssTemBreakpointParaCelular() throws IOException {
        assertThat(Files.readString(CSS))
                .as("E02: deve existir um breakpoint específico para celular (520px)")
                .contains("@media (max-width: 520px)");
    }

    @Test
    void tabelasLargasPermitemRolagemHorizontal() throws IOException {
        String css = Files.readString(CSS);

        assertThat(css)
                .as("E02: tabelas com muitas colunas não podem estourar a largura da tela")
                .containsPattern("(?s)\\.table\\s*\\{[^}]*overflow-x:\\s*auto");
    }

    @Test
    void acoesDoCabecalhoQuebramLinhaEmTelasMedias() throws IOException {
        String css = Files.readString(CSS);
        int inicio = css.indexOf("@media (max-width: 760px)");
        int fim = css.indexOf("@media (max-width: 520px)");

        assertThat(inicio).as("E02: breakpoint de 760px deve existir").isGreaterThanOrEqualTo(0);
        assertThat(fim).as("E02: breakpoint de 520px deve existir").isGreaterThan(inicio);

        String bloco760 = css.substring(inicio, fim);
        assertThat(bloco760)
                .as("E02: botões do cabeçalho devem quebrar linha no celular")
                .contains(".page-actions")
                .contains("flex-wrap: wrap");
    }

    @Test
    void fluxoDeAutenticacaoEModaisSeAdaptamAoCelular() throws IOException {
        String css = Files.readString(CSS);
        String bloco520 = css.substring(css.indexOf("@media (max-width: 520px)"));

        assertThat(bloco520)
                .as("E02: autenticação, modais e conteúdo devem reduzir no celular")
                .contains(".auth-check")
                .contains(".modal-footer")
                .contains(".app-content");
    }
}
