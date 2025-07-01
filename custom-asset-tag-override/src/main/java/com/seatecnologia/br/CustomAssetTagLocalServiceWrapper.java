package com.seatecnologia.br;

import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalServiceWrapper;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceWrapper;

import java.lang.reflect.Field;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Service Wrapper customizado para permitir o caractere + em asset tags
 *
 * @author Nicollas Rezende
 */
@Component(
		immediate = true,
		property = {
				"service.ranking:Integer=100"
		},
		service = ServiceWrapper.class
)
public class CustomAssetTagLocalServiceWrapper extends AssetTagLocalServiceWrapper {

	private static final Log _log = LogFactoryUtil.getLog(CustomAssetTagLocalServiceWrapper.class);

	public CustomAssetTagLocalServiceWrapper() {
		super(null);
	}

	@Override
	public void setWrappedService(AssetTagLocalService assetTagLocalService) {
		super.setWrappedService(assetTagLocalService);

		_log.info("=== CustomAssetTagLocalServiceWrapper ATIVADO ===");

		try {
			// Modifica o array de caracteres inválidos via reflexão
			modifyInvalidCharactersArray();
		} catch (Exception e) {
			_log.error("Erro ao modificar array de caracteres inválidos", e);
		}
	}

	/**
	 * Modifica o array _INVALID_CHARACTERS removendo o caractere +
	 */
	private void modifyInvalidCharactersArray() {
		try {
			// Obtém a instância real do serviço
			AssetTagLocalService service = getWrappedService();
			Class<?> serviceClass = service.getClass();

			_log.info("Modificando array em: " + serviceClass.getName());

			// Procura o campo _INVALID_CHARACTERS
			Field field = null;
			Class<?> currentClass = serviceClass;

			while (currentClass != null) {
				try {
					field = currentClass.getDeclaredField("_INVALID_CHARACTERS");
					break;
				} catch (NoSuchFieldException e) {
					currentClass = currentClass.getSuperclass();
				}
			}

			if (field == null) {
				_log.error("Campo _INVALID_CHARACTERS não encontrado!");
				return;
			}

			// Torna o campo acessível
			field.setAccessible(true);

			// Obtém o array atual
			char[] oldArray = (char[]) field.get(null);
			_log.info("Array original tem " + oldArray.length + " caracteres");

			// Verifica se contém o +
			boolean hasPlus = false;
			for (char c : oldArray) {
				if (c == '+') {
					hasPlus = true;
					break;
				}
			}

			if (!hasPlus) {
				_log.info("Array já não contém o caractere +");
				return;
			}

			// Cria novo array sem o +
			char[] newArray = new char[oldArray.length - 1];
			int j = 0;
			for (char c : oldArray) {
				if (c != '+') {
					newArray[j++] = c;
				}
			}

			// Define o novo array
			field.set(null, newArray);

			_log.info("SUCESSO! Array modificado - caractere + removido!");
			_log.info("Novo array tem " + newArray.length + " caracteres");

		} catch (Exception e) {
			_log.error("Falha ao modificar array via reflexão", e);
			e.printStackTrace();
		}
	}
}