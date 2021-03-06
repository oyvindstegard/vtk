<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#macro displayCollection collectionListing>

  <#local entries=collectionListing.entries />

  <#if (entries?size > 0)>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/include-jquery.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/open-webdav.js"></script>
    <div id="${collectionListing.name}" class="vrtx-resources ${collectionListing.name}">
      <#if collectionListing.title?exists && collectionListing.offset == 0>
        <h2>${collectionListing.title}</h2>
      </#if>
    
      <#list entries as entry>

        <#-- The actual resource we are displaying -->
        <#local entryPropSet = entry.propertySet />
        <#assign url = entry.url />
        <#assign linkURL = url />

        <#-- XX: Provide a way to construct link with assertion
             matching using PropertySet objects: -->
        <#if vrtx.getProp(entryPropSet, "contentType")?exists>
          <#local contentType = vrtx.getProp(entryPropSet, "contentType").value />
          <#if contentType?starts_with("image/")
               || contentType?starts_with("audio/")
               || contentType?starts_with("video/")>

            <#assign linkURL = vrtx.linkConstructor(url.path, 'viewAsWebPageService') />
          </#if>
        </#if>

        <#if !hideIcon?exists>
          <div class="vrtx-resource vrtx-resource-icon">
        <#else>
          <div class="vrtx-resource">
        </#if>
        <#if !hideIcon?exists>
      <a class="vrtx-icon <@vrtx.resourceToIconResolver entryPropSet />" href="${url}"></a>
    </#if> 
      
    <div class="vrtx-title">
      <#assign title = vrtx.propValue(entryPropSet, "title", "", "")! />
      <#if !title?has_content>
        <#assign title = vrtx.propValue(entryPropSet, "solr.name", "", "")!"" />
      </#if>
      <a class="vrtx-title vrtx-title-link" href="${linkURL}">${title}</a>

      <#--
        Only local resources are ever evaluated for edit authorization.
        Use prop set path (uri) and NOT full entry url for link construction.
        See open-webdav.js
      -->
      <#if entry.editLocked>
        <span class="vrtx-resource-locked-webdav"><@vrtx.msg code="listing.edit.locked-by" /> ${entry.lockedByNameHref}</span>
      <#elseif entry.editAuthorized>
        <a class="vrtx-resource-open-webdav" href="${vrtx.linkConstructor(entryPropSet.URI, 'webdavService')}"><@vrtx.msg code="collectionListing.editlink" /></a>
      </#if>
    </div>

    <#local lastModified = vrtx.propValue(entryPropSet, "lastModified", "long")! />

    <#if lastModified?has_content && !collectionListing.hasDisplayPropDef("hide-last-modified")>
      <#assign val>
        <@vrtx.msg code="viewCollectionListing.lastModified" args=[lastModified] />
      </#assign>
      <#assign modifiedBy = vrtx.prop(entryPropSet, 'modifiedBy').principalValue />
      <#if principalDocuments?exists && principalDocuments[modifiedBy.name]?exists>
        <#assign principal = principalDocuments[modifiedBy.name] />
        <div class="lastModified">
          <@vrtx.msg code="viewCollectionListing.lastModified" args=[lastModified] />
          <#if principal.URL?exists>
            <a href="${principal.URL}">${principal.description}</a>
          <#else>
            ${principal.description}
          </#if>
        </div>
      <#else>
        <div class="lastModified">
          <@vrtx.msg code="viewCollectionListing.lastModified" args=[lastModified] />
          ${vrtx.propValue(entryPropSet, 'modifiedBy', 'link')?no_esc!''}
        </div>
      </#if>
    </#if>

    <#local introduction = vrtx.getIntroduction(entryPropSet)! />
    <#if introduction?has_content && !collectionListing.hasDisplayPropDef("hide-introduction")>
      <div class="introduction">
        ${introduction?no_esc}
      </div>
    </#if>

        </div>
      </#list>
    </div>
  </#if>
</#macro>
