package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.AutenticacaoService;

@Route("")
@PageTitle("Login")
public class LoginView extends AuthLayout {

    public LoginView(AutenticacaoService autenticacao) {
        form.addClassName("auth-login");
        titulo("Bem vindo!", null);

        Div espaco = new Div();
        espaco.addClassName("auth-spacer");

        EmailField email = UiKit.email("Email", "");
        PasswordField senha = UiKit.password("Senha", "");

        Button entrar = UiKit.primary("Login", e -> {
            boolean vazioEmail = email.getValue() == null || email.getValue().isBlank();
            boolean vazioSenha = senha.getValue() == null || senha.getValue().isBlank();
            email.setInvalid(vazioEmail);
            senha.setInvalid(vazioSenha);
            if (vazioEmail || vazioSenha) {
                UiKit.erro("Informe email e senha.");
                return;
            }
            switch (autenticacao.login(email.getValue(), senha.getValue())) {
                case OK -> UiKit.ir("home");
                case PENDENTE -> UiKit.ir("aprovacao");
                case INATIVO -> UiKit.erro("Usuário inativo. Procure o administrador.");
                case INVALIDO -> UiKit.erro("Email ou senha inválidos.");
            }
        });
        entrar.addClassName("auth-submit");

        form.add(espaco, email, senha, entrar, link("Esqueci a senha", RecuperarSenhaView.class));
    }
}
