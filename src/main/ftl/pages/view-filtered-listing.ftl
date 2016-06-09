<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <#if cssURLs?exists>
      <#list cssURLs as cssURL>
        <link rel="stylesheet" href="${cssURL}" type="text/css" />
      </#list>
    </#if>
    <#if printCssURLs?exists>
      <#list printCssURLs as cssURL>
        <link rel="stylesheet" href="${cssURL}" media="print" type="text/css" />
      </#list>
    </#if>
    <#if jsURLs?exists>
      <#list jsURLs as jsURL>
        <script type="text/javascript" src="${jsURL}"></script>
      </#list>
    </#if>

    <title>${collection.title}<#if collectionSpecificValues?exists && collectionSpecificValues.currentUrl?exists> (${vrtx.getMsg("listing-filters.title.discontinued")})</#if></title>

    <#if page?has_content>
      <#if "${page}" != "1"><meta name="robots" content="noindex, follow"/></#if>
    </#if>
  </head>
  <body id="vrtx-${collection.resourceType}">
  
    <#assign additionalContent = vrtx.propValue(collection, "additionalContents")! />
    <#if additionalContent?has_content>
      <div id="vrtx-content">
        <div id="vrtx-main-content">
    </#if>
  
    <@vrtx.displayLinkOtherLang collection />
    <h1>${collection.title}<#if collectionSpecificValues?exists && collectionSpecificValues.currentUrl?exists> (${vrtx.getMsg("listing-filters.title.discontinued")})</#if></h1>
    <#if showSubfolderMenu?exists>
      <div class="vrtx-subfolder-menu">
        <#if showSubfolderMenu.resultSets?has_content>
          <div class="menu-title"><#if showSubfolderTitle?exists>${showSubfolderTitle}<#else>${vrtx.getMsg("viewCollectionListing.subareas")}</#if></div>
          <#assign currentCount = 1 />
          <#list showSubfolderMenu.resultSets as resultSet>
            <ul class="resultset-${currentCount}">
              <#list resultSet.itemsSorted as item>
                <#if item.url?exists && item.label?exists>
                  <li><a href="${item.url}">${item.label}</a></li>
                </#if>
              </#list>
            </ul>
            <#assign currentCount = currentCount + 1 />
          </#list>
        </#if>
      </div>
    </#if>

    <#if filters?exists>
      <div id="vrtx-listing-filters" class="vrtx-listing-filters-${filters?size}-col<#if showSubfolderMenu?exists> vrtx-listing-filters-collapsed</#if>">
        <script type="text/javascript"><!--
          var listingFilters = [<#list filters?keys as filterKey>"${filterKey}"<#if filterKey_has_next>, </#if></#list>];
        // -->
        </script>
        <#list filters?keys as filterKey>
          <#assign filter = filters[filterKey]>
          <div class="vrtx-listing-filters-section <#if (filterKey_index = (filters?size - 1))>vrtx-listing-filters-section-last</#if>" id="vrtx-listing-filters-section-${filterKey}">
            <#if useFilterTitle?? && useFilterTitle>
              <h2>${vrtx.getMsg("listing-filters.${filterKey}.title")}</h2>
            </#if>
            <ul>
            <#list filter?keys as parameterKey>
              <#assign url = filter[parameterKey].url>
              <#assign marked = filter[parameterKey].marked>
              <li id="vrtx-listing-filter-parameter-${filterKey}-${parameterKey}" class="vrtx-listing-filter-parameter<#if parameterKey = "all"> vrtx-listing-filter-parameter-all</#if><#if marked> vrtx-listing-filter-parameter-selected</#if>">
                <#if parameterKey = "all">
                  <a href="${url}">${vrtx.getMsg("listing-filters.${filterKey}.all")}</a>
                <#elseif filterKey = "semester"><#-- TODO: Hack to avoid year in i18n -->
                  <#if parameterKey?starts_with("v")>
                    <a href="${url}">${vrtx.getMsg("listing-filters.${filterKey}.filter.v")} 20${parameterKey?substring(1)}</a>
                  <#elseif parameterKey?starts_with("h")>
                    <a href="${url}">${vrtx.getMsg("listing-filters.${filterKey}.filter.h")} 20${parameterKey?substring(1)}</a>
                  <#else>
                    <a href="${url}">${vrtx.getMsg("listing-filters.${filterKey}.filter.s")} 20${parameterKey?substring(1)}</a>
                  </#if>
                <#else>
                  <a href="${url}">${vrtx.getMsg("listing-filters.${filterKey}.filter.${parameterKey}")}</a>
                </#if>
              </li>
            </#list>
            </ul>
          </div>
        </#list>
      </div>
    </#if>

    <#if (result?exists && result?has_content)>
      <#if from?exists && to?exists && total?exists>
        <div id="vrtx-listing-filter-hits">
          <@vrtx.msg code="listing-filters.${collection.resourceType}.from-to-total" args=[from, to, total] default="Showing " + from + "-" +  to + " of " + total + " resources" />
        </div>
      </#if>

      <div id="vrtx-listing-filter-results">
        <#if collection.resourceType = 'course-group-listing'>
          <#import "/pages/studies/view-course-group-listing.ftl" as courseGroup />
          <@courseGroup.displayResult result />
        <#elseif collection.resourceType = 'course-description-listing'>
          <#import "/pages/studies/view-course-description-listing.ftl" as courseDescription />
          <@courseDescription.displayResult result />
        <#elseif collection.resourceType = 'student-exchange-agreement-listing'>
          <#import "/pages/studies/view-student-exchange-agreement-listing.ftl" as studentExchangeAgreement />
          <@studentExchangeAgreement.displayResult result />
        <#else>
          <ul>
            <#list result as res>
              <#assign title = vrtx.propValue(res, 'title') />
              <#assign uri = vrtx.getUri(res) />
              <li><#if title?has_content && uri?has_content><a href="${uri}">${title}</a></#if></li>
            </#list>
          </ul>
        </#if>
      </div>

      <#if pageThroughUrls?exists && (pageThroughUrls?size > 1) >
        <div class="vrtx-paging-feed-wrapper">
          <@viewutils.displayPageThroughUrls pageThroughUrls page />
        </div>
      </#if>
    <#else>
      <div id="vrtx-listing-filter-no-results">
        <@vrtx.msg code="listing-filters.${collection.resourceType}.no-result" default="No result" />
      </div>
    </#if>

    <#if collectionSpecificValues?exists>
      <div id="vrtx-listing-filter-status">
        <#if collectionSpecificValues.discontinuedUrl?exists>
          <a href="${collectionSpecificValues.discontinuedUrl}">${vrtx.getMsg("listing-filters.status.filter.${collection.resourceType}.discontinued")}</a>
        <#elseif collectionSpecificValues.currentUrl?exists>
          <a href="${collectionSpecificValues.currentUrl}">${vrtx.getMsg("listing-filters.status.filter.${collection.resourceType}.current")}</a>
        </#if>
      </div>
    </#if>
    
    <#if additionalContent?has_content>
        </div><#-- end vrtx-main-content -->
        <div id="vrtx-additional-content">
          <div id="vrtx-related-content"> 
            <@vrtx.invokeComponentRefs additionalContent />
          </div>
        </div>
      </div><#-- end vrtx-content -->
    </#if>
    
  </body>
</html>
