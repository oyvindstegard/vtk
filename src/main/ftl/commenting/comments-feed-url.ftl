<#ftl strip_whitespace=true>
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
<#import "/lib/vortikal.ftl" as vrtx />
<#assign linkTitle>
  <@vrtx.msg code="commenting.comments"
             args=[vrtx.propValue(resourceContext.currentResource, "title", "flattened")] />
</#assign>
<link type="application/atom+xml" rel="alternate" href="${commentsFeed.url?html}" title="${linkTitle?html}" />
