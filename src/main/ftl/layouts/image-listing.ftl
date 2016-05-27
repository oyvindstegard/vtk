<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/gallery.ftl" as gallery />

<#if !excludeScripts?exists>
  <#if jsURLs?exists && type?exists && type == 'gallery'>
    <#list jsURLs as jsURL>
      <script type="text/javascript" src="${jsURL}"></script>
    </#list>
  </#if>
</#if>

<#if images?exists>
  <div class="vrtx-image-listing-include" id="vrtx-image-listing-include-${unique}">
    <span class="vrtx-image-listing-include-title"><a href="${folderUrl}?display=gallery">${folderTitle}</a></span>
    <#if type?exists && type = 'gallery'>
      <@gallery.galleryJSInit fadeEffect maxHeight />
      <ul class="vrtx-image-listing-include-thumbs-pure-css" <#if hideThumbnails?? && hideThumbnails>style="display: none"</#if>>
    <#else>
      <ul class="vrtx-image-listing-include-thumbs">
    </#if>
        <@gallery.galleryListImages images />
      </ul>
  </div>
</#if>
