package com.Projeto.manuelo.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * A-13 (DEC-I05): SMTP quando {@code spring.mail.host} está configurado; em
 * desenvolvimento, loga o link; fora de desenvolvimento nunca loga o token.
 *
 * <p>P-032/F01: o boot e o envio registram um diagnóstico seguro (host/porta, sem
 * usuário/senha) para deixar explícito quando nenhum e-mail de redefinição sairá.</p>
 */
@Component
public class EnvioTokenSenhaImpl implements EnvioTokenSenha {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioTokenSenhaImpl.class);
    private static final int PORTA_PADRAO = 25;

    private final ObjectProvider<JavaMailSender> mailSender;
    private final Environment environment;
    private final String appUrl;
    private final String mailHost;
    private final String remetente;
    private final int mailPort;

    @Autowired
    public EnvioTokenSenhaImpl(ObjectProvider<JavaMailSender> mailSender, Environment environment,
            @Value("${manuelo.app.url:http://localhost:8080}") String appUrl,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String remetente,
            @Value("${spring.mail.port:25}") int mailPort) {
        this.mailSender = mailSender;
        this.environment = environment;
        this.appUrl = appUrl == null || appUrl.isBlank() ? "http://localhost:8080" : appUrl.trim();
        this.mailHost = mailHost;
        this.remetente = remetente == null || remetente.isBlank()
                ? "no-reply@manuelo.local" : remetente;
        this.mailPort = mailPort;
    }

    /**
     * Construtor mantido para uso direto em testes de unidade, sem contexto Spring.
     */
    public EnvioTokenSenhaImpl(ObjectProvider<JavaMailSender> mailSender, Environment environment,
            String appUrl, String mailHost, String remetente) {
        this(mailSender, environment, appUrl, mailHost, remetente, PORTA_PADRAO);
    }

    @PostConstruct
    void diagnosticarConfiguracao() {
        if (smtpConfigurado()) {
            LOG.info("SMTP configurado: host={}, porta={}", mailHost.trim(), mailPort);
            return;
        }
        if (emDesenvolvimento()) {
            LOG.info("SMTP não configurado; em dev o link de redefinição de senha será "
                    + "registrado no log.");
            return;
        }
        LOG.warn("SMTP não configurado: e-mails de redefinição de senha NÃO serão enviados. "
                + "Configure SPRING_MAIL_HOST no ambiente.");
    }

    @Override
    public void enviar(String email, String token) {
        String link = link(token);
        boolean smtpConfigurado = smtpConfigurado();
        JavaMailSender sender = smtpConfigurado ? mailSender.getIfAvailable() : null;
        if (sender != null) {
            LOG.info("SMTP configurado: enviando e-mail de redefinição via {}:{}",
                    mailHost.trim(), mailPort);
            enviarPorEmail(sender, email, link);
            return;
        }
        if (emDesenvolvimento()) {
            LOG.info("Link de redefinição de senha para {}: {}", mascararEmail(email), link);
            return;
        }
        if (smtpConfigurado) {
            LOG.warn("JavaMailSender indisponível para o host SMTP {}:{}; e-mail de "
                    + "redefinição NÃO enviado.", mailHost.trim(), mailPort);
            return;
        }
        LOG.warn("E-mail de redefinição NÃO enviado: SMTP não configurado. "
                + "Configure SPRING_MAIL_HOST no ambiente.");
    }

    private boolean smtpConfigurado() {
        return mailHost != null && !mailHost.isBlank();
    }

    private boolean emDesenvolvimento() {
        return environment.acceptsProfiles(Profiles.of("dev"));
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

    private String mascararEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(sem e-mail)";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
