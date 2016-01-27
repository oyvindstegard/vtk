<#ftl strip_whitespace=true>

<#import "/lib/vtk.ftl" as vrtx />
<#if conf.auth && (feed.entries?size &gt; 0 || conf.includeIfEmpty)>
<div class="vrtx-feed<#if conf.itemPicture?exists > with-images</#if>">
  <#if overrideFeedTitle?exists && viewURL?exists>
    <a class="feed-title" href="${viewURL}">${overrideFeedTitle}</a>
  <#elseif conf.feedTitle?exists && viewURL?exists>
    <a class="feed-title" href="${viewURL}">${feed.title?html}</a> 
  <#elseif conf.feedTitleValue?exists>
    <div class="feed-title">${conf.feedTitleValue?html}</div> 
  </#if>

  <#if conf.feedDescription?exists && feed.description?exists>
    <div class="feed-description">${feed.description}</div> 
  </#if>
  
  <#if feed.entries?size gt 0>
    <#assign entries = feed.entries />
      <#if conf.sortByTitle?exists>
        <#assign entries = entries?sort_by("title") />
        <#if !conf.sortAscending?exists>
          <#-- Reverse order, descending sort requested, and ascending is default -->
          <#assign entries = entries?reverse />
        </#if> 
      <#else>
        <#if (conf.sortDescending)?exists && conf.sortDescending>
          <#assign entries = entries?sort_by("publishedDate")?reverse />

        <#elseif (conf.sortAscending)?exists && conf.sortAscending>
          <#assign entries = entries?sort_by("publishedDate") />
        </#if>
      </#if>

      <#assign maxMsgs = conf.maxMsgs />
      <#if entries?size lt maxMsgs>
        <#assign maxMsgs = entries?size />
      </#if>

     <ul class="items">
       <#assign "counter" = 1>
       <#list entries[0..maxMsgs-1] as entry>
         <#if counter == maxMsgs>
           <li class="item-${counter} item-last">
         <#else>
           <li class="item-${counter}">
         </#if>
         <#list elementOrder as element >
           <@displayEntry entry conf element />
         </#list>
         </li>
         <#assign counter = counter+1>
      </#list>
    </ul>
  </#if>
  
  <#if displayIfEmptyMessage?exists && feed.entries?size = 0>
   <p class="vrtx-empty-message">
     ${displayIfEmptyMessage}
   </p>
  </#if>

  <#if conf.linkToAllMessages?exists && viewURL?exists>
  <a class="all-messages" href="${viewURL}">
   <@vrtx.msg code="decorating.feedComponent.allMessages" default="More..." />
  </a>
  </#if>
</div>
</#if>

<#macro displayEntry entry conf element>
  <#local href="${entry.link?default('')}" />
  <#if element = "title" >
    <#if href != ''>
      <a class="item-title" href="${href?html}">${entry.title?trim?html}</a>
    <#else>
      ${entry.title?trim?html}
    </#if>
  </#if>

  <#if element = "publishDate" && conf.publishedDate?exists>
    <#-- Display start date instead of published date (for events): -->
    <#assign eventDate = false />
    <#if (entry.foreignMarkup??)>
      <#list (entry.foreignMarkup) as el>
        <#if el.qualifiedName == 'v:event-start'>
          <#assign eventDate = true />
          <#assign dateObj = el.text?datetime.iso />
          <span class="published-date">
          <@vrtx.date value=dateObj format="${conf.publishedDate}" />
          </span>
          <#break />
        </#if>
      </#list>
    </#if>
    <#-- Regular published date: -->
    <#if !eventDate && entry.publishedDate?exists>
    <span class="published-date">
       <@vrtx.date value=entry.publishedDate format="${conf.publishedDate}" />
    </span>
    </#if>
  </#if>

  <#if element = "categories" >
     <#if conf.displayCategories?exists && (entry.categories)?exists && (entry.categories)?size &gt; 0>
       <ul class="categories">
         <#list entry.categories as category>
           <li>${category.name}</li>
         </#list>
       </ul>
     </#if>
  </#if>
  
  <#if element = "channel" >
     <#if conf.displayChannel?exists>
       <#if conf.publishedDate?exists && entry.publishedDate?exists> - </#if><a href="${feedMapping.getUrl(entry)}" class="channel">${feedMapping.getTitle(entry)?html}</a> 
     </#if>
  </#if>
  
  <#if element = "description" && conf.itemDescription?exists && descriptionNoImage[entry]?exists && descriptionNoImage[entry]?has_content>
    <div class="item-description">
       ${descriptionNoImage[entry]?string}
    </div>
  </#if>

  <#if element = "picture" && conf.itemPicture?exists && imageMap[entry]?exists && imageMap[entry]?has_content >
    <#if href != ''>
     <a class="vrtx-image" href="${href?html}">${imageMap[entry]?string}</a>
    <#else>
      ${imageMap[entry]?string}
    </#if>
  </#if>
</#macro>
