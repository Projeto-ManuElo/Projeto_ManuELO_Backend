package com.Projeto.manuelo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * A-80: handler global de erros das telas Vaadin. Registra um {@link ErrorHandler}
 * por sessão para logar a falha e mostrar uma notificação pt-BR, sem tela branca
 * nem stack trace para o usuário. A autorização continua nos serviços/rotas.
 */
@Component
public class ErrosGlobalConfig implements VaadinServiceInitListener {

    private static final Logger log = LoggerFactory.getLogger(ErrosGlobalConfig.class);

    private static final String MENSAGEM_ACESSO = "Sem permissão para esta ação.";
    private static final String MENSAGEM_GENERICA =
            "Não foi possível concluir a operação. Tente novamente.";

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInitEvent ->
                sessionInitEvent.getSession().setErrorHandler(this::tratar));
    }

    private void tratar(ErrorEvent event) {
        Throwable erro = event.getThrowable();
        log.error("Erro não tratado na interface", erro);

        String mensagem = semPermissao(erro) ? MENSAGEM_ACESSO : MENSAGEM_GENERICA;
        UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        ui.access(() -> {
            Notification notification =
                    Notification.show(mensagem, 5000, Notification.Position.BOTTOM_END);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
    }

    private boolean semPermissao(Throwable erro) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            if (atual instanceof AccessDeniedException) {
                return true;
            }
        }
        return false;
    }
}
