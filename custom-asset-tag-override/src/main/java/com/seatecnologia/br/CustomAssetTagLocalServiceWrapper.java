package com.seatecnologia.br;

import com.liferay.asset.kernel.exception.AssetTagException;
import com.liferay.asset.kernel.exception.AssetTagNameException;
import com.liferay.asset.kernel.exception.DuplicateTagException;
import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalServiceWrapper;
import com.liferay.petra.string.CharPool;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.ModelHintsUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceWrapper;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import org.osgi.service.component.annotations.Component;

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

	private static final Log _log = LogFactoryUtil.getLog(
			CustomAssetTagLocalServiceWrapper.class);

	// Lista de caracteres inválidos SEM o CharPool.PLUS
	private static final char[] _CUSTOM_INVALID_CHARACTERS = {
			CharPool.AMPERSAND, CharPool.APOSTROPHE, CharPool.AT,
			CharPool.BACK_SLASH, CharPool.CLOSE_BRACKET, CharPool.CLOSE_CURLY_BRACE,
			CharPool.COLON, CharPool.COMMA, CharPool.EQUAL, CharPool.GREATER_THAN,
			CharPool.FORWARD_SLASH, CharPool.LESS_THAN, CharPool.NEW_LINE,
			CharPool.OPEN_BRACKET, CharPool.OPEN_CURLY_BRACE, CharPool.PERCENT,
			CharPool.PIPE, CharPool.POUND, CharPool.PRIME,
			CharPool.QUESTION, CharPool.QUOTE, CharPool.RETURN, CharPool.SEMICOLON,
			CharPool.SLASH, CharPool.STAR, CharPool.TILDE
	};

	public CustomAssetTagLocalServiceWrapper() {
		super(null);
	}

	public CustomAssetTagLocalServiceWrapper(
			AssetTagLocalService assetTagLocalService) {
		super(assetTagLocalService);
	}

	@Override
	public AssetTag addTag(
			long userId, long groupId, String name,
			ServiceContext serviceContext)
			throws PortalException {

		_log.info("CustomAssetTagLocalServiceWrapper.addTag - name: " + name);

		// Trim
		if (name != null) {
			name = name.trim();
		}

		// Nossa validação customizada
		validateCustom(name);

		// Verifica duplicação
		if (hasTag(groupId, name)) {
			throw new DuplicateTagException(
					"A tag with the name " + name + " already exists");
		}

		// Tenta criar usando a implementação padrão mas sem a validação
		try {
			// Cria um nome temporário que passa na validação
			String tempName = name.replace("+", "TEMPPLUS");

			// Cria a tag com nome temporário
			AssetTag tag = super.addTag(userId, groupId, tempName, serviceContext);

			// Atualiza para o nome real
			tag.setName(name);
			tag = super.updateAssetTag(tag);

			_log.info("Tag criada com sucesso: " + name);

			return tag;

		} catch (Exception e) {
			_log.error("Erro ao criar tag: " + e.getMessage(), e);
			throw new PortalException("Erro ao criar tag", e);
		}
	}

	@Override
	public AssetTag updateTag(
			long userId, long tagId, String name,
			ServiceContext serviceContext)
			throws PortalException {

		_log.info("CustomAssetTagLocalServiceWrapper.updateTag - name: " + name);

		// Busca a tag
		AssetTag tag = getAssetTag(tagId);
		String oldName = tag.getName();

		if (name != null) {
			name = name.trim();
		}

		// Nossa validação customizada
		validateCustom(name);

		// Verifica duplicação se o nome mudou
		if (!name.equals(oldName)) {
			AssetTag existingTag = fetchTag(tag.getGroupId(), name);
			if ((existingTag != null) && (existingTag.getTagId() != tagId)) {
				throw new DuplicateTagException(
						"A tag with the name " + name + " already exists");
			}
		}

		try {
			// Usa nome temporário se necessário
			String tempName = name.replace("+", "TEMPPLUS");

			// Atualiza com nome temporário
			AssetTag updatedTag = super.updateTag(userId, tagId, tempName, serviceContext);

			// Atualiza para o nome real
			updatedTag.setName(name);
			updatedTag = super.updateAssetTag(updatedTag);

			_log.info("Tag atualizada com sucesso: " + name);

			return updatedTag;

		} catch (Exception e) {
			_log.error("Erro ao atualizar tag: " + e.getMessage(), e);
			throw new PortalException("Erro ao atualizar tag", e);
		}
	}

	/**
	 * Validação customizada que permite o caractere +
	 */
	protected void validateCustom(String name) throws PortalException {
		if (Validator.isNull(name)) {
			throw new AssetTagNameException(
					"Tag name cannot be an empty string");
		}

		if (!isValidWordCustom(name)) {
			throw new AssetTagException(
					"Tag name has one or more invalid characters: " +
							StringUtil.merge(_CUSTOM_INVALID_CHARACTERS, StringPool.SPACE),
					AssetTagException.INVALID_CHARACTER);
		}

		int maxLength = ModelHintsUtil.getMaxLength(
				AssetTag.class.getName(), "name");

		if (name.length() > maxLength) {
			throw new AssetTagException(
					"Tag name has more than " + maxLength + " characters",
					AssetTagException.MAX_LENGTH);
		}
	}

	/**
	 * Verifica se a palavra é válida usando nossa lista customizada
	 */
	private boolean isValidWordCustom(String word) {
		if (Validator.isBlank(word)) {
			return false;
		}

		char[] wordCharArray = word.toCharArray();

		for (char c : wordCharArray) {
			for (char invalidChar : _CUSTOM_INVALID_CHARACTERS) {
				if (c == invalidChar) {
					_log.debug("Character '" + c + "' is not allowed in tag name");
					return false;
				}
			}
		}

		return true;
	}
}