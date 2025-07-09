package com.seatecnologia.br;

import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetTagLocalService;
import com.liferay.asset.kernel.service.AssetTagLocalServiceWrapper;
import com.liferay.asset.kernel.service.persistence.AssetTagPersistence;
import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceWrapper;
import com.liferay.portal.kernel.service.UserLocalService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
	public AssetTag addTag(long userId, long groupId, String name, ServiceContext serviceContext)
			throws PortalException {

		_log.info("=== addTag interceptado: " + name);

		if (name != null && name.contains("+")) {
			return createTagWithPlus(userId, groupId, name, serviceContext);
		}

		return super.addTag(userId, groupId, name, serviceContext);
	}

	@Override
	public List<AssetTag> checkTags(long userId, Group group, String[] names) throws PortalException {

		_log.info("=== checkTags (Group) interceptado com " + names.length + " tags");

		List<AssetTag> result = new ArrayList<>();

		for (String name : names) {
			if (name != null && name.contains("+")) {
				_log.info("Tag com + encontrada em checkTags: " + name);

				// Verifica se já existe
				AssetTag existingTag = fetchTag(group.getGroupId(), name.trim());
				if (existingTag != null) {
					result.add(existingTag);
				} else {
					// Cria nova tag
					ServiceContext serviceContext = new ServiceContext();
					serviceContext.setCompanyId(group.getCompanyId());
					serviceContext.setScopeGroupId(group.getGroupId());
					serviceContext.setAddGroupPermissions(true);
					serviceContext.setAddGuestPermissions(true);

					AssetTag newTag = createTagWithPlus(userId, group.getGroupId(),
							name, serviceContext);
					result.add(newTag);
				}
			} else {
				// Usa o comportamento padrão para tags sem +
				List<AssetTag> standardTags = super.checkTags(userId, group, new String[]{name});
				result.addAll(standardTags);
			}
		}

		return result;
	}

	@Override
	public List<AssetTag> checkTags(long userId, long groupId, String[] names) throws PortalException {

		_log.info("=== checkTags (groupId) interceptado com " + names.length + " tags");

		Group group = _groupLocalService.getGroup(groupId);
		return checkTags(userId, group, names);
	}

	/**
	 * Método auxiliar para criar tag com +
	 */
	private AssetTag createTagWithPlus(long userId, long groupId, String name,
									   ServiceContext serviceContext) throws PortalException {
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
			throw new PortalException("Erro ao criar tag: " + name, e);
		}
	}

	@Reference
	private CounterLocalService _counterLocalService;

	@Reference
	private AssetTagPersistence _assetTagPersistence;

	@Reference
	private UserLocalService _userLocalService;

	@Reference
	private GroupLocalService _groupLocalService;
}