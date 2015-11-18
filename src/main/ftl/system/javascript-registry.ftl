<#if javascriptRegistry?exists && place?exists >
  <#list javascriptRegistry.getMedia(place) as jsURL>
    <script type="text/javascript" src="${jsURL}"></script>
  </#list>
</#if>
