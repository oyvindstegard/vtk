<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: tags.ftl
  - 
  - Description: Article view
  - 
  - Required model data:
  -   resource
  -   tag
-->

<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-tags.ftl" as tags />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  
  <title>${title}
    <#if page?has_content>
      <#if "${page}" != "1"> - <@vrtx.msg code="viewCollectionListing.page" /> ${page}</#if>
    </#if>
  </title>
  
  <#if cssURLs??>
    <#list cssURLs as cssUrl>
       <link href="${cssUrl}" type="text/css" rel="stylesheet" />
    </#list>
  </#if>
  
  <#if alternativeRepresentations??>
    <#list alternativeRepresentations as alt>
      <link rel="alternate" type="${alt.contentType}" title="${alt.title}" href="${alt.url}" />
    </#list>
  </#if>
 
  <meta name="robots" content="noindex"/>
 
</head>

<body id="vrtx-tagview">

  <h1>${title}
    <#if page?has_content>
      <#if "${page}" != "1"> - <@vrtx.msg code="viewCollectionListing.page" /> ${page}</#if>
    </#if>
  </h1>
  
  <#if tagElements??>
    <@tags.displayTagElements tagElements />
  <#else>
    <#if listing?? && listing?has_content >
      <@tags.displayTagListing listing />
    <#elseif searchComponents??>
      <@tags.displayTagListing searchComponents />
    <#else>
      <p>${vrtx.getMsg("tags.notFound")} <span class="italic">${tag}</span>.</p>
    </#if>
  </#if>

  <#if scopeUp??>
    <div class="vrtx-tags-scope-up vrtx-has-tags-scope">
      <ul>
        <li><a href="${scopeUp.url}">${scopeUp.title}</a></li>
      </ul>
    </div>
  </#if>

</body>
</html>
