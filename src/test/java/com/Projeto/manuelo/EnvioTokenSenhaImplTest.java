package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import com.Projeto.manuelo.seguranca.EnvioTokenSenhaImpl;

/**
 * A-98/F01: com SMTP configurado, o envio do token de redefinição deve usar o
 * {@link JavaMailSender} e montar o link {@code /redefinir-senha?token=}.
 *
 * Teste puro de unidade (JUnit + Mockito), sem contexto Spring.
 */
class EnvioTokenSenhaImplTest {

    @Test
    void comHostConfiguradoEnviaEmailComLinkDeRedefinicao() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.mail.host", "smtp.qa.local");
        String host = environment.getProperty("spring.mail.host");
        EnvioTokenSenhaImpl envio = new EnvioTokenSenhaImpl(provider, environment,
                "http://localhost:8080", host, "no-reply@manuelo.local");

        String token = "TOKEN-" + UUID.randomUUID();
        envio.enviar("destinatario@manuelo.local", token);

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());

        SimpleMailMessage mensagem = captor.getValue();
        assertThat(mensagem.getTo()).containsExactly("destinatario@manuelo.local");
        assertThat(mensagem.getText())
                .as("o e-mail deve conter o link de redefinição com o token")
                .contains("/redefinir-senha?token=" + token);
    }
}
