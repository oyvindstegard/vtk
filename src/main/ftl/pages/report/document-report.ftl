<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
  <#if cssURLs?exists>
    <#list cssURLs as cssURL>
      <link rel="stylesheet" href="${cssURL}" type="text/css" />
    </#list>
  </#if>
  </head>
  <body id="vrtx-report-documents">
  <div class="resourceInfo">
    <div class="vrtx-report-nav">
      <div class="back">
        <#if report.backURL?exists>
          <a href="${report.backURL}" ><@vrtx.msg code="report.${report.backURLname}" default="Back" /></a>
        <#else>
          <a href="${serviceURL}" ><@vrtx.msg code="report.back" default="Back" /></a>
        </#if>
      </div>
    </div>
    <h2>
      <@vrtx.msg code="report.${report.reportname}" />

      <#if (report.form)?exists>
        <form action="${(report.form.action)}" method="get">
          <#list report.form.inputs as input>
            <input name="${(input.name)?default('')}" type="${(input.type)?default('')}"
                   value="${(input.value)?default('')}" />
          </#list>
        </form>
      </#if>

      <#if !report.specificCollectionReporter??>
        <a id="vrtx-report-view-other" title="${vrtx.getMsg('manage.choose-location.choose-collection')}" href="${viewReportServiceURL}"><@vrtx.msg code="report.view-other-link" default="View other folder" /> ...</a>
      </#if>
    </h2>
  
  <#if (report.subtype?exists && report.subtype?size > 0)>
    <div id="vrtx-report-filters">
      <ul class="vrtx-report-filter vrtx-report-filter-last" id="vrtx-report-filter-${report.reportname}">
        <#list report.subtype as subtype>
          <#assign subTitle = vrtx.getMsg("report.${report.reportname}.subtype.${subtype.name}", "Critera: ${subtype.name}") />
          <#if subtype.active>
            <li id="vrtx-report-filter-${report.reportname}" class="active-filter"><span>${subTitle}</span></li>
          <#else>
            <li id="vrtx-report-filter-${report.reportname}-${subtype.name}"><a href="${subtype.url}">${subTitle}</a></li>
          </#if>
        </#list>
      </ul>
    </div>  
  </#if>
  
  <#if (report.result?exists && report.result?size > 0)>
    <p id="vrtx-report-info-paging-top">
      <@vrtx.msg code="report.${report.reportname}.about"
                 args=[report.from, report.to, report.total]
                 default="Listing results " + report.from + " - "
                 +  report.to + " of total " + report.total + " resources" />
    </p>
    <div class="vrtx-report">
    <table id="directory-listing" class="report-document-listing">
      <thead>
        <tr>
          <th scope="col" id="vrtx-report-name"><@vrtx.msg code="report.title" default="Name" /></th>
          <th scope="col" id="vrtx-report-last-modified"><@vrtx.msg code="report.last-modified" default="Last modified" /></th>
          <th scope="col" id="vrtx-report-modified-by"><@vrtx.msg code="report.modified-by" default="Modified by" /></th>
          <th scope="col" id="vrtx-report-permissions"><@vrtx.msg code="collectionListing.permissions" default="Permissions"/></th>
          <#if report.reportname != "unpublished">
            <th scope="col" id="vrtx-report-published"><@vrtx.msg code="report.published" default="Published" /></th>
          </#if>
        </tr>
      </thead>
      <tbody>
      <#assign count = 1 />
      <#assign collectionSize = report.result?size />
      <#list report.result as res >
        <#assign lastModifiedTime = vrtx.propValue(res, 'lastModified') />
        
        <#assign aclIsInherited = vrtx.getMsg("report.yes", "Yes")>
        <#if report.isInheritedAcl[res_index] >
          <#assign aclIsInherited = vrtx.getMsg("report.no", "No")>
        </#if>
        <#assign isReadRestricted = vrtx.getMsg("collectionListing.permissions.readAll") >
        <#if report.isReadRestricted[res_index] >
          <#assign isReadRestricted = vrtx.getMsg("collectionListing.permissions.restricted")>
        </#if>
        <#assign url = "">
        <#if report.viewURLs[res_index]?exists>
          <#assign url = report.viewURLs[res_index] />
        </#if>
        <#if report.reportname != "unpublished">
          <#assign published = vrtx.propValue(res, 'published')! />
          <#assign publishedStatus = vrtx.getMsg("report.yes", "Yes")>
          <#if published?has_content && published?markup_string = "false">
            <#assign publishedStatus = vrtx.getMsg("report.no", "No")>
          </#if>
        </#if>
        
          <#assign isCollection = vrtx.propValue(res, 'collection') />
          <#assign title = vrtx.propValue(res, 'title') />
        
          <#assign rowType = "odd" />
          <#if (res_index % 2 == 0) >
            <#assign rowType = "even" />
          </#if>

          <#assign firstLast = ""  />
          <#if (res_index == 0) && (res_index == (collectionSize - 1))>
            <#assign firstLast = " first last" />
          <#elseif (res_index == 0)>
            <#assign firstLast = " first" />
          <#elseif (res_index == (collectionSize - 1))>
            <#assign firstLast = " last" />
          </#if>
          
          <tr class="${rowType} <@vrtx.resourceToIconResolver res /> ${isCollection}${firstLast}">
            <td class="vrtx-report-name"><a href="${url}">${title}</a></td>
            <td class="vrtx-report-last-modified">${lastModifiedTime}</td>
            <td class="vrtx-report-last-modified-by">
              <#assign modifiedBy = vrtx.prop(res, 'modifiedBy').principalValue />
              <#if principalDocuments?? && principalDocuments[modifiedBy.name]??>
                <#assign principal = principalDocuments[modifiedBy.name] />
                <#if principal.URL??>
                  <a href="${principal.URL}">${principal.description}</a>
                <#else>
                  ${principal.description}
                </#if>
              <#else>
                <#assign modifiedByNameLink = vrtx.propValue(res, 'modifiedBy', 'link') />
                ${modifiedByNameLink}
              </#if>
            </td>
            <td class="vrtx-report-permissions permissions">
              <#assign hasTooltip = (report.permissionTooltips[res_index])?exists />
              <#if report.isReadRestricted[res_index] >
                <#assign permissionClass = "restricted" />
              <#else>
                <#assign permissionClass = "allowed-for-all" />
              </#if>
              <span class="${permissionClass}<#if hasTooltip> permission-tooltips</#if>"><#if hasTooltip><a href='javascript:void(0);' title='${report.permissionTooltips[res_index]}'></#if>${isReadRestricted}<#if hasTooltip></a></#if></span>
              <#if !report.isInheritedAcl[res_index]><span class="own-permission">&bull;</span></#if>
            </td>
            <#if report.reportname != "unpublished">
              <td class="vrtx-report-published">${publishedStatus}</td>
            </#if>
          </tr>
        </#list>
        </tbody>
      </table>
    </div>

    <#if report.prev?exists || report.next?exists>
      <p id="vrtx-report-paging-bottom">
        <@displayPaging />
      </p>
    </#if>
  <#else>
    <p><@vrtx.msg code="report.document-reporter.no.documents.found" /></p>
  </#if>
  
  </div>
  
  <#macro displayPaging>
    <span class="vrtx-report-paging">
      <#if report.prev?exists>
        <a href="${report.prev}" class="prev">
        <@vrtx.msg code="report.prev-page" default="Previous page" /></a><#if report.next?exists><a href="${report.next}" class="next"><@vrtx.msg code="report.next-page" default="Next page" /></a></#if>
      <#elseif report.next?exists>
        <a href="${report.next}" class="next"><@vrtx.msg code="report.next-page" default="Next page" /></a>
      </#if>
    </span>
  </#macro>
  
  </body>
</html>
