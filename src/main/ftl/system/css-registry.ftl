<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#if cssRegistry?exists && place?exists>
  <#list cssRegistry.getMedia(place) as cssURL>
    <link rel="stylesheet" href="${cssURL}" type="text/css" />
  </#list>
</#if>
<#if !place?exists && serviceCssURLs?exists>
  <#list serviceCssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL}" type="text/css" />
  </#list>
</#if>
