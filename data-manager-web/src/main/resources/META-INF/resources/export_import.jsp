<%--
    export_import.jsp: default view of the export-import panel
    
    Created:    2017-11-01 19:01 by Christian Berndt
    Modified:   2017-12-12 17:27 by Christian Berndt
    Version:    1.0.4
--%>

<%@ include file="/init.jsp" %>

<%
    String redirect = ParamUtil.getString(request, "redirect");

    PortletURL portletURL = renderResponse.createRenderURL();

    portletURL.setParameter("mvcPath", "/view.jsp");
    portletURL.setParameter("redirect", redirect);
    portletURL.setParameter("tabs1", "export-import");

    // TODO: check export-import permissions
%>
<c:choose>
    <c:when test="<%= false %>">
<%--     <c:when test="<%= !GroupPermissionUtil.contains(permissionChecker, themeDisplay.getScopeGroup(), ActionKeys.MANAGE_STAGING) %>"> --%>
        <div class="alert alert-info">
            <liferay-ui:message key="you-do-not-have-permission-to-access-the-requested-resource" />
        </div>
    </c:when>
    <c:otherwise>
        <aui:nav-bar cssClass="navbar-collapse-absolute" markupView="<%= markupView %>">
            <aui:nav cssClass="navbar-nav">

                <%
                    portletURL.setParameter("tabs2", "export");
                %>

                <aui:nav-item
                    href="<%= portletURL.toString() %>"
                    label="export"
                    selected='<%= tabs2.equals("export") %>'
                />

                <%
                    portletURL.setParameter("tabs2", "import");
                %>

                <aui:nav-item
                    href="<%= portletURL.toString() %>"
                    label="import"
                    selected='<%= tabs2.equals("import") %>'
                />
                
                <%
                    portletURL.setParameter("tabs2", "manage");
                %>

                <aui:nav-item
                    href="<%= portletURL.toString() %>"
                    label="manage"
                    selected='<%= tabs2.equals("manage") %>'
                />
            </aui:nav>
        </aui:nav-bar>
        
        <div class="portlet-export-import-container" id="<portlet:namespace />exportImportPortletContainer">
        
            <liferay-util:include page="/export_import_error.jsp" servletContext="<%= application %>" />

            <c:choose>
                <c:when test='<%= tabs2.equals("export") %>'>
                    <liferay-util:include page="/export/view.jsp" servletContext="<%= application %>" />
                </c:when>
                <c:when test='<%= tabs2.equals("import") %>'>
                    <liferay-util:include page="/import/view.jsp" servletContext="<%= application %>" />
                </c:when>
                <c:when test='<%= tabs2.equals("manage") %>'>
                    <liferay-util:include page="/import/manage.jsp" servletContext="<%= application %>" />               
                    <liferay-util:include page="/delete_measurements.jsp" servletContext="<%= application %>"/>
                </c:when>  
            </c:choose>
        </div>
    </c:otherwise>
</c:choose>
