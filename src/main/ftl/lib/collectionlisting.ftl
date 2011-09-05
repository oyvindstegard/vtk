<#ftl strip_whitespace=true>
<#--
  - File: collectionlisting.ftl
  - 
  - Description: Directory listing view component.
  -   Produces a table, with columns determined by the model variable
  -   'childInfoItems'. See Java class
  -   'org.vortikal.web.referencedataprovider.CollectionListingProvider'
  -   for the model definition used
  - 
  - Required model data:
  -   resourceContext
  -   collectionListing
  -   
  -  
  - Optional model data:
  -   
  -
  -->

<#import "vortikal.ftl" as vrtx />

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
          <th class="invertedSortColumn ${item}">
        <#else>
          <th class="sortColumn ${item}">
        </#if>
      <#else>
        <th class="${item}">
      </#if>
      <#switch item>
        <#case "last-modified">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.lastModified" default="Last Modified"/></a>
          <#break>

        <#case "locked">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.locked" default="Locked (by)"/></a>
          <#break>

        <#case "content-type">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.contentType" default="Content Type"/></a>
          <#break>
            
        <#case "resource-type">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.resourceType" default="Resource Type"/></a>
          <#break>

        <#case "owner">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.owner" default="Owner"/></a>
          <#break>
            
        <#case "permissions">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.permissions" default="Permissions"/></a>
          <#break>
            
        <#case "published">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="publish.permission.state" default="Status"/></a>
          <#break>

        <#case "content-length">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.size" default="Size"/></a>
          <#break>

        <#case "name">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.${item}" default="${item?cap_first}"/></a>
          <#if withForm>
            </th><th class="checkbox">
          </#if>
          <#break>
            
        <#case "title">
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.resourceTitle" default="Title"/></a>
          <#break>

        <#default>
          <a href="${collectionListing.sortByLinks[item]?html}" id="${item}">
            <@vrtx.msg code="collectionListing.${item}" default="${item?cap_first}"/></a>
      </#switch>
      </th>
    </#list>
    <#list collectionListing.linkedServiceNames as item>
      <th>
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
          <tr class="${rowType} <@vrtx.iconResolver child.resourceType child.contentType /> true${firstLast}">  
        <#else>
          <tr class="${rowType} <@vrtx.iconResolver child.resourceType child.contentType />${firstLast}">
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
                  <a href="${collectionListing.browsingLinks[child_index]?html}" title="${resourceTypeName}">
                    <span class="authorizedListedResource">${child.name}</span>
                  </a>
                  <#if withForm>
                    </td><td class="checkbox"><input name="${child.URI?html}" type="checkbox"/>
                  </#if>
                <#else>
                  <span class="unauthorizedListedResource">${child.name}</span>
                  <#if withForm>
                    </td><td class="checkbox">&nbsp;
                  </#if> 
                </#if>
                <#break>

              <#case "content-length">
                <#if child.isCollection()>
                  &nbsp;
                <#elseif child.contentLength <= 1000>
                  ${child.contentLength} B
                <#elseif child.contentLength <= 1000000>
                  ${(child.contentLength / 1000)?string("0.#")} KB
                <#elseif child.contentLength <= 1000000000>
                  ${(child.contentLength / 1000000)?string("0.#")} MB
                <#elseif child.contentLength <= 1000000000000>
                  ${(child.contentLength / 1000000000)?string("0.#")} GB
                <#else>
                  ${child.contentLength} B
                </#if>
                <#break>
            
              <#case "resource-type">
                ${vrtx.getMsg("resourcetype.name.${child.resourceType}")}
                <#break>

              <#case "last-modified">
                <#--${child.lastModified?string("yyyy-MM-dd HH:mm:ss")}-->
                ${child.lastModified?string("yyyy-MM-dd")}
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
                <#if restricted != "restricted" >
                  <span class="allowed-for-all">${vrtx.getMsg("collectionListing.permissions.readAll")}</span>
                <#else>
                  <span class="restricted">${vrtx.getMsg("collectionListing.permissions.restricted")}</span>
                </#if>
                <#break>
            
             <#case "published">
               <#if published?exists 
                 && child.collection?string == "false"
                 && child.contentType == "application/json">
                 <#if published == "true">
                   ${vrtx.getMsg("publish.permission.published")}
                 <#else>
                   ${vrtx.getMsg("publish.permission.unpublished")}
                 </#if>
               <#else>
                 <#if published == "true">
                   <span style="color: #bbb">${vrtx.getMsg("publish.permission.published")}</span>
                 <#else>
                   <span style="color: #bbb">${vrtx.getMsg("publish.permission.unpublished")}</span>
                 </#if>
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
            <#-- class="thickbox" --> 
      	    (&nbsp;<a href="${collectionListing.childLinks[child_index][item]?html}&amp;showAsHtml=true&amp;height=80&amp;width=230"
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
        <td colspan="5"><@vrtx.msg code="collectionListing.empty" default="This collection is empty"/></td>
      </tr>
    </#if> 
  </tbody>
</table>
 
<#if withForm>
  <div id="collectionListing.submit">
    <#list submitActions?keys as actionName>
      <input type="submit"
             value="${actionName?html}"
             id="collectionListing.action.${actionName?html}"
             name="action"
             title="${submitActions[actionName]?html}" />
      <#--
      <button type="submit" value="${actionName?html}" 
              id="collectionListing.action.${actionName?html}" name="action">
        ${submitActions[actionName]?html}
      </button>
      -->
    </#list>
  </div>
  </form>
</#if>

</#macro>