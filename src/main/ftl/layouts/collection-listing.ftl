<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#if (conf.auth && entries?exists && entries?size > 0)>
  <div class="vrtx-collection-listing-component-wrapper">
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/include-jquery.js"></script>
    <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/open-webdav.js"></script>
    <div class="vrtx-collection-listing-component">
    <#if (conf.folderTitle && folderTitle?exists)>
      <h3>${folderTitle?string}</h3>
    </#if>
      <#if conf.compactView>
        <table class="vrtx-collection-listing-table vrtx-collection-listing-table-compact">
      <#else>
        <table class="vrtx-collection-listing-table">
      </#if>
        <thead>
          <tr>
            <th scope="col" class="vrtx-collection-listing-title"><@vrtx.msg code="collectionListing.resourceTitle" default="Title" /></th>
          <#if !conf.compactView>
            <th scope="col" class="vrtx-collection-listing-modified-by"><@vrtx.msg code="collectionListing.lastModifiedBy" default="Modified by" /></th>
            <th scope="col" class="vrtx-collection-listing-last-modified"><@vrtx.msg code="collectionListing.lastModified" default="Last modified" /></th>
          </#if>
          </tr>
        </thead>
        <tbody>
        <#assign count = 1 />
        <#assign entriesCount = entries?size />
        <#list entries as entry >

          <#-- The actual resource we are displaying -->
          <#assign entryPropSet = entry.propertySet />

          <#assign title = vrtx.propValue(entryPropSet, 'title') />
          <#assign lastModifiedTime = vrtx.propValue(entryPropSet, 'lastModified') />

          <#assign rowType = "odd" />
          <#if (entry_index % 2 == 0) >
            <#assign rowType = "even" />
          </#if>

          <#assign firstLast = ""  />
          <#if (entry_index == 0) && (entry_index == (entriesCount - 1))>
            <#assign firstLast = " first last" />
          <#elseif (entry_index == 0)>
            <#assign firstLast = " first" />
          <#elseif (entry_index == (entriesCount - 1))>
            <#assign firstLast = " last" />
          </#if>

          <tr class="${rowType} ${firstLast}">
            <#if !conf.compactView>
              <td class="vrtx-collection-listing-title first-col">
            <#else>
              <td class="vrtx-collection-listing-title first-col last-col">
            </#if>
              <a class="vrtx-icon <@vrtx.resourceToIconResolver entryPropSet />" href="${entry.url}"></a>
              <a class="vrtx-title-link" href="${entry.url}">${title}</a>

            <#--
              Only local resources are ever evaluated for edit authorization.
              Use prop set path (uri) and NOT full entry url for link construction.
              See open-webdav.js
            -->
            <#if entry.editLocked>
              <span class="vrtx-resource-locked-webdav"><@vrtx.msg code="listing.edit.locked-by" /> ${entry.lockedByNameHref}</span>
            <#elseif entry.editAuthorized>
              <a class="vrtx-resource-open-webdav" href="${vrtx.linkConstructor(entryPropSet.URI, 'webdavService')}"><@vrtx.msg code="collectionListing.edit" /></a>
            </#if>
            <#if conf.compactView>
              <span>${lastModifiedTime}</span>
            </#if>
            </td>
          <#if !conf.compactView>
            <td class="vrtx-collection-listing-last-modified-by">
              <#assign modifiedBy = vrtx.prop(entryPropSet, 'modifiedBy').principalValue />
              <#if principalDocuments?exists && principalDocuments[modifiedBy.name]?exists>
                <#assign principal = principalDocuments[modifiedBy.name] />
                <#if principal.URL?exists>
                  <a href="${principal.URL}">${principal.description}</a>
                <#else>
                  ${principal.description}
                </#if>
              <#else>
                <#assign modifiedByNameLink = vrtx.propValue(entryPropSet, 'modifiedBy', 'link') />
                ${modifiedByNameLink}
              </#if>
            </td>
            <td class="vrtx-collection-listing-last-modified last-col">${lastModifiedTime}</td>
          </#if>
          </tr>
        </#list>
        </tbody>
      </table>
      <#if (conf.goToFolderLink && goToFolderLink?exists)>
        <a href="${goToFolderLink}"><@vrtx.msg code="collectionListing.goTo" default="Go to" /> ${folderTitle}</a>
      </#if>
    </div>
  </div>
</#if>
