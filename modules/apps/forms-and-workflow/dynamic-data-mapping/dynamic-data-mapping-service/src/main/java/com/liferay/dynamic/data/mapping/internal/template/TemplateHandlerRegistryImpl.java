/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.mapping.internal.template;

import com.liferay.dynamic.data.mapping.kernel.DDMTemplate;
import com.liferay.dynamic.data.mapping.kernel.DDMTemplateManager;
import com.liferay.dynamic.data.mapping.service.permission.DDMTemplatePermission;
import com.liferay.portal.instance.lifecycle.BasePortalInstanceLifecycleListener;
import com.liferay.portal.instance.lifecycle.PortalInstanceLifecycleListener;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.template.TemplateHandler;
import com.liferay.portal.kernel.template.TemplateHandlerRegistry;
import com.liferay.portal.kernel.util.AggregateResourceBundleLoader;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapDictionary;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.ResourceBundleLoader;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.language.LanguageResources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * @author Michael C. Han
 */
@Component(immediate = true, service = TemplateHandlerRegistry.class)
public class TemplateHandlerRegistryImpl implements TemplateHandlerRegistry {

	@Override
	public long[] getClassNameIds() {
		return ArrayUtil.toLongArray(_classNameIdTemplateHandlers.keySet());
	}

	@Override
	public TemplateHandler getTemplateHandler(long classNameId) {
		return _classNameIdTemplateHandlers.get(classNameId);
	}

	@Override
	public TemplateHandler getTemplateHandler(String className) {
		return _classNameTemplateHandlers.get(className);
	}

	@Override
	public List<TemplateHandler> getTemplateHandlers() {
		return new ArrayList<>(_classNameTemplateHandlers.values());
	}

	@Activate
	protected synchronized void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;

