<#ftl strip_whitespace=true output_format="XML" auto_esc=true>
<?xml version="1.0" encoding="utf-8"?>
<#import "/lib/vtk.ftl" as vrtx />
<feed xmlns="http://www.w3.org/2005/Atom">
  <title type="html">
    <#if resource.URI == '/'>
      <@vrtx.msg code='commenting.comments' args=[repositoryID] default='Comments' />
    <#else>
      <@vrtx.msg code='commenting.comments' args=[resource.title] default='Comments' />
    </#if>
  </title>
  <#assign uri = resource.URI.toString() />
  <link href="${urlMap[uri]}" />
  <link rel="self" href="${selfURL}" />
  <#assign date_format>yyyy-MM-dd'T'HH:mm:ssZZ</#assign>
  <updated><@vrtx.date value=resource.lastModified format=date_format?markup_string /></updated>
  <id>${selfURL}</id>
  <#list comments as comment>
  <#assign resource = resourceMap[comment.URI] />
  <entry>
    <title>${comment.author.description} <@vrtx.msg code="commenting.comments.on" default="about" /> "${resource.title}"</title>
    <link href="${(urlMap[comment.URI] + '#comment-' + comment.ID)}" />
    <id>${(urlMap[comment.URI] + '#comment-' + comment.ID)}</id>
    <author>
      <name>${comment.author.description}</name>
      <#if comment.author.URL?exists>
      <uri>${comment.author.URL}</uri>
      </#if>
    </author>
    <published><@vrtx.date value=comment.time format=date_format?markup_string /></published>
    <updated><@vrtx.date value=comment.time format=date_format?markup_string /></updated>
    <summary type="html">${comment.content}</summary>
  </entry>
  </#list>
</feed>
