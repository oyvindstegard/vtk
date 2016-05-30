<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#if cssURLs?exists>
  <#list cssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL}" type="text/css" />
  </#list>
</#if>
<#if serviceCssURLs?exists>
  <#list serviceCssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL}" type="text/css" />
  </#list>
</#if>
<#if printCssURLs?exists>
  <#list printCssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL}" type="text/css" media="print" />
  </#list>
</#if>
