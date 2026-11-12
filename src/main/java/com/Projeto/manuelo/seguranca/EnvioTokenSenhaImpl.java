package com.Projeto.manuelo.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * A-13 (DEC-I05): SMTP quando {@code spring.mail.host} está configurado; em
 * desenvolvimento, loga o link; fora de desenvolvimento nunca loga o token.
 */
@Component
public class EnvioTokenSenhaImpl implements EnvioTokenSenha {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioTokenSenhaImpl.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final Environment environment;
    private final String appUrl;
    private final String mailHost;
    private final String remetente;

    public EnvioTokenSenhaImpl(ObjectProvider<JavaMailSender> mailSender, Environment environment,
            @Value("${manuelo.app.url:http://localhost:8080}") String appUrl,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String remetente) {
        this.mailSender = mailSender;
        this.environment = environment;
        this.appUrl = appUrl == null || appUrl.isBlank() ? "http://localhost:8080" : appUrl.trim();
        this.mailHost = mailHost;
        this.remetente = remetente == null || remetente.isBlank() ? "no-reply@manuelo.local" : remetente;
    }

    @Override
    public void enviar(String email, String token) {
        String link = link(token);
        JavaMailSender sender = mailHost != null && !mailHost.isBlank() ? mailSender.getIfAvailable() : null;
        if (sender != null) {
            enviarPorEmail(sender, email, link);
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            LOG.info("Link de redefinição de senha para {}: {}", email, link);
        }
    }

    private void enviarPorEmail(JavaMailSender sender, String email, String link) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(email);
        mensagem.setSubject("Redefinição de senha - ManuElo");
        mensagem.setText("""
                Olá,

                Recebemos um pedido para redefinir sua senha no ManuElo.
                Acesse o link abaixo (válido por 1 hora):

                %s

                Se você não solicitou, ignore este e-mail.
                """.formatted(link));
        sender.send(mensagem);
    }

    private String link(String token) {
        String base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        return base + "/redefinir-senha?token=" + token;
    }
}
