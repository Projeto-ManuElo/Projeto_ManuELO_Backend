package br.com.manuelo;

import java.util.List;
import java.util.UUID;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.Dominio;
import br.com.manuelo.dados.SessaoUsuario;

import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.seguranca.UsuarioAdminService;

@Route(value = "usuarios", layout = MainLayout.class)
@PageTitle("Usuários")
public class UsuariosView extends Div implements BeforeEnterObserver {

    private final UsuarioAdminService servico;
    private final SessaoUsuario sessao;
    private final Div tabela = new Div();
    private List<UsuarioAdminService.UsuarioLista> usuarios;

    public UsuariosView(UsuarioAdminService servico, SessaoUsuario sessao) {
        this.servico = servico;
        this.sessao = sessao;
        addClassName("page");
        add(UiKit.header("Usuários", "Aprove cadastros pendentes e gerencie o acesso ao sistema"));
        tabela.addClassName("table");
        add(UiKit.card(UiKit.cardHeader("Usuários cadastrados", null), tabela));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!sessao.administrador()) {
            event.rerouteTo("home");
            return;
        }
        usuarios = servico.listar();
        renderizar();
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-usuarios", "Nome", "Email", "Perfil", "Status", "Ações"));
        for (UsuarioAdminService.UsuarioLista usuario : usuarios) {
            Div acoes = new Div();
            if ("PENDENTE_APROVACAO".equals(usuario.status())) {
                acoes.add(UiKit.secondary("Aprovar", e -> aprovar(usuario.id(), usuario.nome())));
                acoes.add(UiKit.secondary("Reprovar", e -> reprovar(usuario.id(), usuario.nome())));
            } else if ("ATIVO".equals(usuario.status()) && !usuario.id().equals(sessao.id())) {
                acoes.add(UiKit.secondary("Alterar perfil",
                        e -> alterarPerfil(usuario.id(), usuario.nome(), usuario.perfil())));
                acoes.add(UiKit.secondary("Inativar", e -> inativar(usuario.id())));
            }
            tabela.add(UiKit.tableRow("cols-usuarios",
                    new Span(usuario.nome()),
                    UiKit.muted(usuario.email()),
                    UiKit.muted(Dominio.perfil(usuario.perfil())),
                    UiKit.badge(Dominio.situacaoUsuario(usuario.status())),
                    acoes));
        }
    }

    private void aprovar(String id, String nome) {
        UiKit.confirmar("Aprovar usuário?", "O usuário " + nome + " terá acesso ao sistema.", "Aprovar", true, () -> {
            try {
                servico.aprovar(UUID.fromString(id));
                usuarios = servico.listar();
                renderizar();
                UiKit.aviso("Usuário aprovado.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });
    }

    private void reprovar(String id, String nome) {
        UiKit.confirmar("Reprovar cadastro?", "O cadastro de " + nome + " será inativado.", "Reprovar", true, () -> {
            try {
                servico.inativar(UUID.fromString(id));
                usuarios = servico.listar();
                renderizar();
                UiKit.aviso("Cadastro reprovado.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });
    }

    private void inativar(String id) {
        UiKit.confirmar("Inativar usuário?", "O usuário perderá o acesso ao sistema.", "Inativar", true, () -> {
            try {
                servico.inativar(UUID.fromString(id));
                usuarios = servico.listar();
                renderizar();
                UiKit.aviso("Usuário inativado.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });
    }

    private void alterarPerfil(String id, String nome, String perfilAtual) {
        ComboBox<PerfilUsuario> perfil = new ComboBox<>("Perfil");
        perfil.setItems(PerfilUsuario.values());
        perfil.setItemLabelGenerator(item -> Dominio.perfil(item.name()));
        perfil.setValue(perfilAtual == null ? null : PerfilUsuario.valueOf(perfilAtual));
        perfil.setWidthFull();

        Dialog dialog = new Dialog();
        dialog.addClassName("modal");
        dialog.setWidth("520px");
        dialog.setMaxWidth("94vw");
        dialog.setCloseOnOutsideClick(false);

        Div conteudo = new Div();
        conteudo.addClassName("confirm-content");
        Span titulo = new Span("Alterar perfil");
        titulo.addClassName("confirm-title");
        Span texto = new Span("Escolha o novo perfil de " + nome + ".");
        texto.addClassName("confirm-text");
        conteudo.add(titulo, texto, perfil);

        Button cancelar = UiKit.secondary("Cancelar", e -> dialog.close());
        Button confirmar = UiKit.primary("Alterar perfil", e -> {
            PerfilUsuario escolhido = perfil.getValue();
            if (escolhido == null) {
                UiKit.erro("Selecione um perfil.");
                return;
            }
            dialog.close();
            try {
                servico.alterarPerfil(UUID.fromString(id), escolhido);
                usuarios = servico.listar();
                renderizar();
                UiKit.aviso("Perfil alterado.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });

        dialog.add(conteudo, UiKit.dialogFooter(cancelar, confirmar));
        dialog.open();
    }
}
