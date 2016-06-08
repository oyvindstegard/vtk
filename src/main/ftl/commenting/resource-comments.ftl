<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
  <head>
  
    <#assign title>
      <#compress>
        <#if resource.URI == '/'>
          <@vrtx.msg code='commenting.comments' args=[repositoryID] default='Comments' />
        <#else>
          <@vrtx.msg code='commenting.comments' args=[resource.title] default='Comments' />
        </#if>
      </#compress>
    </#assign>
    <#if feedURL?exists>
      <link type="application/atom+xml" rel="alternate" href="${feedURL}" title="${title}" />
    </#if>
    <#if cssURLs?exists>
      <#list cssURLs as cssUrl>
        <link href="${cssUrl}" type="text/css" rel="stylesheet" />
      </#list>
    </#if>
    <title>${title}</title>
    
    <meta name="robots" content="noindex"/> 
    
  </head>
  <body id="vrtx-recent-comments-view">
    <h1>${title}</h1>
    <ul class="comments">
    <#list comments as comment>
        <li class="comment"><h2>
          <a href="${(commentURLMap[comment.ID] + '#comment-' + comment.ID)}">
            <#-- XXX: look up principal -->
            ${comment.author}
	    <@vrtx.msg code="commenting.comments.on" default="about" />
            "${resourceMap[comment.URI].title}"
          </a></h2>
          <div class="comment">${comment.content?no_esc}</div>
          <span class="pubdate"><@vrtx.date value=comment.time format='long' /></span>
        </li>
    </#list>
    </ul>
	<#if feedURL?exists>
        <div class="vrtx-feed-link">
          <a id="vrtx-feed-link" href="${feedURL}"><@vrtx.msg code="viewCollectionListing.feed.fromThis" /></a>
        </div>
    </#if>
  </body>
</html>
