<#ftl strip_whitespace=true>

<#--
  - File: resource-title.ftl
  - 
  - Description: Resource title in manage header
  - 
  - Required model data:
  -   resourceContext
  -   
  - Optional model data:
  -   resourceTitle
  -   globalMenu
  -
  -->

<#import "/spring.ftl" as spring />

<#if !resourceContext?exists>
  <#stop "Unable to render model: required submodel
  'resourceContext' missing">
</#if>

<#import "/lib/menu/list-menu.ftl" as listMenu />

<#assign contentType>
  <@vrtx.resolveContentType contentType="${resourceContext.currentResource.contentType?replace('/', '-')}"/>
</#assign>

<div id="titleContainer" class="clear">
  <div class="resource-title ${contentType}">  
    <h1> 
      <#if (resourceTitle.title)?exists>${resourceTitle.title}<#else>${resourceContext.currentResource.name}</#if>
    </h1>
<#if globalMenu?exists>
    <@listMenu.listMenu menu=globalMenu displayForms=true prepend="(&nbsp;" append="&nbsp;)"/>
</#if>
    <#include "lock.ftl" />
  </div>
</div>

