package br.com.manuelo;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;

import com.Projeto.manuelo.servico.RelatorioService;

public class ExportarExtratoDialog extends Dialog {

    private static final List<String> TIPOS = List.of("Todos", "Entrada", "Saída");

    private final RelatorioService relatorios;
    private final DatePicker inicio = UiKit.date("Data inicial");
    private final DatePicker fim = UiKit.date("Data final");
    private final ComboBox<String> tipo = UiKit.select("Tipo", "Todos", TIPOS);

    public ExportarExtratoDialog(RelatorioService relatorios, LocalDate inicioPadrao,
            LocalDate fimPadrao, String tipoPadrao) {
        this.relatorios = relatorios;

        addClassName("modal");
        setWidth("560px");
        setMaxWidth("94vw");
        setCloseOnOutsideClick(false);

        LocalDate hoje = LocalDate.now();
        inicio.setValue(inicioPadrao == null ? hoje.withDayOfMonth(1) : inicioPadrao);
        fim.setValue(fimPadrao == null ? hoje : fimPadrao);
        tipo.setValue(tipoPadrao == null || tipoPadrao.isBlank() ? "Todos" : tipoPadrao);

        Div header = UiKit.dialogHeader("Exportar extrato",
                "O período e o filtro escolhidos serão usados no PDF do relatório.");
        header.add(UiKit.iconButton(VaadinIcon.CLOSE, "Fechar", e -> close()));

        Div body = new Div(UiKit.formGrid(inicio, fim, tipo));
        body.addClassName("modal-body");

        Button cancelar = UiKit.secondary("Cancelar", e -> close());
        Button exportar = UiKit.primary("Exportar PDF", e -> exportar());

        add(header, body, UiKit.dialogFooter(cancelar, exportar));
    }

    private void exportar() {
        LocalDate dataInicio = inicio.getValue();
        LocalDate dataFim = fim.getValue();
        if (!validarPeriodo(dataInicio, dataFim)) {
            return;
        }
        try {
            String id = relatorios.gerar("Movimentação financeira", dataInicio, dataFim,
                    tipo.getValue());
            baixar(relatorios.baixar(id));
            close();
            UiKit.aviso("Extrato exportado em PDF.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private boolean validarPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null) {
            inicio.setInvalid(true);
            UiKit.erro("Informe a data inicial do período.");
            return false;
        }
        if (dataFim == null) {
            fim.setInvalid(true);
            UiKit.erro("Informe a data final do período.");
            return false;
        }
        if (dataInicio.isAfter(dataFim)) {
            inicio.setInvalid(true);
            fim.setInvalid(true);
            UiKit.erro("A data inicial não pode ser posterior à data final.");
            return false;
        }
        inicio.setInvalid(false);
        fim.setInvalid(false);
        return true;
    }

    private void baixar(RelatorioService.Arquivo arquivo) {
        StreamResource resource = new StreamResource(arquivo.nome(),
                () -> new ByteArrayInputStream(arquivo.conteudo()));
        resource.setContentType(arquivo.mime());
        resource.setHeader("Content-Disposition",
                "attachment; filename=\"" + arquivo.nome().replace("\"", "") + "\"");
        StreamRegistration registro = UI.getCurrent().getSession().getResourceRegistry()
                .registerResource(resource);
        UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
    }
}
