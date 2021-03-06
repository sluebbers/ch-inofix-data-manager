<%--
    import/view.jsp: default view of the measurements import
    
    Created:    2017-11-01 18:58 by Christian Berndt
    Modified:   2017-12-03 17:31 by Christian Berndt
    Version:    1.0.3
--%>

<%@ include file="/init.jsp" %>

<%
    long backgroundTaskId = ParamUtil.getLong(request, "backgroundTaskId");
    Calendar cal = CalendarFactoryUtil.getCalendar(timeZone, locale);

    int timeZoneOffset = timeZone.getOffset(cal.getTimeInMillis());
%>

<%
    String displayStyle = ParamUtil.getString(request, "displayStyle", "list");
    long groupId = scopeGroupId;
    String navigation = ParamUtil.getString(request, "navigation", "all");
    String orderByCol = ParamUtil.getString(request, "orderByCol", "create-date");
    String orderByType = ParamUtil.getString(request, "orderByType", "desc");
    String searchContainerId = "importMeasurementProcesses";

    boolean completed = false;

    if ("completed".equals(navigation)) {
        completed = true;
    }
%>

<% // TODO: enable permission checks %>

<c:choose>
    <c:when test="<%= false %>">
<%--     <c:when test="<%= !GroupPermissionUtil.contains(permissionChecker, liveGroupId, ActionKeys.EXPORT_IMPORT_LAYOUTS) %>"> --%>
        <div class="alert alert-info">
            <liferay-ui:message key="you-do-not-have-permission-to-access-the-requested-resource" />
        </div>
    </c:when>
    <c:otherwise>   
        <liferay-util:include page="/import/processes_list/view.jsp" servletContext="<%= application %>">
            <liferay-util:param name="displayStyle" value="<%= displayStyle %>" />
            <liferay-util:param name="navigation" value="<%= navigation %>" />
            <liferay-util:param name="orderByCol" value="<%= orderByCol %>" />
            <liferay-util:param name="orderByType" value="<%= orderByType %>" />
            <liferay-util:param name="searchContainerId" value="<%= searchContainerId %>" />
        </liferay-util:include>
        
        <liferay-util:include page="/import/add_button.jsp" servletContext="<%= application %>">
            <liferay-util:param name="groupId" value="<%= String.valueOf(groupId) %>" />
            <liferay-util:param name="displayStyle" value="<%= displayStyle %>" />
        </liferay-util:include>
    </c:otherwise>
</c:choose>

<liferay-portlet:resourceURL copyCurrentRenderParameters="<%= false %>" id="importMeasurements" var="importProcessesURL">
    <portlet:param name="<%= Constants.CMD %>" value="<%= Constants.IMPORT %>" />
    <portlet:param name="<%= SearchContainer.DEFAULT_CUR_PARAM %>" value="<%= ParamUtil.getString(request, SearchContainer.DEFAULT_CUR_PARAM) %>" />
    <portlet:param name="<%= SearchContainer.DEFAULT_DELTA_PARAM %>" value="<%= ParamUtil.getString(request, SearchContainer.DEFAULT_DELTA_PARAM) %>" />
    <portlet:param name="displayStyle" value="<%= displayStyle %>" />
    <portlet:param name="navigation" value="<%= navigation %>" />
    <portlet:param name="orderByCol" value="<%= orderByCol %>" />
    <portlet:param name="orderByType" value="<%= orderByType %>" />
    <portlet:param name="searchContainerId" value="<%= searchContainerId %>" />
    <portlet:param name="tabs1" value="<%= tabs1 %>" />
    <portlet:param name="tabs2" value="<%= tabs2 %>" />
</liferay-portlet:resourceURL>
    
<aui:script use="liferay-export-import">

    new Liferay.ExportImport(
        {
            incompleteProcessMessageNode: '#<portlet:namespace />incompleteProcessMessage',
            locale: '<%= locale.toLanguageTag() %>',
            namespace: '<portlet:namespace />',
            processesNode: '#importProcessesSearchContainer',
            processesResourceURL: '<%= HtmlUtil.escapeJS(importProcessesURL.toString()) %>',
            timeZoneOffset: <%= timeZoneOffset %>
        }
    );
</aui:script>