		for (Map.Entry<String, TemplateHandler> entry :
				_classNameTemplateHandlers.entrySet()) {

			String className = entry.getKey();
			TemplateHandler templateHandler = entry.getValue();

			_classNameIdTemplateHandlers.put(
				_portal.getClassNameId(className), templateHandler);

			if (_serviceRegistrations.containsKey(className)) {
				continue;
			}

			registerPortalInstanceLifecycleListener(templateHandler);
		}
	}

	@Reference(
		cardinality = ReferenceCardinality.MULTIPLE,
		policy = ReferencePolicy.DYNAMIC,
		policyOption = ReferencePolicyOption.GREEDY,
		unbind = "removeTemplateHandler"
	)
	protected synchronized void addTemplateHandler(
		TemplateHandler templateHandler) {

		String className = templateHandler.getClassName();

		_classNameTemplateHandlers.put(className, templateHandler);

		if (_bundleContext == null) {
			return;
		}

		_classNameIdTemplateHandlers.put(
			_portal.getClassNameId(className), templateHandler);

		registerPortalInstanceLifecycleListener(templateHandler);
	}

	@Deactivate
	protected synchronized void deactivate() {
		_classNameIdTemplateHandlers.clear();
		_classNameTemplateHandlers.clear();

		for (ServiceRegistration<?> serviceRegistration :
				_serviceRegistrations.values()) {

			serviceRegistration.unregister();
		}

		_serviceRegistrations.clear();

		_bundleContext = null;
	}

	protected void registerPortalInstanceLifecycleListener(
		TemplateHandler templateHandler) {

		ServiceRegistration<?> serviceRegistration = _serviceRegistrations.get(
			templateHandler.getClassName());

		if (serviceRegistration != null) {
			serviceRegistration.unregister();
		}

		PortalInstanceLifecycleListener portalInstanceLifecycleListener =
			new TemplateHandlerPortalInstanceLifecycleListener(templateHandler);

		serviceRegistration = _bundleContext.registerService(
			PortalInstanceLifecycleListener.class,
			portalInstanceLifecycleListener,
			new HashMapDictionary<String, Object>());

		_serviceRegistrations.put(
			templateHandler.getClassName(), serviceRegistration);
	}

	protected synchronized void removeTemplateHandler(
		TemplateHandler templateHandler) {

		String className = templateHandler.getClassName();

		_classNameTemplateHandlers.remove(className);

		if (_portal != null) {
			_classNameIdTemplateHandlers.remove(
				_portal.getClassNameId(className));
		}

		ServiceRegistration<?> serviceRegistration =
			_serviceRegistrations.remove(className);

		if (serviceRegistration != null) {
			serviceRegistration.unregister();
		}
	}

	@Reference(unbind = "-")
	protected void setDDMTemplateManager(
		DDMTemplateManager ddmTemplateManager) {

		_ddmTemplateManager = ddmTemplateManager;
	}

	@Reference(unbind = "-")
	protected void setDDMTemplatePermission(
		DDMTemplatePermission ddmTemplatePermission) {
	}

	@Reference(unbind = "-")
	protected void setGroupLocalService(GroupLocalService groupLocalService) {
		_groupLocalService = groupLocalService;
	}

	@Reference(unbind = "-")
	protected void setPortal(Portal portal) {
		_portal = portal;
	}

	@Reference(unbind = "-")
	protected void setUserLocalService(UserLocalService userLocalService) {
		_userLocalService = userLocalService;
	}

	private BundleContext _bundleContext;
	private final Map<Long, TemplateHandler> _classNameIdTemplateHandlers =
		new ConcurrentHashMap<>();
	private final Map<String, TemplateHandler> _classNameTemplateHandlers =
		new ConcurrentHashMap<>();
	private DDMTemplateManager _ddmTemplateManager;
	private GroupLocalService _groupLocalService;
	private Portal _portal;
	private final Map<String, ServiceRegistration<?>> _serviceRegistrations =
		new ConcurrentHashMap<>();
	private UserLocalService _userLocalService;

	private class TemplateHandlerPortalInstanceLifecycleListener
		extends BasePortalInstanceLifecycleListener {

		@Override
		public void portalInstanceRegistered(Company company) throws Exception {
			long classNameId = _portal.getClassNameId(
				_templateHandler.getClassName());

			ServiceContext serviceContext = new ServiceContext();

			Group group = _groupLocalService.getCompanyGroup(
				company.getCompanyId());

			serviceContext.setScopeGroupId(group.getGroupId());

			long userId = _userLocalService.getDefaultUserId(
				company.getCompanyId());

			serviceContext.setUserId(userId);

			List<Element> templateElements =
				_templateHandler.getDefaultTemplateElements();

			for (Element templateElement : templateElements) {
				String templateKey = templateElement.elementText(
					"template-key");

				DDMTemplate ddmTemplate = _ddmTemplateManager.fetchTemplate(
					group.getGroupId(), classNameId, templateKey);

				if (ddmTemplate != null) {
					continue;
				}

				Class<?> clazz = _templateHandler.getClass();

				ClassLoader classLoader = clazz.getClassLoader();

				ResourceBundleLoader resourceBundleLoader =
					new AggregateResourceBundleLoader(
						ResourceBundleUtil.getResourceBundleLoader(
							"content.Language", classLoader),
						LanguageResources.RESOURCE_BUNDLE_LOADER);

				Map<Locale, String> nameMap = getLocalizationMap(
					resourceBundleLoader, group.getGroupId(),
					templateElement.elementText("name"));
				Map<Locale, String> descriptionMap = getLocalizationMap(
					resourceBundleLoader, group.getGroupId(),
					templateElement.elementText("description"));

				String type = templateElement.elementText("type");

				if (type == null) {
					type = DDMTemplateManager.TEMPLATE_TYPE_DISPLAY;
				}

				String language = templateElement.elementText("language");

				String scriptFileName = templateElement.elementText(
					"script-file");

				String script = StringUtil.read(classLoader, scriptFileName);

				boolean cacheable = GetterUtil.getBoolean(
					templateElement.elementText("cacheable"));

				_ddmTemplateManager.addTemplate(
					userId, group.getGroupId(), classNameId, 0,
					_portal.getClassNameId(
						_PORTLET_DISPLAY_TEMPLATE_CLASS_NAME),
					templateKey, nameMap, descriptionMap, type, null, language,
					script, cacheable, false, null, null, serviceContext);
			}
		}

		@Override
		public void portalInstanceUnregistered(Company company)
			throws Exception {
		}

		protected Map<Locale, String> getLocalizationMap(
			ResourceBundleLoader resourceBundleLoader, long groupId,
			String key) {

			Map<Locale, String> map = new HashMap<>();

			for (Locale locale : LanguageUtil.getAvailableLocales(groupId)) {
				ResourceBundle resourceBundle =
					resourceBundleLoader.loadResourceBundle(
						LocaleUtil.toLanguageId(locale));

				map.put(locale, LanguageUtil.get(resourceBundle, key));
			}

			return map;
		}

		private TemplateHandlerPortalInstanceLifecycleListener(
			TemplateHandler templateHandler) {

			_templateHandler = templateHandler;
		}

		private static final String _PORTLET_DISPLAY_TEMPLATE_CLASS_NAME =
			"com.liferay.portlet.display.template.PortletDisplayTemplate";

		private final TemplateHandler _templateHandler;

	}

}