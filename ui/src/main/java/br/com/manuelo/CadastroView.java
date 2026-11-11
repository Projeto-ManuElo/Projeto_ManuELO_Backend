package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("cadastro")
@PageTitle("Cadastro")
public class CadastroView extends AuthLayout {

    public CadastroView() {
        form.addClassName("auth-cadastro");
        titulo("Cadastre-se!", null);

        TextField nome = UiKit.text("Nome", "");
        EmailField email = UiKit.email("Email", "");
        PasswordField senha = UiKit.password("Senha", "");
        PasswordField confirmar = UiKit.password("Confirmar Senha", "");

        Button cadastrar = UiKit.primary("Cadastrar", e -> UiKit.ir("aprovacao"));
        cadastrar.addClassName("auth-submit");

        form.add(nome, email, senha, confirmar, cadastrar,
                link("Esqueci a senha", RecuperarSenhaView.class));
    }
}
