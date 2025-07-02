package com.seatecnologia.br;

import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalServiceWrapper;
import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.asset.kernel.service.persistence.AssetTagPersistence;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceWrapper;
import com.liferay.portal.kernel.service.UserLocalService;

import java.util.Date;

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
				"service.ranking:Integer=2000"
		},
		service = ServiceWrapper.class
)
public class CustomAssetTagLocalServiceWrapper extends AssetTagLocalServiceWrapper {

	private static final Log _log = LogFactoryUtil.getLog(CustomAssetTagLocalServiceWrapper.class);

	public CustomAssetTagLocalServiceWrapper() {
		super(null);
	}

	@Override
	public AssetTag addTag(long userId, long groupId, String name,
						   ServiceContext serviceContext) throws PortalException {

		_log.info("=== addTag interceptado: " + name);

		// Se tem +, cria diretamente sem validação
		if (name != null && name.contains("+")) {
			try {
				// Verifica se já existe
				AssetTag existingTag = fetchTag(groupId, name.trim());
				if (existingTag != null) {
					_log.info("Tag já existe: " + name);
					return existingTag;
				}

				// Cria a tag diretamente
				long tagId = _counterLocalService.increment(AssetTag.class.getName());
				AssetTag tag = _assetTagPersistence.create(tagId);

				tag.setUuid(serviceContext.getUuid());
				tag.setGroupId(groupId);
				tag.setCompanyId(serviceContext.getCompanyId());
				tag.setUserId(userId);
				tag.setUserName(_userLocalService.getUser(userId).getFullName());
				tag.setCreateDate(serviceContext.getCreateDate(new Date()));
				tag.setModifiedDate(serviceContext.getModifiedDate(new Date()));
				tag.setName(name.trim());
				tag.setAssetCount(0);

				tag = _assetTagPersistence.update(tag);

				_log.info("Tag criada com + via BYPASS: " + name);
				return tag;

			} catch (Exception e) {
				_log.error("Erro ao criar tag com +", e);
				// Se falhar, cria sem o +
				String safeName = name.replace("+", "_plus_");
				return super.addTag(userId, groupId, safeName, serviceContext);
			}
		}

		return super.addTag(userId, groupId, name, serviceContext);
	}

	@Override
	public AssetTag updateTag(long userId, long tagId, String name,
							  ServiceContext serviceContext) throws PortalException {

		_log.info("=== updateTag interceptado: " + name);

		if (name != null && name.contains("+")) {
			try {
				AssetTag tag = getAssetTag(tagId);
				tag.setName(name.trim());
				tag = _assetTagPersistence.update(tag);

				_log.info("Tag atualizada com +: " + name);
				return tag;

			} catch (Exception e) {
				_log.error("Erro ao atualizar tag com +", e);
			}
		}

		return super.updateTag(userId, tagId, name, serviceContext);
	}

	@Reference
	private CounterLocalService _counterLocalService;

	@Reference
	private AssetTagPersistence _assetTagPersistence;

	@Reference
	private UserLocalService _userLocalService;
}