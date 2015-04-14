<#ftl strip_whitespace=true>
<#--
  - File: css-ref.ftl
  - 
  - Description: inserts a <link rel="stylesheet"> reference to a given URL
  -  
  - Required model data:
  -  url - the CSS URL
  -
  -->
<#if !url?exists>
  <#stop "Missing 'url' in model"/>
</#if>
<link rel="stylesheet" type="text/css" href="${url?html}" />
