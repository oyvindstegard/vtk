<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: image-as-html.ftl
  - 
  - Description: Display image with metadata and download link
  -
  - Required model data:
  -  resource
  -  inline
  -
  - Optional model data:
  -  src
  -  description
  -  jsURLs
  -->
  
<#import "/lib/vtk.ftl" as vrtx />
<#assign lang><@vrtx.requestLanguage/></#assign>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${title}</title>
  <#if jsURLs?exists>
    <#list jsURLs as jsURL>
      <script type="text/javascript" src="${jsURL}"></script>
    </#list>
  </#if>
</head>
<body id="vrtx-image-preview">

<#if resource.name != title >
  <h1>${title}</h1>
</#if>
  </div>
  
<#if src?exists && inline>
  <a href="${src}"><img src="${src}" alt="" /></a>
</#if>

<#if description?exists >
  <div id="vrtx-meta-description" class="vrtx-introduction">
    ${description}
  </div>
</#if>

<#assign pixelHeight = vrtx.propValue(resource, "pixelHeight") />
<#assign pixelWidth = vrtx.propValue(resource, "pixelWidth") />

<h2>${vrtx.getMsg('imageAsHtml.source')}</h2>
<p id="vrtx-image-view-link">
  <a href="${src}">${resource.name}</a>
  <#if pixelHeight != "" && pixelWidth != "">
    &nbsp;(${pixelWidth} x ${pixelHeight} px)
  </#if>
</p>

<#assign photographer = vrtx.propValue(resource, "photographer") />
<#if photographer?exists && photographer != "">
  <h2>${vrtx.getMsg('imageAsHtml.byline')}</h2>
  <p>${photographer}</p> 
</#if>


<#if .vars["copyrightHelpURL." + lang]?exists && .vars["copyrightHelpURL." + lang]?trim != "">
  <h2>${vrtx.getMsg('imageAsHtml.copyright-info')}</h2>
  <#assign url = .vars["copyrightHelpURL." + lang] />
  <p><#if url?exists>${url}</#if></p>
</#if>

</body>
</html>
