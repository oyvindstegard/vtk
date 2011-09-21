<#ftl strip_whitespace=true>

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
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/permissions.ftl" as permissions />
<#import "/lib/autocomplete.ftl" as autocomplete />
<#import "/lib/ping.ftl" as ping />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Permissions on resource</title>
    <@ping.ping url=pingURL['url'] interval=900 />   
      
    <@autocomplete.addAutoCompleteScripts srcBase="${webResources?html}"/>
    <script type='text/javascript' src='${webResources?html}/js/autocomplete/autocomplete-permissions.js'></script>
    
  </head>
  <body id="vrtx-permissions">

    <#assign resource = resourceContext.currentResource />
    <#assign defaultHeader = vrtx.getMsg("permissions.header", "Permissions on this resource") />

    <div class="resourceInfo permissions">
      <h2>
        <@vrtx.msg
           code="permissions.header.${resource.resourceType}"
           default="${defaultHeader}"/>
      </h2>

      <#assign defaultNotInherited = vrtx.getMsg("permissions.notInherited", "Custom permissions") />

      <#if aclInfo.aclEditURLs?exists && aclInfo.aclEditURLs.inheritance?exists>
      <form action="${aclInfo.aclEditURLs.inheritance?html}" method="post"
            id="permissions.toggleInheritance" class="vrtx-admin-button">
        <#if resourceContext.currentResource.inheritedAcl>
          <div id="permissions-inheritance">
          <@vrtx.msg code="permissions.isInherited" default="Inherited permissions" />
          <#if aclInfo.aclEditURLs.inheritance?exists>
            &nbsp;<div class="vrtx-button-small"><input type="submit" id="permissions.toggleInheritance.submit" name="confirmation" value="<@vrtx.msg code="permissions.setCustom" default="Edit" />" /></div>
          </#if>
          </div>
        <#else>
          <#assign warning =
                   vrtx.getMsg("permissions.removeAcl.warning", 
                   "Are you sure you want to set inherited permissions? This cannot be undone.",
                   [resource.name]) />
          <div id="permissions-inheritance">         
          <@vrtx.msg code="permissions.notInherited.${resource.resourceType}" default="${defaultNotInherited}" />
          <#if aclInfo.aclEditURLs.inheritance?exists>
            &nbsp;<div class="vrtx-button-small"><input type="submit" onclick="return confirm('${warning?html?js_string}');" id="permissions.toggleInheritance.submit" name="confirmation" value="<@vrtx.msg code="permissions.setInherited" default="Edit" />" /></div>
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
      		<@vrtx.msg code="permissions.notInherited.${resource.resourceType}" default="${defaultNotInherited}" />  
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
      <#if resource.collection>
        <#assign privilegeList = [{"name":"add-comment", "heading":commentHeading }, {"name":"read-processed", "heading":readProHeading }] />
      <#else>
        <#assign privilegeList = [{"name":"add-comment", "heading":commentHeading }, {"name":"read-processed", "heading":readProHeading }] />
      </#if>        
      <@permissions.editOrDisplayPrivileges privilegeList=privilegeList heading=groupHeading />

    </div>
  </body>
</html>

