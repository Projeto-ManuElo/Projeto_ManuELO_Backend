package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("Login")
public class LoginView extends AuthLayout {

    public LoginView() {
        form.addClassName("auth-login");
        titulo("Bem vindo!", null);

        Div espaco = new Div();
        espaco.addClassName("auth-spacer");

        EmailField email = UiKit.email("Email", "");
        PasswordField senha = UiKit.password("Senha", "");

        Button entrar = UiKit.primary("Login", e -> UiKit.ir("home"));
        entrar.addClassName("auth-submit");

        form.add(espaco, email, senha, entrar, link("Esqueci a senha", RecuperarSenhaView.class));
    }
}
