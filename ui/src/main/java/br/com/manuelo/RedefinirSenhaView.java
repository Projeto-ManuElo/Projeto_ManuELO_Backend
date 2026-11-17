package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.Projeto.manuelo.seguranca.RecuperacaoSenhaService;

@Route("redefinir-senha")
@PageTitle("Redefinir Senha")
public class RedefinirSenhaView extends AuthLayout implements BeforeEnterObserver {

    private final RecuperacaoSenhaService recuperacao;
    private final Button redefinir;
    private String token;

    public RedefinirSenhaView(RecuperacaoSenhaService recuperacao) {
        this.recuperacao = recuperacao;
        titulo("Redefina sua Senha!", null);

        Div espaco = new Div();
        espaco.addClassName("auth-spacer-red");
        form.add(espaco);

        PasswordField novaSenha = UiKit.password("Nova Senha", "");
        PasswordField confirmar = UiKit.password("Confirmar Nova Senha", "");
        novaSenha.addClassName("auth-mb");
        redefinir = UiKit.primary("Redefinir", e -> redefinir(novaSenha, confirmar));
        redefinir.addClassName("auth-submit");
        redefinir.addClassName("auth-submit-red");

        form.add(novaSenha, confirmar, redefinir);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("token", java.util.List.of())
                .stream()
                .findFirst()
                .orElse(null);
        if (token == null || token.isBlank()) {
            token = null;
            UiKit.erro("Link inválido ou expirado. Solicite novamente.");
            redefinir.setEnabled(false);
        }
    }

    private void redefinir(PasswordField novaSenha, PasswordField confirmar) {
        if (token == null) {
            UiKit.erro("Link inválido ou expirado. Solicite novamente.");
            return;
        }
        boolean invalido = novaSenha.getValue() == null || novaSenha.getValue().length() < 8
                || !novaSenha.getValue().equals(confirmar.getValue());
        novaSenha.setInvalid(novaSenha.getValue() == null || novaSenha.getValue().length() < 8);
        confirmar.setInvalid(novaSenha.getValue() == null
                || !novaSenha.getValue().equals(confirmar.getValue()));
        if (invalido) {
            UiKit.erro("A senha deve ter ao menos 8 caracteres e ser igual à confirmação.");
            return;
        }
        if (recuperacao.redefinir(token, novaSenha.getValue()) == RecuperacaoSenhaService.Resultado.OK) {
            UiKit.aviso("Senha redefinida com sucesso.");
            UiKit.ir("");
        } else {
            UiKit.erro("Link inválido ou expirado. Solicite novamente.");
        }
    }
}
