package com.seatecnologia.br;

import com.liferay.portal.kernel.events.ActionException;
import com.liferay.portal.kernel.events.LifecycleAction;
import com.liferay.portal.kernel.events.LifecycleEvent;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

import java.lang.reflect.Field;

import org.osgi.service.component.annotations.Component;

/**
 * StartupAction para modificar o array de caracteres inválidos no início
 */
@Component(
        immediate = true,
        property = "key=application.startup.events",
        service = LifecycleAction.class
)
public class CustomStartupAction implements LifecycleAction {

    private static final Log _log = LogFactoryUtil.getLog(CustomStartupAction.class);
    private static boolean _modified = false;

    @Override
    public void processLifecycleEvent(LifecycleEvent lifecycleEvent) throws ActionException {
        if (!_modified) {
            _log.info("=== CustomStartupAction executando ===");
            modifyInvalidCharactersArray();
        }
    }

    private synchronized void modifyInvalidCharactersArray() {
        if (_modified) {
            return;
        }

        try {
            // Carrega a classe diretamente
            ClassLoader portalClassLoader = com.liferay.portal.kernel.util.PortalClassLoaderUtil.getClassLoader();
            Class<?> implClass = portalClassLoader.loadClass("com.liferay.portlet.asset.service.impl.AssetTagLocalServiceImpl");

            _log.info("Classe carregada: " + implClass.getName());

            // Busca o campo _INVALID_CHARACTERS
            Field field = implClass.getDeclaredField("_INVALID_CHARACTERS");
            field.setAccessible(true);

            // Remove final
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            } catch (Exception e) {
                _log.debug("Não foi possível remover final modifier", e);
            }

            // Obtém o array
            char[] oldArray = (char[]) field.get(null);
            _log.info("Array original tem " + oldArray.length + " caracteres");

            // Remove o +
            int plusCount = 0;
            for (char c : oldArray) {
                if (c == '+') plusCount++;
            }

            if (plusCount == 0) {
                _log.info("Array já não contém +");
                _modified = true;
                return;
            }

            char[] newArray = new char[oldArray.length - plusCount];
            int j = 0;
            for (char c : oldArray) {
                if (c != '+') {
                    newArray[j++] = c;
                }
            }

            // Define o novo array
            field.set(null, newArray);
            _modified = true;

            _log.info("======================================");
            _log.info("SUCESSO! StartupAction modificou o array!");
            _log.info("Caractere + removido dos caracteres inválidos");
            _log.info("======================================");

        } catch (Exception e) {
            _log.error("Erro ao modificar array no startup", e);
        }
    }
}