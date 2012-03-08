<#ftl strip_whitespace=true>
<#import "/lib/vortikal.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <#if cssURLs?exists>
      <#list cssURLs as cssURL>
        <link rel="stylesheet" href="${cssURL}" />
      </#list>
    </#if>
  </head>
  <body id="vrtx-report-broken-links">
  <div class="resourceInfo">
    <div class="vrtx-report-nav">
      <div class="back">
        <a href="${serviceURL?html}" ><@vrtx.msg code="report.back" default="Back" /></a>
      </div>
    </div>
    <h2><@vrtx.msg code="report.${report.reportname}" /></h2>
  </div>
  
  <div id="vrtx-report-filters">
    <ul class="vrtx-report-filter" id="vrtx-report-filter-published">
      <li><a href="${report.backURL?html}">Alle</a></li>
      <li><a href="${report.backURL?html}&published=false">Upubliserte</a></li>
    </ul>
    <ul class="vrtx-report-filter" id="vrtx-report-filter-read-restriction">
      <li><a href="${report.backURL?html}">Åpne og lukkede</a></li>
      <li><a href="${report.backURL?html}&read-restriction=false">Åpne</a></li>
      <li><a href="${report.backURL?html}&read-restriction=true">Lukkede</a></li>
    </ul>
  </div>

  <#if (report.result?exists && report.result?size > 0)>
    <p id="vrtx-report-info-paging-top">
      <@vrtx.msg code="report.${report.reportname}.about"
                 args=[report.from, report.to, report.total]
                 default="Listing results " + report.from + " - "
                 +  report.to + " of total " + report.total + " resources" />
      <#if report.prev?exists || report.next?exists>
        <@displayPaging />  
      </#if>
    </p>
    <div class="vrtx-report">
      <table id="directory-listing" class="report-broken-links">
        <thead>
          <tr>
            <th id="vrtx-report-broken-links-web-page"><@vrtx.msg code="report.${report.reportname}.web-page" /></th>
            <th id="vrtx-report-broken-links-count"><@vrtx.msg code="report.${report.reportname}.count" /></th>
            <th id="vrtx-report-broken-links"><@vrtx.msg code="report.${report.reportname}.these" /></th>
          </tr>
        </thead>
        <tbody>
        <#assign brokenLinksSize = report.result?size />
        <#list report.result as item>
          <#assign title = vrtx.propValue(item, 'title') />
          <#assign url = "">
          <#if report.viewURLs[item_index]?exists>
            <#assign url = report.viewURLs[item_index] />
          </#if>
          <#if (report.linkCheck[item.URI])?exists>
            <#assign linkCheck = report.linkCheck[item.URI] />
            <#if linkCheck['brokenLinks']?exists>
              <#assign brokenLinks = linkCheck['brokenLinks'] />
            </#if>
          </#if>
          <#assign linkStatus = vrtx.propValue(item, 'link-status') />
          
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
          
          <#assign restricted = "">
          <#if report.isReadRestricted[item_index]>
            <#assign restricted = " restricted">
          <#else>
            <#assign restricted = " allowed-for-all">
          </#if>  
          <#assign published = "">
          <#if vrtx.propValue(item, "published") = "true">
            <#assign published  = " published">
          <#else>
            <#assign published  = " unpublished">
          </#if>
   
          <tr class="${rowType}${firstLast}${published}${restricted}">
            <td class="vrtx-report-broken-links-web-page">
              <a href="${url?html}">${title?html}</a>
              <#if linkStatus = 'AWAITING_LINKCHECK'>
                [ * ] <#-- currently being checked, be patient -->
              </#if>
            </td>
            <td class="vrtx-report-broken-links-count">
              <#if brokenLinks?exists>${brokenLinks?size}</#if> 
            </td>
            <td class="vrtx-report-broken-links">
              <#if brokenLinks?exists>
                <ul>
                <#list brokenLinks as link>
                  <li>${link?html}</li>
                  <#if link_index &gt; 10>
                    <li>...</li>
                    <#break />
                  </#if>
                </#list>
                </ul>
              </#if>
            </td>
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

  <#macro displayPaging>
    <span class="vrtx-report-paging">
      <#if report.prev?exists>
        <a href="${report.prev?html}">
        <@vrtx.msg code="report.prev-page" default="Previous page" /></a><#if report.next?exists>&nbsp;&nbsp;&nbsp;<a href="${report.next?html}"><@vrtx.msg code="report.next-page" default="Next page" /></a></#if>
      <#elseif report.next?exists>
        <a href="${report.next?html}"><@vrtx.msg code="report.next-page" default="Next page" /></a>
      </#if>
    </span>
  </#macro>
  
  </body>
</html>
