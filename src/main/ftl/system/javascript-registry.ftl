<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#if javascriptRegistry?exists && place?exists >
  <#list javascriptRegistry.getMedia(place) as jsURL>
    <script type="text/javascript" src="${jsURL}"></script>
  </#list>
</#if>
<#if !place?exists && serviceJsURLs?exists>
  <#list serviceJsURLs as jsURL>
    <script type="text/javascript" src="${jsURL}"></script>
  </#list>
</#if>
