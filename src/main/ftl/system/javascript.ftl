<#ftl strip_whitespace=true>
<#if jsURLs?exists>
  <#list jsURLs as jsURL>
    <script type="text/javascript" src="${jsURL}"></script>
  </#list>
</#if>
<#if serviceJsURLs?exists>
  <#list serviceJsURLs as jsURL>
    <script type="text/javascript" src="${jsURL}"></script>
  </#list>
</#if>
