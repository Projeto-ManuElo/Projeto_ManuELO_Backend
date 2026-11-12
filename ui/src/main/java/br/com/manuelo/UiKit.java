package br.com.manuelo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class UiKit {

    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd MMM", PT_BR);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(PT_BR);

    private UiKit() {
    }

    public static Div page(Component... children) {
        Div page = new Div(children);
        page.addClassName("page");
        return page;
    }

    public static Div header(String titulo, String subtitulo, Component... acoes) {
        Div textos = new Div();
        textos.addClassName("page-heading");
        H2 h = new H2(titulo);
        h.addClassName("page-title");
        textos.add(h);
        if (subtitulo != null && !subtitulo.isBlank()) {
            Paragraph p = new Paragraph(subtitulo);
            p.addClassName("page-subtitle");
            textos.add(p);
        }
        Div header = new Div(textos);
        header.addClassName("page-header");
        if (acoes.length > 0) {
            Div direita = new Div(acoes);
            direita.addClassName("page-actions");
            header.add(direita);
        }
        return header;
    }

    public static Div card(Component... children) {
        Div card = new Div(children);
        card.addClassName("card");
        return card;
    }

    public static Div cardHeader(String titulo, String subtitulo, Component... acoes) {
        Div textos = new Div();
        textos.addClassName("card-heading");
        H3 h = new H3(titulo);
        h.addClassName("card-title");
        textos.add(h);
        if (subtitulo != null && !subtitulo.isBlank()) {
            Span s = new Span(subtitulo);
            s.addClassName("card-subtitle");
            textos.add(s);
        }
        Div header = new Div(textos);
        header.addClassName("card-header");
        if (acoes.length > 0) {
            Div direita = new Div(acoes);
            direita.addClassName("card-actions");
            header.add(direita);
        }
        return header;
    }

    public static Div cardHeaderComSubtitulo(String titulo, Span subtitulo, Component... acoes) {
        Div textos = new Div();
        textos.addClassName("card-heading");
        H3 h = new H3(titulo);
        h.addClassName("card-title");
        textos.add(h);
        if (subtitulo != null) {
            subtitulo.addClassName("card-subtitle");
            textos.add(subtitulo);
        }
        Div header = new Div(textos);
        header.addClassName("card-header");
        if (acoes.length > 0) {
            Div direita = new Div(acoes);
            direita.addClassName("card-actions");
            header.add(direita);
        }
        return header;
    }

    public static Div row(Component... children) {
        Div row = new Div(children);
        row.addClassName("row");
        return row;
    }

    public static Div stack(Component... children) {
        Div stack = new Div(children);
        stack.addClassName("stack");
        return stack;
    }

    public static Div formGrid(Component... children) {
        Div grid = new Div(children);
        grid.addClassName("form-grid");
        return grid;
    }

    public static Div field(Component... children) {
        Div col = new Div(children);
        col.addClassName("field-col");
        return col;
    }

    public static Div kpi(String rotulo, String valor, String cor, VaadinIcon icone, String rota) {
        return kpi(rotulo, valor, cor, icone, rota, null, null);
    }

    public static Div kpi(String rotulo, String valor, String cor, VaadinIcon icone, String rota,
            String subtexto, String corSubtexto) {
        Div label = new Div(rotulo);
        label.addClassName("kpi-label");
        Div number = new Div(valor);
        number.addClassName("kpi-value");
        Div conteudo = new Div(label, number);
        conteudo.addClassName("kpi-content");
        if (subtexto != null && !subtexto.isBlank()) {
            Div sub = new Div(subtexto);
            sub.addClassName("kpi-sub");
            if (corSubtexto != null) {
                sub.getStyle().set("color", corSubtexto);
            }
            conteudo.add(sub);
        }

        Div iconeBox = new Div(icone.create());
        iconeBox.addClassName("kpi-icon");

        Div card = new Div(conteudo, iconeBox);
        card.addClassName("kpi");
        card.getStyle().set("--kpi-color", cor);
        card.addClickListener(e -> ir(rota));
        return card;
    }

    public static Div tableHead(String colsClass, String... headers) {
        Div head = new Div();
        head.addClassName("table-head");
        head.addClassName(colsClass);
        for (String header : headers) {
            head.add(new Span(header));
        }
        return head;
    }

    public static Div tableRow(String colsClass, Component... cells) {
        Div row = new Div(cells);
        row.addClassName("table-row");
        row.addClassName(colsClass);
        return row;
    }

    public static Span muted(String texto) {
        Span span = new Span(texto);
        span.addClassName("muted");
        return span;
    }

    public static Span strong(String texto) {
        Span span = new Span(texto);
        span.addClassName("strong");
        return span;
    }

    public static Span badge(String texto) {
        String variante = switch (texto) {
            case "Alugado", "Entrada", "Ativo", "Pago", "Concluído", "Aprovado", "Contrato", "Comprovante" -> "success";
            case "Vago", "A vencer", "Pendente", "Em análise", "Aguardando", "Manutenção" -> "warning";
            case "Em manutenção", "Atrasado", "Saída", "Vencido", "Inativo", "Rejeitado", "Tributos" -> "danger";
            case "Laudo" -> "info";
            default -> "neutral";
        };
        return badge(texto, variante);
    }

    public static Span badge(String texto, String variante) {
        Span span = new Span(texto);
        span.addClassName("badge");
        span.addClassName("badge-" + variante);
        return span;
    }

    public static Span money(BigDecimal valor, boolean comSinal) {
        return money(valor, comSinal, false);
    }

    public static Span money(BigDecimal valor, boolean comSinal, boolean neutro) {
        String texto = MOEDA.format(valor.abs());
        if (texto.endsWith(",00")) {
            texto = texto.substring(0, texto.length() - 3);
        }
        if (comSinal) {
            texto = (valor.signum() >= 0 ? "+ " : "- ") + texto;
        } else if (valor.signum() < 0) {
            texto = "- " + texto;
        }
        Span span = new Span(texto);
        if (neutro) {
            span.addClassName("money-neutral");
        } else {
            span.addClassName(valor.signum() >= 0 ? "money-in" : "money-out");
        }
        return span;
    }

    public static String data(LocalDate data) {
        return data == null ? "" : DATA.format(data);
    }

    public static String diaMes(LocalDate data) {
        return data == null ? "" : DIA_MES.format(data).replace(".", "");
    }

    public static Button primary(String texto, ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(texto, listener);
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        button.addClassName("btn-primary");
        return button;
    }

    public static Button primaryIcon(String texto, VaadinIcon icone,
            ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = primary(texto, listener);
        button.setIcon(icone.create());
        return button;
    }

    public static Button secondary(String texto, ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(texto, listener);
        button.addClassName("btn-secondary");
        return button;
    }

    public static Button tertiary(String texto, ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(texto, listener);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClassName("btn-tertiary");
        return button;
    }

    public static Button iconButton(VaadinIcon icone, String ariaLabel,
            ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Button button = new Button(icone.create(), listener);
        button.addClassName("icon-button");
        button.getElement().setAttribute("aria-label", ariaLabel);
        return button;
    }

    public static TextField text(String label, String placeholder) {
        TextField field = new TextField(label);
        field.setPlaceholder(placeholder);
        field.setWidthFull();
        return field;
    }

    public static TextField text(String label) {
        TextField field = new TextField(label);
        field.setWidthFull();
        return field;
    }

    public static EmailField email(String label, String placeholder) {
        EmailField field = new EmailField(label);
        field.setPlaceholder(placeholder);
        field.setWidthFull();
        return field;
    }

    public static PasswordField password(String label, String placeholder) {
        PasswordField field = new PasswordField(label);
        field.setPlaceholder(placeholder);
        field.setWidthFull();
        return field;
    }

    public static TextArea textArea(String label, String placeholder) {
        TextArea field = new TextArea(label);
        field.setPlaceholder(placeholder);
        field.setWidthFull();
        return field;
    }

    public static DatePicker date(String label) {
        DatePicker field = new DatePicker(label);
        field.setPlaceholder("dd/mm/aaaa");
        field.setWidthFull();
        return field;
    }

    public static ComboBox<String> select(String label, String placeholder, Collection<String> itens) {
        ComboBox<String> field = new ComboBox<>(label);
        field.setItems(itens);
        field.setPlaceholder(placeholder);
        field.setWidthFull();
        return field;
    }

    public static Upload upload(String titulo, String dica) {
        Upload upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
        }));
        upload.setAcceptedMimeTypes("application/pdf", "image/png", "image/jpeg");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(10 * 1024 * 1024);
        upload.addClassName("upload-box");

        Span tituloSpan = new Span(titulo);
        tituloSpan.addClassName("upload-title");
        Span dicaSpan = new Span(dica);
        dicaSpan.addClassName("upload-hint");
        Div texto = new Div(tituloSpan, dicaSpan);
        texto.addClassName("upload-copy");
        Div label = new Div(texto);
        label.addClassName("upload-label");
        upload.setDropLabel(label);
        return upload;
    }

    public static Image logo(String width) {
        Image image = new Image("/images/logo.png", "Fundação Manoel Cruz");
        image.setWidth(width);
        image.addClassName("logo-image");
        return image;
    }

    public static Div dialogHeader(String titulo, String subtitulo) {
        Div textos = new Div();
        textos.addClassName("modal-heading");
        H3 h = new H3(titulo);
        h.addClassName("modal-title");
        textos.add(h);
        if (subtitulo != null && !subtitulo.isBlank()) {
            Span s = new Span(subtitulo);
            s.addClassName("modal-subtitle");
            textos.add(s);
        }
        Div header = new Div(textos);
        header.addClassName("modal-header");
        return header;
    }

    public static Div dialogFooter(Component... children) {
        Div footer = new Div(children);
        footer.addClassName("modal-footer");
        return footer;
    }

    public static Dialog modal(String titulo, String subtitulo, Component conteudo, Component rodape) {
        Dialog dialog = new Dialog();
        dialog.addClassName("modal");
        dialog.setWidth("620px");
        dialog.setMaxWidth("94vw");
        dialog.setMaxHeight("92vh");
        dialog.setCloseOnOutsideClick(false);
        dialog.setDraggable(true);
        dialog.setResizable(false);

        Div header = dialogHeader(titulo, subtitulo);
        Button fechar = iconButton(VaadinIcon.CLOSE, "Fechar", e -> dialog.close());
        header.add(fechar);

        Div body = new Div(conteudo);
        body.addClassName("modal-body");

        dialog.add(header, body);
        if (rodape != null) {
            dialog.add(rodape);
        }
        return dialog;
    }

    public static void confirmar(String titulo, String texto, String acao, Runnable aoConfirmar) {
        confirmar(titulo, texto, acao, false, aoConfirmar);
    }

    public static void confirmar(String titulo, String texto, String acao, boolean sucesso, Runnable aoConfirmar) {
        Dialog dialog = new Dialog();
        dialog.addClassName("modal");
        dialog.setWidth("520px");
        dialog.setMaxWidth("94vw");
        dialog.setCloseOnOutsideClick(false);

        Div conteudo = new Div();
        conteudo.addClassName("confirm-content");
        Div icone = new Div(VaadinIcon.CHECK.create());
        icone.addClassName("confirm-icon");
        if (sucesso) {
            icone.addClassName("success");
        }
        Span tituloSpan = new Span(titulo);
        tituloSpan.addClassName("confirm-title");
        Span textoSpan = new Span(texto);
        textoSpan.addClassName("confirm-text");
        conteudo.add(icone, tituloSpan, textoSpan);

        Button cancelar = secondary("Cancelar", e -> dialog.close());
        Button confirmar = primary(acao, e -> {
            dialog.close();
            aoConfirmar.run();
        });
        dialog.add(conteudo, dialogFooter(cancelar, confirmar));
        dialog.open();
    }

    public static void aviso(String mensagem) {
        Notification notification = Notification.show(mensagem, 2800, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void erro(String mensagem) {
        String texto = mensagem == null || mensagem.isBlank()
                ? "Não foi possível concluir a operação."
                : mensagem;
        if (texto.length() > 220) {
            texto = texto.substring(0, 217) + "...";
        }
        Notification notification = Notification.show(texto, 5000, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    public static void ir(String rota) {
        UI.getCurrent().navigate(rota);
    }

    public static Div emptyState(String titulo, String texto) {
        Div empty = new Div();
        empty.addClassName("empty-state");
        Icon icon = VaadinIcon.INBOX.create();
        icon.addClassName("empty-icon");
        Span t = new Span(titulo);
        t.addClassName("empty-title");
        Span s = new Span(texto);
        s.addClassName("empty-text");
        empty.add(icon, t, s);
        return empty;
    }

    public static Div listOf(Component... children) {
        Div div = new Div(children);
        div.addClassName("vertical-list");
        return div;
    }

    public static Div spacer() {
        Div spacer = new Div();
        spacer.addClassName("spacer");
        return spacer;
    }
}
