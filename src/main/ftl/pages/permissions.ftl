<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: permissions.ftl
  - 
  - Description: A HTML page that displays the permissions set on a
  - resource
  -
  - Required model data:
  -
  - Optional model data:
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/permissions.ftl" as permissions />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Permissions on resource</title>
    <link rel="stylesheet" type="text/css" href="${webResources}/jquery/plugins/jquery.autocomplete.css" />
    <link rel="stylesheet" type="text/css" href="${webResources}/js/autocomplete/autocomplete.override.css" />
    <script type='text/javascript' src='${webResources}/jquery/plugins/jquery.autocomplete.js'></script>
    <script type='text/javascript' src='${webResources}/js/autocomplete/autocomplete.js'></script>
    <script type='text/javascript' src='${webResources}/js/autocomplete/autocomplete-permissions.js'></script>
  </head>
  <body id="vrtx-permissions">

    <#assign resource = resourceContext.currentResource />
    <#assign defaultHeader = vrtx.getMsg("permissions.header", "Permissions on this resource") />

    <div class="resourceInfo permissions">
      <h2>
        <@vrtx.msg
           code="permissions.header.${resource.resourceType}"
           default=defaultHeader?markup_string />
      </h2>
      
      <script type="text/javascript"><!--
        var removeAdminPermissionsMsg = '<@vrtx.msg code="permissions.all.confirm.remove.yourself" default="Are you sure you want to remove all admin permissions for yourself?" />';
        var removeAdminPermissionsTitle = '<@vrtx.msg code="permissions.all.confirm.remove.yourself.title" default="Confirm remove" />';
      // -->
      </script>

      <#assign defaultNotInherited = vrtx.getMsg("permissions.notInherited", "Custom permissions") />

      <#if aclInfo.aclEditURLs?exists && aclInfo.aclEditURLs.inheritance?exists>
      <form action="${aclInfo.aclEditURLs.inheritance}" method="post" id="permissions.toggleInheritance">
        <#if resourceContext.currentResource.inheritedAcl>
          <div id="permissions-inheritance">
          <@vrtx.msg code="permissions.isInherited" default="Inherited permissions" /><#t/>
          <#t/><#if aclInfo.aclEditURLs.inheritance?exists>&nbsp;<#t/>
            <#t/><#if resourceContext.requestContext.indexFile><#t/>
              <#t/><abbr tabindex="0" title="${vrtx.getMsg('permissions.inherited.index-file')}" class="tooltips"></abbr>
            <#else>
              <#t/><input class="vrtx-button-small" type="submit" id="permissions.toggleInheritance.submit" name="confirmation" value="<@vrtx.msg code="permissions.setCustom" default="Edit" />" />
            </#if>
          </#if>
          </div>
        <#else>
          <#assign warning =
                   vrtx.getMsg("permissions.removeAcl.warning", 
                   "Are you sure you want to set inherited permissions? This cannot be undone.",
                   [resource.name]) />
          <div id="permissions-inheritance">
          <@vrtx.msg code="permissions.notInherited.${resource.resourceType}" default=defaultNotInherited?markup_string />
          <#if aclInfo.aclEditURLs.inheritance?exists>
            <#assign permissionsSetInherited><@vrtx.msg code="permissions.setInherited" /></#assign>
            <script type="text/javascript"><!--
              var confirmSetInheritedPermissionsMsg = '${warning?markup_string?js_string}',
                  confirmSetInheritedPermissionsTitle = '${permissionsSetInherited}';
            // -->
            </script>
            &nbsp;<input class="vrtx-button-small" type="submit" id="permissions.toggleInheritance.submit" name="confirmation" value="${permissionsSetInherited}" />
          </div>
          </#if>
        </#if>
      </form>
      <#elseif resourceContext.currentResource.inheritedAcl>
      	<div id="permissions-inheritance">
     	  <@vrtx.msg code="permissions.isInherited" default="Inherited permissions" />
     	</div>
      <#elseif !resourceContext.currentResource.inheritedAcl>
      	<div id="permissions-inheritance">
      	  <@vrtx.msg code="permissions.notInherited.${resource.resourceType}" default="${defaultNotInherited?markup_string}" />
      	</div>
      </#if>	
      
      <div id="permissions-read-write-admin">
        <div class="permissions-read-wrapper">
          <#assign privilegeHeading><@vrtx.msg code="permissions.privilege.read" default="Read" /></#assign>
          <@permissions.editOrDisplayPrivilege privilegeName="read" privilegeHeading=privilegeHeading />
        </div>
        <div class="permissions-read-write-wrapper">
          <#assign privilegeHeading><@vrtx.msg code="permissions.privilege.read-write" default="Read and Write" /></#assign>
          <@permissions.editOrDisplayPrivilege privilegeName="read-write" privilegeHeading=privilegeHeading />
        </div>
        <div class="permissions-all-wrapper">
          <#assign privilegeHeading><@vrtx.msg code="permissions.privilege.all" default="Admin - all privileges" /></#assign>
          <@permissions.editOrDisplayPrivilege privilegeName="all" privilegeHeading=privilegeHeading />
        </div>
      </div>
      
      <#assign groupHeading><@vrtx.msg code="permissions.advanced" default="Advanced permissions" /></#assign>

      <#assign commentHeading><@vrtx.msg code="permissions.privilege.add-comment" default="Add comments" /></#assign>
      <#assign readProHeading><@vrtx.msg code="permissions.privilege.read-processed" default="Read processed only" /></#assign>
      <#assign readWritePubHeading><@vrtx.msg code="permissions.privilege.read-write-unpublished" default="Read and write unpublished" /></#assign>
      
      <@permissions.editOrDisplayPrivileges privilegeList=[ 
                                 {"name":"add-comment", "heading":commentHeading }, 
                                 {"name":"read-processed", "heading":readProHeading },
                                 {"name":"read-write-unpublished", "heading":readWritePubHeading }
                               ] heading=groupHeading />

    </div>
    <@vrtx.javascriptPlaceholder place="permissions:body_end" />
  </body>
</html>
