<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: comments-feed-url.ftl
  - 
  - Description: Inserts a <link> to the comments Atom feed
  - 
  - Required model data:
  -   resourceContext
  -   commentsFeed
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#assign linkTitle>
  <@vrtx.msg code="commenting.comments"
             args=[vrtx.propValue(resourceContext.currentResource, "title", "flattened")] />
</#assign>
<#if commentsEnabled?exists && commentsEnabled >
  <link type="application/atom+xml" rel="alternate" href="${commentsFeed.url}" title="${linkTitle}" />
</#if>
