<#--
  - File: pages/media-player.ftl
  - 
  - Description: Web page display of single local media resource.
  - 
  - This template requires "mediaResource" in model data and is not meant for display
  - of non-local media.
  -->

<#import "/lib/vtk.ftl" as vrtx />
<#import "/layouts/media-player.ftl" as mediaPlayer />

<#assign title = vrtx.propValue(mediaResource, "title" , "flattened") />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${title}</title>
  <#if jsURLs?exists>
    <#list jsURLs as jsURL>
      <script type="text/javascript" src="${jsURL}"></script>
    </#list>
  </#if>
  <link rel="stylesheet" media="all" href="/vrtx/__vrtx/static-resources/themes/default/view-media-player.css" />
</head>
<body>

<h1>${title}</h1>

<#if description?exists >
  <div id="vrtx-meta-description">
    ${description}
  </div>
</#if>

<#assign dateStr = 0 />
<#if nanoTime?has_content><#assign dateStr = nanoTime?c /></#if>
<@mediaPlayer.mediaPlayer dateStr />

</body>
</html>
