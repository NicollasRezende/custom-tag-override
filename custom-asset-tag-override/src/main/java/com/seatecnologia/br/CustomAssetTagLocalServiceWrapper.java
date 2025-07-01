package com.seatecnologia.br;

import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalServiceWrapper;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Activate;

/**
 * Service Wrapper customizado para permitir o caractere + em asset tags
 *
 * @author Nicollas Rezende
 */
@Component(
		immediate = true,
		property = {
				"service.ranking:Integer=2000"
		},
		service = ServiceWrapper.class
)
public class CustomAssetTagLocalServiceWrapper extends AssetTagLocalServiceWrapper {

	private static final Log _log = LogFactoryUtil.getLog(CustomAssetTagLocalServiceWrapper.class);
	private static volatile boolean _arrayModified = false;

	public CustomAssetTagLocalServiceWrapper() {
		super(null);
		_log.info("CustomAssetTagLocalServiceWrapper construtor chamado");
	}

	@Activate
	protected void activate() {
		_log.info("=== CustomAssetTagLocalServiceWrapper ATIVADO ===");
		// Agenda a modificação para depois
		Thread modifierThread = new Thread(() -> {
			try {
				Thread.sleep(5000); // Aguarda 5 segundos
				modifyInvalidCharactersArray();
			} catch (Exception e) {
				_log.error("Erro no thread de modificação", e);
			}
		});
		modifierThread.setDaemon(true);
		modifierThread.start();
	}

	@Override
	public void setWrappedService(AssetTagLocalService assetTagLocalService) {
		super.setWrappedService(assetTagLocalService);
		_log.info("Wrapped service definido: " + assetTagLocalService.getClass().getName());
		modifyInvalidCharactersArray();
	}

	@Override
	public AssetTag addTag(long userId, long groupId, String name,
						   ServiceContext serviceContext) throws PortalException {

		_log.info("=== addTag interceptado: " + name);

		// Se tem +, usa estratégia de substituição
		if (name != null && name.contains("+")) {
			// Cria com nome temporário
			String tempName = name.replace("+", "_PLUS_TEMP_");

			try {
				AssetTag tag = super.addTag(userId, groupId, tempName, serviceContext);

				// Atualiza para o nome real
				return updateTag(userId, tag.getTagId(), name, serviceContext);

			} catch (Exception e) {
				_log.error("Erro ao criar tag com +: " + e.getMessage());
				// Se falhar, tenta sem o +
				String safeName = name.replace("+", " e ");
				return super.addTag(userId, groupId, safeName, serviceContext);
			}
		}

		return super.addTag(userId, groupId, name, serviceContext);
	}

	@Override
	public AssetTag updateTag(long userId, long tagId, String name,
							  ServiceContext serviceContext) throws PortalException {

		_log.info("=== updateTag interceptado: " + name);

		ensureArrayModified();

		// Se tem +, tenta bypass
		if (name != null && name.contains("+") && !_arrayModified) {
			AssetTag tag = getAssetTag(tagId);
			String tempName = name.replace("+", "_PLUS_");
			AssetTag updatedTag = super.updateTag(userId, tagId, tempName, serviceContext);

			// Atualiza o nome real depois
			try {
				Field nameField = updatedTag.getClass().getDeclaredField("_name");
				nameField.setAccessible(true);
				nameField.set(updatedTag, name);

				// Força update no banco
				Method update = getWrappedService().getClass().getMethod("updateAssetTag", AssetTag.class);
				return (AssetTag) update.invoke(getWrappedService(), updatedTag);
			} catch (Exception e) {
				_log.error("Erro ao atualizar nome com +", e);
			}
		}

		return super.updateTag(userId, tagId, name, serviceContext);
	}

	/**
	 * Adiciona tag com + usando bypass de validação
	 */
	private AssetTag addTagWithBypass(long userId, long groupId, String name,
									  ServiceContext serviceContext) throws PortalException {

		_log.info("Tentando criar tag com bypass para: " + name);

		// Primeiro cria com nome temporário
		String tempName = name.replace("+", "_PLUS_");
		AssetTag tag = super.addTag(userId, groupId, tempName, serviceContext);

		// Depois atualiza o nome
		try {
			Field nameField = tag.getClass().getDeclaredField("_name");
			nameField.setAccessible(true);
			nameField.set(tag, name.trim());

			// Força a atualização no banco
			Method update = getWrappedService().getClass().getMethod("updateAssetTag", AssetTag.class);
			tag = (AssetTag) update.invoke(getWrappedService(), tag);

			_log.info("Tag criada com sucesso via bypass: " + tag.getName());
			return tag;

		} catch (Exception e) {
			_log.error("Erro no bypass, retornando tag temporária", e);
			return tag;
		}
	}

