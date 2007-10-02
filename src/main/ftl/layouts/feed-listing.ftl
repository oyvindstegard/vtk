<#import "/lib/vortikal.ftl" as vrtx />
<#if feed.entries?size &gt; 0 || conf.includeIfEmpty>
<div class="vrtx-feed">
  <#if conf.feedTitle?exists>
    <a class="feed-title" href="${feed.link}">${feed.title?html}</a> 
  </#if>

  <#if conf.feedDescription?exists>
    <div class="feed-description">${feed.description?html}</div> 
  </#if>

  <#if feed.entries?size gt 0>
    <#assign entries = feed.entries />
      <#if conf.sortByTitle?exists>
        <#assign entries = entries?sort_by("title") />
      </#if>
      <#assign maxMsgs = conf.maxMsgs />
      <#if entries?size lt maxMsgs>
        <#assign maxMsgs = entries?size />
      </#if>

     <ul class="items">
       <#list entries[0..maxMsgs-1] as entry>
         <li>
          <a class="item-title" href="${entry.link?html}">${entry.title?html}</a>
	  <#-- description -->
	  <#if conf.itemDescription?exists && (entry.description.value)?exists>
          <div class="item-description">
            ${entry.description.value}
          </div>
          </#if>
          <#if conf.publishedDate?exists && entry.publishedDate?exists>
          <span class="published-date">
            <@vrtx.date value=entry.publishedDate format="${conf.publishedDate}" />
          </span>
          </#if>
          <#if conf.displayChannel?exists>
          
            <a href="${feedMapping.getUrl(entry)}" class="channel">${feedMapping.getTitle(entry)?html}</a> 
          </#if>
         </li>
      </#list>
    </ul>
  </#if>

  <#if conf.bottomLinkToAllMessages?exists>
  <a class="all-messages" href="${feed.link}">
   <@vrtx.msg code="decorating.feedComponent.allMessages" default="More..." />
  </a>
  </#if>
</div>
</#if>
