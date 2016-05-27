<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>

<#--
  - File: layouts/media-player.ftl
  - 
  - Description: Media player macro.
  -
  -->

<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/media-player.ftl" as mpLib />

<#macro mediaPlayer dateStr>
  <#if media?exists>
    <#if streamType?exists>

      <@mpLib.genPlaceholder "${media}" dateStr />
      <@mpLib.initFlash '${media?url("UTF-8")}' dateStr true />

    <#elseif contentType?exists>
    
      <#if contentType == "audio" 
        || contentType == "audio/mpeg"
        || contentType == "audio/mp3"
        || contentType == "audio/x-mpeg">

	    <@mpLib.genPlaceholder "${media}" dateStr true />
        <@mpLib.initFlash '${media?url("UTF-8")}' dateStr false true />

	    <@mpLib.genDownloadLink "${media}" "audio" />
	  
      <#elseif contentType == "video/quicktime" >
        <object classid="clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B" id="testid" width="${width}" height="${height}" 
                codebase="http://www.apple.com/qtactivex/qtplugin.cab">
          <param name="src" value="${media}"/>
          <param name="autoplay" value="<#if autoplay?exists && autoplay = "true">true<#else>false</#if>"/>
          <param name="controller" value="true"/>
          <param name="loop" value="false"/>
          <param name="scale" value="aspect" />
          <embed src="${media}" 
                 width="${width}" 
                 height="${height}"
                 autoplay="<#if autoplay?exists && autoplay = "true">true<#else>false</#if>"
                 controller="true" loop="false" scale="aspect" pluginspage="http://www.apple.com/quicktime/download/">
          </embed>
        </object>
        
        <@mpLib.genDownloadLink "${media}" />
      
      <#elseif contentType == "application/x-shockwave-flash"
                           && extension == "swf">
    
	    <@mpLib.genPlaceholder "${media}" dateStr />
	    <@mpLib.initFlash '${media?url("UTF-8")}' dateStr false false true />

      <#elseif contentType == "video/x-flv"
            || contentType == "video/mp4">

	    <@mpLib.genPlaceholder "${media}" dateStr false true />
	    <@mpLib.initFlash '${media?url("UTF-8")}' dateStr />
	  
	    <#if contentType == "video/mp4" && !media?starts_with("rtmp")>
          <@mpLib.genDownloadLink "${media}" "video" />
	    </#if>
	  
      <#else>

        <@mpLib.genDownloadLink "${media}" "media" true />
      
      </#if>
    </#if>
  </#if><#-- if media?exists -->
</#macro>

<#-- When this layout template is invoked as "page" for components: -->
<#assign dateStr = 0 />
<#if nanoTime?has_content><#assign dateStr = nanoTime?c /></#if>
<@mediaPlayer dateStr />
