<#ftl strip_whitespace=true />
<#import "/lib/vortikal.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en">
  <head>
    <#assign title>
      <#compress>
        <@vrtx.msg code='commenting.recentComments'
                   args=[resource.title] default='Recent comments' />
      </#compress>
    </#assign>
    <link type="application/atom+xml" rel="alternate" href="${feedURL?html}" title="${title?html}" />
    <title>${title?html}</title>
  </head>
  <body>
    <h1>${title?html}</h1>
    <#list comments?reverse as comment>
      <ul>
        <li>
          ${comment.author?html}
          <@vrtx.date value=comment.time format='long' />
          <a href="${(commentURLMap[comment.ID] + '#comment-' + comment.ID)?html}">
            ${resourceMap[comment.URI].title?html}:
          </a>
          <@vrtx.html value=comment.content format='flattened' />
        </li>
      </ul>
    </#list>
  </body>
</html>
