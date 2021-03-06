<#assign pageCounts = dataFactory.getSequence(dataFactory.maxAssetPublisherPageCount) />

<#list pageCounts as pageCount>
	<#assign
		portletId = "com_liferay_asset_publisher_web_portlet_AssetPublisherPortlet_INSTANCE_TEST_" + pageCount

		layoutModel = dataFactory.newLayoutModel(groupId, groupId + "_asset_publisher_" + pageCount, "", portletId)
	/>

	${dataFactory.getCSVWriter("assetPublisher").write(layoutModel.friendlyURL + "\n")}

	<@insertLayout
		_layoutModel = layoutModel
	/>

	<#assign portletPreferencesModels = dataFactory.newAssetPublisherPortletPreferencesModels(layoutModel.plid) />

	<#list portletPreferencesModels as portletPreferencesModel>
		${dataFactory.toInsertSQL(portletPreferencesModel)}
	</#list>

	${dataFactory.toInsertSQL(dataFactory.newPortletPreferencesModel(layoutModel.plid, groupId, portletId, pageCount))}
</#list>