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
      <#global baseFolder = resourceContext.currentURI />
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
      	baseBrowserURL: "${fckeditorBase.url}",
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
    <@vrtx.msg code="report.${report.reportname}.filters.link-type.${report.linkType}" />
  </#assign>
  
  <div class="resourceInfo">
    <div class="vrtx-report-nav">
      <div class="back">
        <a href="${serviceURL}" ><@vrtx.msg code="report.back" default="Back" /></a>
      </div>
    </div>
    <h2><@vrtx.msg code="report.${report.reportname}.title" /> ${linkTypeLocalization?markup_string?lower_case}
    <a id="vrtx-report-view-other" title="${vrtx.getMsg('manage.choose-location.choose-collection')}" href="${viewReportServiceURL}"><@vrtx.msg code="report.view-other-link" default="View other folder" />...</a>
    </h2>
  </div>
  
  <@reportLib.generateFilters report.filters />

  <#if report.result?has_content>
    <#--
      <p id="vrtx-report-broken-links-info">
        <span class="vrtx-report-broken-links-info-number">${report.brokenLinkCount} <@vrtx.msg code="report.${report.reportname}.info.total-broken-links-count" /></span>
        <span class="vrtx-report-broken-links-info-number">${report.total}</span> <@vrtx.msg code="report.${report.reportname}.info.web-pages.num" />
        <span class="vrtx-report-broken-links-info-number">7%</span> av nettsidene med brutte lenker
      </p>
    -->
    <p id="vrtx-report-info-paging-top">
      <@vrtx.msg code="report.${report.reportname}.about"
                 args=[report.from, report.to, report.total, report.brokenLinkCount]
                 default="Listing results " + report.from + "–"
                 + report.to + " of total " + report.total + " of web pages with " + report.brokenLinkCount + " broken" /> ${linkTypeLocalization?markup_string?lower_case}
    </p>

    <#if alternativeName?has_content>
      <@reportLib.alternativeViewCheckbox alternativeName "report.broken-links.switch-view" isAlternativeView />
    </#if>
  
    <div class="vrtx-report">
      <table id="directory-listing" class="report-broken-links">
        <thead>
          <tr>
            <th scope="col" id="vrtx-report-broken-links-web-page"><@vrtx.msg code="report.${report.reportname}.web-page" /></th>
            <th scope="col" id="vrtx-report-broken-links-count"><@vrtx.msg code="report.${report.reportname}.count" /></th>
            <th scope="col" id="vrtx-report-broken-links">${linkTypeLocalization} <@vrtx.msg code="report.${report.reportname}.list" /></th>
            <th scope="col" id="vrtx-report-last-checked"><@vrtx.msg code="report.broken-links.last-checked" default="Last checked" /></th>
          </tr>
        </thead>
        <tbody>
        <#assign brokenLinksSize = report.result?size />
        <#list report.result as item>
          <#assign title = vrtx.propValue(item, 'title')!'' />
          <#assign url = "" />
          <#assign timestamp = "" />
          <#if report.viewURLs[item_index]?exists>
            <#assign url = report.viewURLs[item_index] />
          </#if>
          <#if (report.linkCheck[item.URI])?exists>
            <#assign linkCheck = report.linkCheck[item.URI] />
            <#if linkCheck['brokenLinks']?exists>
              <#assign brokenLinks = linkCheck['brokenLinks'] />
            </#if>
            <#if linkCheck['timestamp']?exists>
              <#assign timestamp = linkCheck['timestamp'] />
            </#if>
          </#if>
          <#assign linkStatus = vrtx.propValue(item, 'link-status')!'' />

          <#assign rowType = "odd" />
          <#if (item_index % 2 == 0) >
            <#assign rowType = "even" />
          </#if>

          <#assign firstLast = ""  />
          <#if (item_index == 0) && (item_index == (brokenLinksSize - 1))>
            <#assign firstLast = " first last" />
          <#elseif (item_index == 0)>
            <#assign firstLast = " first" />
          <#elseif (item_index == (brokenLinksSize - 1))>
            <#assign firstLast = " last" />
          </#if>
          
          <#assign brokenLinksList>
            <#assign onlyCount = false />
            <#assign countedBrokenLinks = 0 />
            <#if brokenLinks?exists>
              <ul>
                <#list brokenLinks as link>
                  <#if link?is_hash>
                    <#if (link.link)?exists>
                      <#if (report.linkType == "anchor-img" &&  (link.type == "ANCHOR" || link.type == "IMG" || link.type == "PROPERTY"))
                        || (report.linkType == "anchor" && link.type == "ANCHOR")
                        || (report.linkType == "img"    && (link.type == "IMG" || link.type == "PROPERTY"))
                        || (report.linkType == "other"  && link.type != "ANCHOR" && link.type != "IMG" && link.type != "PROPERTY")>
                        <#assign countedBrokenLinks = countedBrokenLinks + 1 />
                        <#if !onlyCount>
                          <li>${link.link}<#if (link.status)?exists><!--${link.status}--></#if></li>
                        </#if>
                      </#if>
                    </#if>
                  <#else>
                    <#assign countedBrokenLinks = countedBrokenLinks + 1 />
                    <#if !onlyCount>
                      <li>${link}</li>
                    </#if>
                  </#if>
                  <#if (countedBrokenLinks > 9 && !onlyCount)>
                    <li>...</li>
                    <#assign onlyCount = true />
                  </#if>
                </#list>
              </ul>
            </#if>
          </#assign>
   
          <tr class="${rowType}${firstLast}">
            <td class="vrtx-report-broken-links-web-page">
              <a href="${url}">${title}</a>
              <span>${item.URI}</span>
            </td>
            <td class="vrtx-report-broken-links-count">
              ${countedBrokenLinks}
            </td>
            <td class="vrtx-report-broken-links">
              <#if brokenLinksList?exists>
                ${brokenLinksList}
              </#if>
            </td>
            <td class="vrtx-report-last-modified">
              <#if timestamp != "">
                <@vrtx.date value=timestamp?datetime('yyyyMMdd HH:mm:ss') format="long" />
              </#if>
              <#if timestamp != "" && linkStatus = 'AWAITING_LINKCHECK'>
                - 
              </#if>
              <#if linkStatus = 'AWAITING_LINKCHECK'>
                <@vrtx.msg code="report.broken-links.awaiting-linkcheck" />
              </#if>
            </td>
          </tr>
        </#list>
        </tbody>
      </table>
    </div>
    <#if report.prev?exists || report.next?exists || report.brokenLinksToTsvReportService?exists>
      <p id="vrtx-report-paging-bottom">
        <@displayPaging />
      </p>
    </#if>
  <#else>
    <p id="vrtx-report-broken-links-info"><@vrtx.msg code="report.${report.reportname}.info.web-pages.none-found" /> ${linkTypeLocalization?markup_string?lower_case}.<span class="vrtx-report-broken-links-info-number">&nbsp;</span></p>
  </#if>

  <#macro displayPaging>
    <#if report.prev?exists || report.next?exists>
      <span class="vrtx-report-paging">
        <#if report.prev?exists>
          <a href="${report.prev}" class="prev">
          <@vrtx.msg code="report.prev-page" default="Previous page" /></a><#if report.next?exists><a href="${report.next}" class="next"><@vrtx.msg code="report.next-page" default="Next page" /></a></#if>
        <#elseif report.next?exists>
          <a href="${report.next}" class="next"><@vrtx.msg code="report.next-page" default="Next page" /></a>
        </#if>
      </span>
    </#if>
  </#macro>
  
  </body>
</html>
