package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("redefinir-senha")
@PageTitle("Redefinir Senha")
public class RedefinirSenhaView extends AuthLayout {

    public RedefinirSenhaView() {
        titulo("Redefina sua Senha!", null);

        Div espaco = new Div();
        espaco.addClassName("auth-spacer-red");
        form.add(espaco);

        PasswordField novaSenha = UiKit.password("Nova Senha", "");
        PasswordField confirmar = UiKit.password("Confirmar Nova Senha", "");
        novaSenha.addClassName("auth-mb");
        Button redefinir = UiKit.primary("Redefinir", e -> UiKit.ir(""));
        redefinir.addClassName("auth-submit");
        redefinir.addClassName("auth-submit-red");

        form.add(novaSenha, confirmar, redefinir);
    }
}
