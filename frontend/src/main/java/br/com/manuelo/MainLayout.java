package br.com.manuelo;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;

import java.util.LinkedHashMap;
import java.util.Map;

public class MainLayout extends HorizontalLayout implements RouterLayout, AfterNavigationObserver {

    private final Map<String, Anchor> links = new LinkedHashMap<>();

    public MainLayout() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        Span titulo = new Span("ManuElo");
        titulo.getStyle().set("color", "white").set("font-weight", "bold");
        Span sub = new Span("Fundação Manoel Cruz");
        sub.getStyle().set("color", "#aab").set("font-size", "0.7rem");
        VerticalLayout cabecalho = new VerticalLayout(titulo, sub);
        cabecalho.setPadding(false);
        cabecalho.setSpacing(false);

        Avatar avatar = new Avatar("Davi Ferreira");
        Span nome = new Span("Davi Ferreira");
        nome.getStyle().set("color", "white").set("font-size", "0.85rem");
        Span cargo = new Span("Administrador");
        cargo.getStyle().set("color", "#aab").set("font-size", "0.7rem");
        VerticalLayout dados = new VerticalLayout(nome, cargo);
        dados.setPadding(false);
        dados.setSpacing(false);
        HorizontalLayout usuario = new HorizontalLayout(avatar, dados);
        usuario.getStyle().set("background", "#252a55").set("border-radius", "8px").set("padding", "10px");

        VerticalLayout menu = new VerticalLayout(
                cabecalho,
                secao("PRINCIPAL"),
                item(VaadinIcon.HOME, "Visão geral", "home"),
                item(VaadinIcon.BUILDING, "Imóveis", "imoveis"),
                item(VaadinIcon.USERS, "Inquilinos", "inquilinos"),
                item(VaadinIcon.FILE_TEXT, "Contratos", "contratos"),
                item(VaadinIcon.MONEY, "Movimentação Financeira", "financeiro"),
                secao("GESTÃO DOCUMENTAL"),
                item(VaadinIcon.FOLDER, "Documentos", "documentos"),
                item(VaadinIcon.BAR_CHART, "Relatórios", "relatorios"));
        menu.setSizeFull();
        menu.setSpacing(false);

        VerticalLayout lateral = new VerticalLayout(menu, usuario);
        lateral.setWidth("220px");
        lateral.setHeight("100%");
        lateral.setPadding(true);
        lateral.getStyle().set("background", "#1a1f44").set("flex-shrink", "0");

        add(lateral);
    }

    private Span secao(String texto) {
        Span s = new Span(texto);
        s.getStyle().set("color", "#889").set("font-size", "0.65rem").set("margin-top", "16px");
        return s;
    }

    private Anchor item(VaadinIcon icone, String texto, String rota) {
        Icon i = icone.create();
        i.setSize("16px");
        Anchor a = new Anchor(rota, i, new Span(texto));
        a.getStyle()
                .set("display", "flex").set("align-items", "center").set("gap", "10px")
                .set("padding", "10px").set("border-radius", "8px")
                .set("color", "white").set("text-decoration", "none").set("font-size", "0.85rem");
        links.put(rota, a);
        return a;
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String atual = event.getLocation().getFirstSegment();
        links.forEach((rota, a) ->
                a.getStyle().set("background", rota.equals(atual) ? "#5b5bf0" : "transparent"));
    }
}