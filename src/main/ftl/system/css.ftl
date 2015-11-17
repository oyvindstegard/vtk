<#ftl strip_whitespace=true>
<#if cssURLs?exists>
  <#list cssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL?html}" type="text/css" />
  </#list>
</#if>
<#if serviceCssURLs?exists>
  <#list serviceCssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL?html}" type="text/css" />
  </#list>
</#if>
<#if printCssURLs?exists>
  <#list printCssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL?html}" type="text/css" media="print" />
  </#list>
</#if>
<#if cssRegistry?exists && place?exists>
  <#list cssRegistry.getMedia(place) as cssURL>
    <link rel="stylesheet" href="${cssURL?html}" type="text/css" />
  </#list>
</#if>

