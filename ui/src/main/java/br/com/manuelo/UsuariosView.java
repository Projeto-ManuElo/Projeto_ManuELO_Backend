package br.com.manuelo;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.Dominio;
import br.com.manuelo.dados.SessaoUsuario;
import br.com.manuelo.dados.UsuarioRepositorio;

@Route(value = "usuarios", layout = MainLayout.class)
@PageTitle("Usuários")
public class UsuariosView extends Div implements BeforeEnterObserver {

    private final UsuarioRepositorio repositorio;
    private final SessaoUsuario sessao;
    private final Div tabela = new Div();
    private List<UsuarioRepositorio.UsuarioLista> usuarios;

    public UsuariosView(UsuarioRepositorio repositorio, SessaoUsuario sessao) {
        this.repositorio = repositorio;
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
        usuarios = repositorio.listar();
        renderizar();
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-usuarios", "Nome", "Email", "Perfil", "Status", "Ações"));
        for (UsuarioRepositorio.UsuarioLista usuario : usuarios) {
            Div acoes = new Div();
            if (usuario.pendente()) {
                acoes.add(UiKit.secondary("Aprovar", e -> aprovar(usuario.id())));
            } else if ("ATIVO".equals(usuario.status()) && !usuario.id().equals(sessao.id())) {
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

    private void aprovar(String id) {
        try {
            repositorio.aprovar(id);
            usuarios = repositorio.listar();
            renderizar();
            UiKit.aviso("Usuário aprovado.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private void inativar(String id) {
        UiKit.confirmar("Inativar usuário?", "O usuário perderá o acesso ao sistema.", "Inativar", true, () -> {
            try {
                repositorio.inativar(id);
                usuarios = repositorio.listar();
                renderizar();
                UiKit.aviso("Usuário inativado.");
            } catch (RuntimeException ex) {
                UiKit.erro(ex.getMessage());
            }
        });
    }
}
