<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/menu/list-menu.ftl" as listMenu />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/ping.ftl" as ping />

<#assign resource = resourceContext.currentResource />

<#-- ********************
      JavaScript domains 
     ********************
     
     TODO: move to XML
-->
<#-- Listing (collection and trash-can) -->
<#if (!RequestParameters.mode?exists && !RequestParameters.action?exists && resource.collection)
  || (RequestParameters.mode?exists && RequestParameters.mode == "trash-can" && resource.collection)
  || (RequestParameters.action?exists && RequestParameters.action == "create-document" && resource.collection)
  || (RequestParameters.action?exists && RequestParameters.action == "create-directory" && resource.collection)
  || (RequestParameters.action?exists && RequestParameters.action == "upload-file" && resource.collection)
  || (RequestParameters.action?exists && RequestParameters.action == "copy-resources-to-this-folder" && resource.collection)
  || (RequestParameters.action?exists && RequestParameters.action == "move-resources-to-this-folder" && resource.collection)>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/domains/listing.js"></script>
<#-- Save in editors -->
<#elseif (RequestParameters.action?exists && RequestParameters.action == "plaintext-edit")
      || (RequestParameters.mode?exists && RequestParameters.mode == "editor" &&
          RequestParameters.action?exists && RequestParameters.action == "edit")
      || (RequestParameters.mode?exists && RequestParameters.mode == "aspects")>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/domains/editors.js"></script>
<#-- Permissions-->
<#elseif (RequestParameters.mode?exists && RequestParameters.mode == "permissions")>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/domains/permissions.js"></script>
<#-- About -->
<#elseif (RequestParameters.mode?exists && RequestParameters.mode == "about")>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/domains/about.js"></script>
</#if>

<#-- ********************
      Server information 
     ********************
-->

<#assign lastModified = resource.getLastModified() />
<#assign modifiedBy = resource.getModifiedBy() />
<span id="server-now-time" class="hidden-server-info">${nowTime?string("yyyy")},${nowTime?string("MM")},${nowTime?string("dd")},${nowTime?string("HH")},${nowTime?string("mm")},${nowTime?string("ss")}</span>
<span id="resource-last-modified" class="hidden-server-info">${lastModified?string("yyyy")},${lastModified?string("MM")},${lastModified?string("dd")},${lastModified?string("HH")},${lastModified?string("mm")},${lastModified?string("ss")}</span>
<span id="resource-last-modified-by" class="hidden-server-info">${modifiedBy}</span>
<#if resource.lock?exists && resource.lock.principal?exists>
  <#assign lockedBy = resource.lock.principal.name />
  <#if resource.lock.principal.URL?exists>
    <#assign lockedBy = resource.lock.principal.description />
  </#if>
  <#assign owner = resource.lock.principal.qualifiedName />
  <#assign currentPrincipal = resourceContext.principal.qualifiedName />
  <span id="resource-locked-by-other" class="hidden-server-info"><#if owner?exists && owner != currentPrincipal>true<#else>false</#if></span>
  <span id="resource-locked-by" class="hidden-server-info">${lockedBy}</span>
</#if>
<span id="resource-can-edit" class="hidden-server-info"><#if (writePermissionAtAll.permissionsQueryResult)?exists && writePermissionAtAll.permissionsQueryResult = 'true'>true<#else>false</#if></span>

<#include "/system/system.ftl" />

<#-- ***************
      Keep-alive
     ***************
-->
<#if pingURL?? && !resourceContext.currentServiceName?lower_case?contains("preview")>
  <@ping.ping url=pingURL['url'] interval=300/> 
</#if>

<#-- ***************
      Resource menu 
     ***************
-->
<#if resource?? && resourceMenuLeft?? && resourceMenuRight??>
  <@gen resource resourceMenuLeft resourceMenuRight />
<#elseif resource?exists && resourceMenuLeft?exists>
  <@gen resource resourceMenuLeft />
<#else>
  <@gen resource /> 
</#if>

<#macro gen resource resourceMenuLeft resourceMenuRight>
  <div id="title-container">
    
    <div id="resource-title" class="<@vrtx.resourceToIconResolver resource /> ${resource.collection?string}">
      <h1><#compress>
        <#if resource.URI == '/'>
          ${repositoryID}
        <#else>
          ${resource.name}
        </#if>
      </#compress></h1>
      <#if browseURL?exists && editField?exists><#-- TODO: fix this hack for browse -->
        <ul class="list-menu button-row" id="resourceMenuLeft">
          <li class="createLinkToResourceService first last">
            <a href="javascript:updateParent('${editField}', '${browseURL}')"><@vrtx.msg code="resourceMenuLeft.createLinkToResourceService" default="Create link" /></a>
          </li>
        </ul>
      </#if>

      <#if resourceMenuLeft??>
        <@listMenu.listMenu menu=resourceMenuLeft displayForms=true prepend="" append=""/>
      </#if>
      <#if resourceMenuRight??>
        <@listMenu.listMenu menu=resourceMenuRight displayForms=true prepend="" append=""/>
      </#if>
    </div>
  </div>
</#macro>
