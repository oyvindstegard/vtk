<#ftl strip_whitespace=true />
<#import "/lib/menu/list-menu.ftl" as listMenu />
<#import "/lib/vortikal.ftl" as vrtx />

<#assign resource = resourceContext.currentResource />

<#if resource?exists && resourceMenuLeft?exists && resourceMenuRight?exists>
  <@gen resource resourceMenuLeft resourceMenuRight />
<#elseif resource?exists && resourceMenuLeft?exists>
  <@gen resource resourceMenuLeft />
<#else>  
  <@gen resource /> 
</#if>

<#macro gen resource resourceMenuLeft="" resourceMenuRight="">
  <div id="titleContainer">
    <div class="resource-title <@vrtx.iconResolver resource.resourceType resource.contentType /> ${resource.collection?string}">
      <h1>
        <#if resource.URI == '/'>
          ${repositoryID?html}
        <#else>
          ${resource.name?html}
        </#if>
      </h1>
      <#if browseURL?exists && editField?exists><#-- TODO: fix this hack for browse -->
        <ul class="list-menu resourceMenuLeft">
          <li class="createLinkToResourceService first last">
            <a href="javascript:updateParent('${editField}', '${browseURL}')"><@vrtx.msg code="resourceMenuLeft.createLinkToResourceService" default="Create link" /></a>
          </li>
        </ul>
      </#if>
      <#if resourceMenuRight != "">
        <@listMenu.listMenu menu=resourceMenuRight displayForms=true prepend="" append=""/>
      </#if>
      <#if resourceMenuRight != "">
        <@listMenu.listMenu menu=resourceMenuLeft displayForms=true prepend="" append=""/>
      </#if>
    </div>
  </div>
</#macro>
