<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/report.ftl" as reportLib />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <#if cssURLs?exists>
      <#list cssURLs as cssURL>
        <link rel="stylesheet" href="${cssURL}" type="text/css" />
      </#list>
    </#if>
    <#global baseFolder = "/" />
    <#if resourceContext.parentURI?exists>
      <#global baseFolder = resourceContext.currentURI?html />
    </#if>
    <!--[if lt IE 9]>
      <style type="text/css">
        #vrtx-report-filters ul {
          margin-right: 2%;
        }
      </style>
    <![endif]-->

    <script type="text/javascript"><!--
      var filtersAdvancedShow = "<@vrtx.msg code='report.broken-links.filters.advanced.show' />",
          filtersAdvancedHide = "<@vrtx.msg code='report.broken-links.filters.advanced.hide' />",
          filtersAdvancedTitle = "<@vrtx.msg code='report.broken-links.filters.advanced.title' />",
          filtersAdvancedExcludeTitle = "<@vrtx.msg code='report.broken-links.filters.advanced.exclude-title' />",
          filtersAdvancedIncludeTitle = "<@vrtx.msg code='report.broken-links.filters.advanced.include-title' />",
          filtersAdvancedUpdate = "<@vrtx.msg code='report.broken-links.filters.advanced.update' />";
          
      vrtxAdmin.multipleFormGroupingMessages = {
        add: "${vrtx.getMsg('editor.add')}",
        remove: "${vrtx.getMsg('editor.remove')}",
        moveUp: "${vrtx.getMsg('editor.move-up')}",
        moveDown: "${vrtx.getMsg('editor.move-down')}",
        browse: "${vrtx.getMsg('editor.browseImages')}"
      };
      vrtxAdmin.multipleFormGroupingPaths = {
        baseBrowserURL: "${fckeditorBase.url?html}",
        baseFolderURL: "${baseFolder}",
        basePath: "${fckBrowse.url.pathRepresentation}"
      };
      if(vrtxAdmin.hasFreeze) { // Make immutables
        Object.freeze(vrtxAdmin.multipleFormGroupingMessages);
        Object.freeze(vrtxAdmin.multipleFormGroupingPaths);
      }
   // -->
   </script>
   <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/plugins/mustache.js"></script>
   <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/editor.js"></script>
   <script type="text/javascript" src="/vrtx/__vrtx/static-resources/js/reports/broken-links.js"></script>
  </head>
  <body id="vrtx-report-broken-links">
  <#assign linkTypeLocalization>
    <@vrtx.msg code="report.broken-links.filters.link-type.${report.linkType}" />
  </#assign>

  <div class="resourceInfo">
    <div class="vrtx-report-nav">
      <div class="back">
        <a href="${serviceURL?html}" ><@vrtx.msg code="report.back" default="Back" /></a>
      </div>
    </div>
    <h2><@vrtx.msg code="report.${report.reportname}.title" /> ${linkTypeLocalization?lower_case} <@vrtx.msg code="report.${report.reportname}.postfix" default="ordered by subfolders" />
    <a id="vrtx-report-view-other" title="${vrtx.getMsg('manage.choose-location.choose-collection')}" href="${viewReportServiceURL?html}"><@vrtx.msg code="report.view-other-link" default="View other folder" />...</a></h2>
  </div>

  <@generateFilters report.filters />

  <#if report.map?has_content>
    <p id="vrtx-report-info-paging-top">
      <@vrtx.msg code="report.${report.reportname}.about"
                 args=[report.documentSum, report.sum]
                 default="There is a total of " + report.documentSum + " web pages with " + report.sum + " broken" /> ${linkTypeLocalization?lower_case}
    </p>
  </#if>
  
  <#if alternativeName?has_content>
    <@reportLib.alternativeViewCheckbox alternativeName "report.broken-links.switch-view" isAlternativeView />
  </#if>
  
  <#if report.map?has_content>  
    <div class="vrtx-report">
      <table id="directory-listing" class="report-broken-links-collection">
        <thead>
          <tr>
            <th scope="col" id="vrtx-report-broken-links-collection"><@vrtx.msg code="report.${report.reportname}.collection" /></th>
            <th scope="col" id="vrtx-report-broken-links-collection-document-count"><@vrtx.msg code="report.${report.reportname}.document-count" /></th>
            <th scope="col" id="vrtx-report-broken-links-collection-count">#${linkTypeLocalization?lower_case} <@vrtx.msg code="report.${report.reportname}.count" /></th>
          </tr>
        </thead>
        <tbody>
          <#list report.map?keys as uri>
            <#assign rowType = "odd" />
            <#if (uri_index % 2 == 0) >
              <#assign rowType = "even" />
            </#if>

            <#assign firstLast = ""  />
            <#if (uri_index == 0) && (uri_index == (report.map?size - 1))>
              <#assign firstLast = " first last" />
            <#elseif (uri_index == 0)>
              <#assign firstLast = " first" />
            <#elseif (uri_index == (report.map?size - 1))>
              <#assign firstLast = " last" />
            </#if>

            <tr class="${rowType}${firstLast}">
              <td class="vrtx-report-broken-links-collection">
                <a href="${report.map[uri].url?html}">${report.map[uri].title?html}</a>
                <span>${uri?html}</span>
              </td>
              <td class="vrtx-report-broken-links-collection-document-count">
                ${report.map[uri].documentCount}
              </td>
              <td class="vrtx-report-broken-links-collection-count">
                ${report.map[uri].linkCount}
              </td>
            </tr>
          </#list>
        </tbody>
      </table>
    </div>
  </#if>
  
  
  <#if report.prev?exists || report.next?exists || report.brokenLinksToTsvReportService?exists>
    <p id="vrtx-report-paging-bottom">
      <@displayPaging />
    </p>
  </#if>

  <#macro generateFilters filters>
    <#if filters?exists && (filters?size > 0)>
      <div id="vrtx-report-filters">
        <#list report.filters?keys as filterKey>
          <#local filterOpts = filters[filterKey] />
          <#if (filterOpts?size > 0)>
            <#if (filterKey_index == (filters?size - 1))>
              <ul class="vrtx-report-filter vrtx-report-filter-last" id="vrtx-report-filter-${filterKey}">
            <#else>
              <ul class="vrtx-report-filter" id="vrtx-report-filter-${filterKey}">
            </#if>
              <#list filterOpts as filterOpt>
                <#local filterID = "vrtx-report-filter-" + filterKey + "-" + filterOpt.name />
                <#if filterOpt.active>
                  <li class="active-filter" id="${filterID}">
                    <span><@vrtx.msg code="report.broken-links.filters.${filterKey}.${filterOpt.name}" /></span>
                <#else>
                  <li id="${filterID}">
                    <a href="${filterOpt.URL?html}"><@vrtx.msg code="report.broken-links.filters.${filterKey}.${filterOpt.name}" /></a>
                </#if>
                  </li>
              </#list>
            </ul>
          </#if>
        </#list>
      </div>
    </#if>
  </#macro>

  <#macro displayPaging>
    <#if report.prev?exists || report.next?exists>
      <span class="vrtx-report-paging">
        <#if report.prev?exists>
          <a href="${report.prev?html}" class="prev">
          <@vrtx.msg code="report.prev-page" default="Previous page" /></a><#if report.next?exists><a href="${report.next?html}" class="next"><@vrtx.msg code="report.next-page" default="Next page" /></a></#if>
        <#elseif report.next?exists>
          <a href="${report.next?html}" class="next"><@vrtx.msg code="report.next-page" default="Next page" /></a>
        </#if>
      </span>
    </#if>
    <#if report.brokenLinksToTsvReportService?exists>
      <a id="vrtx-report-broken-links-tsv" href="${report.brokenLinksToTsvReportService?html}">
        <@vrtx.msg code="report.${report.reportname}.tsv-file" default="Download as tab-separated file" />
      </a>
    </#if>
  </#macro>

  </body>
</html>
