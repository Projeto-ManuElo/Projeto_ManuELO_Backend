package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.datepicker.DatePicker;

import br.com.manuelo.UiKit;

/**
 * A-93/E03: todo campo de data criado por {@link UiKit#date(String)} deve estar em
 * português do Brasil e exibir/interpretar o formato {@code dd/MM/yyyy}, independente
 * do locale padrão da JVM/UI.
 *
 * Vermelho proposital: hoje {@code UiKit.date} só cria o {@link DatePicker} e não
 * configura locale nem i18n, então {@code getI18n()} é nulo e o campo herda o formato
 * padrão do componente.
 */
class UiKitDateTest {

    @Test
    void campoDeDataUsaLocalePtBrEFormatoDiaMesAno() {
        DatePicker campo = UiKit.date("Data");

        assertThat(campo).as("UiKit.date deve devolver um DatePicker").isNotNull();
        assertThat(campo.getLocale())
                .as("A-93: o campo de data deve usar o locale pt-BR")
                .isEqualTo(UiKit.PT_BR);

        DatePicker.DatePickerI18n i18n = campo.getI18n();
        assertThat(i18n)
                .as("A-93: UiKit.date deve configurar o i18n do DatePicker em pt-BR")
                .isNotNull();
        assertThat(i18n.getDateFormats())
                .as("A-93: o formato exibido/interpretado deve ser dd/MM/yyyy")
                .contains("dd/MM/yyyy");
    }
}