	/**
	 * Garante que tentamos modificar o array
	 */
	private void ensureArrayModified() {
		if (!_arrayModified) {
			modifyInvalidCharactersArray();
		}
	}

	/**
	 * Modifica o array _INVALID_CHARACTERS removendo o caractere +
	 */
	private synchronized void modifyInvalidCharactersArray() {
		if (_arrayModified) {
			return;
		}

		try {
			AssetTagLocalService service = getWrappedService();
			if (service == null) {
				_log.warn("Wrapped service ainda é null");
				return;
			}

			// Navega pela cadeia de wrappers até encontrar a implementação
			Object currentService = service;
			Class<?> currentClass = currentService.getClass();

			_log.info("Procurando AssetTagLocalServiceImpl a partir de: " + currentClass.getName());

			// Se é um proxy, tenta obter o handler
			if (Proxy.isProxyClass(currentClass)) {
				try {
					InvocationHandler handler = Proxy.getInvocationHandler(currentService);
					_log.info("É um proxy, handler: " + handler.getClass().getName());

					// Procura campos que possam conter o serviço real
					Field[] fields = handler.getClass().getDeclaredFields();
					for (Field f : fields) {
						f.setAccessible(true);
						Object value = f.get(handler);
						if (value != null && value.getClass().getName().contains("AssetTag")) {
							currentService = value;
							currentClass = value.getClass();
							_log.info("Encontrado serviço via campo: " + f.getName());
							break;
						}
					}
				} catch (Exception e) {
					_log.warn("Erro ao processar proxy", e);
				}
			}

			// Se é um wrapper, tenta obter o wrapped
			while (currentClass.getName().contains("Wrapper") && !currentClass.getName().contains("AssetTagLocalServiceImpl")) {
				try {
					Method getWrapped = currentClass.getMethod("getWrappedService");
					currentService = getWrapped.invoke(currentService);
					currentClass = currentService.getClass();
					_log.info("Navegando wrapper: " + currentClass.getName());
				} catch (Exception e) {
					break;
				}
			}

			// Procura AssetTagLocalServiceImpl na hierarquia
			Class<?> targetClass = currentClass;
			while (targetClass != null) {
				_log.info("Verificando classe: " + targetClass.getName());
				if (targetClass.getName().endsWith("AssetTagLocalServiceImpl")) {
					break;
				}
				targetClass = targetClass.getSuperclass();
			}

			if (targetClass == null || !targetClass.getName().endsWith("AssetTagLocalServiceImpl")) {
				_log.error("AssetTagLocalServiceImpl não encontrado!");
				return;
			}

			_log.info("Classe alvo encontrada: " + targetClass.getName());

			// Busca o campo _INVALID_CHARACTERS
			Field field = targetClass.getDeclaredField("_INVALID_CHARACTERS");
			field.setAccessible(true);

			// Remove o modifier final
			try {
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
			} catch (Exception e) {
				_log.warn("Não foi possível remover modifier final (Java 9+?)", e);
			}

			// Obtém o array atual
			char[] oldArray = (char[]) field.get(null);
			_log.info("Array original tem " + oldArray.length + " caracteres");

			// Verifica se tem +
			int plusIndex = -1;
			for (int i = 0; i < oldArray.length; i++) {
				if (oldArray[i] == '+') {
					plusIndex = i;
					break;
				}
			}

			if (plusIndex == -1) {
				_log.info("Array já não contém o caractere +");
				_arrayModified = true;
				return;
			}

			// Cria novo array sem o +
			char[] newArray = new char[oldArray.length - 1];
			System.arraycopy(oldArray, 0, newArray, 0, plusIndex);
			System.arraycopy(oldArray, plusIndex + 1, newArray, plusIndex, oldArray.length - plusIndex - 1);

			// Define o novo array
			field.set(null, newArray);
			_arrayModified = true;

			_log.info("======================================");
			_log.info("SUCESSO! Array _INVALID_CHARACTERS modificado!");
			_log.info("Caractere + foi REMOVIDO da lista de inválidos");
			_log.info("Novo array tem " + newArray.length + " caracteres");
			_log.info("======================================");

		} catch (Exception e) {
			_log.error("Falha ao modificar array via reflexão", e);
			_log.error("Detalhes: ", e);
		}
	}
}