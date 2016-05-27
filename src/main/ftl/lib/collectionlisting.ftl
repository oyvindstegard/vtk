<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: collectionlisting.ftl
  - 
  - Description: Directory listing view component.
  -   Produces a table, with columns determined by the model variable
  -   'childInfoItems'. See Java class
  -   'vtk.web.referencedataprovider.CollectionListingProvider'
  -   for the model definition used
  - 
  - Required model data:
  -   resourceContext
  -   collectionListing
  -  
  - Optional model data:
  -
  -->

<#import "vtk.ftl" as vrtx />

<#macro listCollection withForm=false action="" submitActions={}>

<#if !resourceContext.currentResource.collection>
  <#stop "This template only works with collection resources:
  ${resourceContext.currentResource.URI} is not a collection." />
</#if>
<#if !collectionListing?exists>
  <#stop "Unable to render model: required submodel
  'collectionListing' missing">
</#if>

<#if withForm>
  <form name="collectionListingForm" action="${action}" method="post" accept-charset="UTF-8">
</#if>

<table id="directory-listing" class="collection-listing">
  <thead>
   <tr>
   <#list collectionListing.childInfoItems as item>
      <#if collectionListing.sortedBy = item>
        <#if collectionListing.invertedSort>
          <th scope="col" class="invertedSortColumn ${item}">
        <#else>
          <th scope="col" class="sortColumn ${item}">
        </#if>
      <#else>
        <th scope="col" class="${item}">
      </#if>
      <#switch item>
        <#case "last-modified">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.lastModified" default="Last Modified"/></a>
          <#break>

        <#case "locked">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.locked" default="Locked (by)"/></a>
          <#break>

        <#case "content-type">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.contentType" default="Content Type"/></a>
          <#break>
            
        <#case "resource-type">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.resourceType" default="Resource Type"/></a>
          <#break>

        <#case "owner">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.owner" default="Owner"/></a>
          <#break>
            
        <#case "permissions">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.permissions" default="Permissions"/></a>
          <#break>
            
        <#case "published">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="publish.permission.state" default="Status"/></a>
          <#break>

        <#case "content-length">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.size" default="Size"/></a>
          <#break>

        <#case "name">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.${item}" default="${item?cap_first}"/></a>
          <#if withForm>
            </th><th scope="col" class="checkbox">
          </#if>
          <#break>
            
        <#case "title">
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.resourceTitle" default="Title"/></a>
          <#break>

        <#default>
          <a href="${collectionListing.sortByLinks[item]}" id="${item}">
            <@vrtx.msg code="collectionListing.${item}" default="${item?cap_first}"/></a>
      </#switch>
      </th>
    </#list>
    <#list collectionListing.linkedServiceNames as item>
      <th scope="col">
      </th>
    </#list>
    </tr>
  </thead>
  <tbody>
    <#assign rowType = "odd">
    <#assign collectionSize = collectionListing.children?size />
  
    <#if (collectionSize > 0)>
      <#list collectionListing.children as child>
  
        <#assign firstLast = ""  />
        <#if (child_index == 0) && (child_index == (collectionSize - 1))>
          <#assign firstLast = " first last" /> 
        <#elseif (child_index == 0)>
          <#assign firstLast = " first" />
        <#elseif (child_index == (collectionSize - 1))>
          <#assign firstLast = " last" /> 
        </#if>
  
        <#if child.collection>
          <tr id="resource--${child.name}" class="${rowType} <@vrtx.resourceToIconResolver child /> true${firstLast}">
        <#else>
          <tr id="resource--${child.name}" class="${rowType} <@vrtx.resourceToIconResolver child />${firstLast}">
        </#if>
    
        <#list collectionListing.childInfoItems as item>
          <#assign class = item >
          <#if item = "locked" && child.lock?exists>
            <#assign class = class + " activeLock">
          </#if>
      
          <#local restricted = "" />
          <#if item = "permissions">
            <#if child.isReadRestricted() >
              <#assign class = class + " restricted" />
              <#local restricted = "restricted">
            </#if>
          </#if>
      
          <#if item = "published">
            <#assign published = vrtx.propValue(child, "published") />
          </#if>
         
      
          <td class="${class}">
            <#switch item>
        
              <#case "title">
                ${child.title}
                <#break>

              <#case "name">
                <#if collectionListing.browsingLinks[child_index]?exists>
                  <#local resourceTypeName = vrtx.resourceTypeName(child) />
                  <a href="${collectionListing.browsingLinks[child_index]}" title="${resourceTypeName}">
                    <span class="authorizedListedResource">
                      ${child.name}
                    </span>
                  </a>
                  <#if withForm>
                    </td><td class="checkbox"><input name="${child.URI}" type="checkbox" />
                  </#if>
                <#else>
                  <span class="unauthorizedListedResource-wrapper">
                    <span class="unauthorizedListedResource">
                      ${child.name}
                    </span>
                  </span>
                  <#if withForm>
                    </td><td class="checkbox"><input name="${child.URI}" type="checkbox" />
                  </#if> 
                </#if>
                <#break>

              <#case "content-length">
                <#if child.isCollection()>
                  &nbsp;
                <#else>
                  <@vrtx.calculateResourceSize child.contentLength />
                </#if>
                <#break>
            
              <#case "resource-type">
                ${vrtx.getMsg("resourcetype.name.${child.resourceType}")}
                <#break>

              <#case "last-modified">
                ${vrtx.getPropValue(child, "lastModified", "short")}
                <#break>
              <#case "locked">
                <#if child.lock?exists>
                  <span class="lockOwner"></span>
                  <!-- span class="lockOwner">${child.lock.principal.name}</span -->
                </#if>
                <#break>

              <#case "content-type">
                <#if child.contentType != "application/x-vortex-collection">
                  ${child.contentType}
                </#if>
                <#break>

              <#case "owner">
                ${child.owner.name}
                <#break>
            
              <#case "permissions">
                <#assign hasTooltip = collectionListing.permissionTooltips[child_index]?exists />
                <#if restricted != "restricted" >
                  <span class="allowed-for-all<#if hasTooltip> permission-tooltips</#if>"><#if hasTooltip><a href='javascript:void(0);' title='${collectionListing.permissionTooltips[child_index]}'></#if>${vrtx.getMsg("collectionListing.permissions.readAll")}<#if hasTooltip></a></#if></span>
                <#else>
                  <span class="restricted<#if hasTooltip> permission-tooltips</#if>"><#if hasTooltip><a href='javascript:void(0);' title='${collectionListing.permissionTooltips[child_index]}'></#if>${vrtx.getMsg("collectionListing.permissions.restricted")}<#if hasTooltip></a></#if></span>
                </#if>
                <#if !child.isInheritedAcl()><span class="own-permission">&bull;</span></#if>
                <#break>
            
             <#case "published">
               <#if published?exists>
                    <#assign unpublishedCollection = vrtx.getPropValue(child, "unpublishedCollection")?exists && vrtx.getPropValue(child, "unpublishedCollection") == "true">
                      <span>
                       <#if published == "true">
                            ${vrtx.getMsg("publish.permission.published")}
                       <#else>
                            ${vrtx.getMsg("publish.permission.unpublished")}
                       </#if>
                     </span>
                 </#if>
               <#break>
          </#switch>
        </td>
      </#list>
    
      <#list collectionListing.linkedServiceNames as item>
        <td class="${item}">
          <#if collectionListing.childLinks[child_index][item]?has_content>
            <#assign actionName =
                     vrtx.getMsg("collectionListing.action." + item, item, [item, child.name]) />
            <#assign confirmation =
                     vrtx.getMsg("collectionListing.confirmation." + item,
                                 "Are you sure you want to " + item + " " + child.name + "?", 
                                 [child.name]) />
            <#if child.isCollection()>
              <#assign titleMsg = vrtx.getMsg("confirm-delete.title.folder") />
            <#else>
              <#assign titleMsg = vrtx.getMsg("confirm-delete.title.file") />
            </#if>
      	    (&nbsp;<a href="${collectionListing.childLinks[child_index][item]}"
      	              title="${titleMsg}">${actionName}</a>&nbsp;)
	      </#if>
        </td>
      </#list>
      </tr>
      
      <#if rowType = "even">
        <#assign rowType = "odd">
      <#else>
        <#assign rowType = "even">
      </#if>
    </#list>
    <#else>
      <tr id="collectionlisting-empty" class="first last">
        <td colspan="6"><@vrtx.msg code="collectionListing.empty" default="This collection is empty"/></td>
      </tr>
    </#if> 
  </tbody>
</table>
 
<#if withForm>
  <div id="collectionListing.submit">
    <#list submitActions?keys as actionName>
      <input type="submit"
             value="${actionName}"
             id="collectionListing.action.${actionName}"
             name="action"
             title="${submitActions[actionName]}" />
    </#list>
  </div>
  </form>
</#if>
</#macro>
