<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#macro publishMessage resourceContext>
  <#local resource = resourceContext.currentResource />
  <#local propResource = vrtx.getProp(resourceContext.currentResource,"unpublishedCollection")! />
  <#if resourceContext.parentResource??>
    <#local propParent = vrtx.getProp(resourceContext.parentResource,"unpublishedCollection")! />
  </#if>

  <p>
    <#local notPublished = ((propResource?has_content || propParent?has_content) || !resource.published)  />
    <span class="<#if notPublished >unpublished<#else>published</#if>">
      <#if propResource?has_content && propResource.inherited >
        <#if resource.published >
          ${vrtx.getMsg("publish.unpublished.published")}
        <#else> 
          ${vrtx.getMsg("publish.unpublished.unpublishedCollection")}
        </#if>
      <#elseif propResource?has_content && !propParent?has_content>  
        ${vrtx.getMsg("publish.permission.unpublished")}
      <#elseif propParent?has_content >
        ${vrtx.getMsg("publish.unpublished.unpublishedCollection")}
      <#elseif resource.published>
        ${vrtx.getMsg("publish.permission.published")}
      <#else>
        ${vrtx.getMsg("publish.permission.unpublished")}
      </#if>
    </span>
    <#local resourceType = "resource">
    <#if resource.collection >
      <#local resourceType = "folder">
    </#if>
    <#if propResource?has_content && propResource.inherited >
      <#if resource.published >
        <abbr tabindex="0" class="tooltips" title="<@vrtx.msg code="publish.unpublished.published.info.${resourceType}" />"></abbr>
      <#else> 
        <abbr tabindex="0" class="tooltips" title="<@vrtx.msg code="publish.unpublished.unpublishedCollection.info.${resourceType}" />"></abbr>
      </#if>
    <#elseif propResource?has_content && !propParent?has_content>  
      <abbr tabindex="0" class="tooltips" title="<@vrtx.msg code="unpublishedCollection.info" />"></abbr>
    <#elseif propParent?has_content >
      <abbr tabindex="0" class="tooltips" title="<@vrtx.msg code="publish.unpublished.unpublishedCollection.info.${resourceType}" />"></abbr>
    </#if> 
  </p>
</#macro>
