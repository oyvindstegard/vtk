<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

<#--
  - File: lib/media-player.ftl
  - 
  - Description: Media player library
  -
  -->

<#-- Minimum Flash Player version required: -->
<#assign flashPlayerVersion = "10.2.0" />

<#import "/lib/vtk.ftl" as vrtx />

<#--
 * genPlaceholder
 *
 * Generates a placeholder for a media file
 *
 * @param url the HTML-escaped URL to the media file
 * @param dateStr Unix-time in nanoseconds
 * @param isAudio (optional) is placeholder an audio file
 * @param showPlayButton (optional) do we want to show placeholder play button
 *
-->
<#macro genPlaceholder url dateStr isAudio=false showPlayButton=false useVideoTag=false>
  <#if !isAudio>
    <#if poster?? && (hideVideoFallbackLink?? == false || useVideoTag)>
      <#local imgSrc = poster />
    <#-- FIXME Specific handling for "videoref" resource type does *NOT* belong in VTK ! -->
    <#elseif mediaResource?? && vrtx.isOfType("videoref", mediaResource.resourceType)>
      <#local imgSrc = "/vrtx/__vrtx/static-resources/themes/default/icons/video-streaming-only.png" />
      <#local width = "500" />
      <#local height = "279" />
    <#else>
      <#local imgSrc = "/vrtx/__vrtx/static-resources/themes/default/icons/video-noflash.png" />
      <#local width = "500" />
      <#local height = "279" />
    </#if>
    <#local alt = vrtx.getMsg("article.media-file") />
  <#else>
    <#local imgSrc = "/vrtx/__vrtx/static-resources/themes/default/icons/audio-icon.png" />
    <#local width = "151" />
    <#local height = "82" />
    <#local alt = vrtx.getMsg("article.audio-file") />
  </#if>

  <#if useVideoTag>
    <div id="mediaspiller-${dateStr}" class="vrtx-media-player-no-flash">
      <video src="${url}" controls<#if autoplay?? && autoplay == "true"> autoplay</#if> width="${width}" height="${height}" poster="${imgSrc}"></video>
    </div>
  <#else>
    <div id="mediaspiller-${dateStr}-print" class="vrtx-media-player-print<#if showPlayButton> vrtx-media-player-no-flash</#if>">
      <img src="${imgSrc}" width="${width}" height="${height}" alt="${alt}"/>
      <#if showPlayButton><a class="playbutton" href="${url}"></a></#if>
    </div>
    <div id="mediaspiller-${dateStr}"<#if showPlayButton> class="vrtx-media-player-no-flash"</#if>>
      <#if flashDownloadLink?? && flashDownloadLink><p><a href="https://get.adobe.com/flashplayer/">Enable flash</a></p></#if>
      <#if hideVideoFallbackLink?? == false><a class="vrtx-media" href="${url}"></#if>
        <img src="${imgSrc}" width="${width}" height="${height}" alt="${alt}"/>
        <#if showPlayButton><span class="playbutton"></span></#if>
      <#if hideVideoFallbackLink?? == false></a></#if>
    </div>
  </#if>

</#macro>

<#--
 * initFlash
 *
 * Initialize Flash with JavaScript
 *
 * @param url the URL-escaped (UTF-8) URL to the media file
 * @param dateStr Unix-time in nanoseconds
 * @param isStream (optional) is a live stream
 * @param isAudio (optional) is an audio file
 * @param isSWF (optional) is a Shockwave-SWF file
 *
-->
<#macro initFlash url dateStr isStream=false isAudio=false isSWF=false>

  <#local flashUrl = strobe />
  
  <script type="text/javascript"><!--
    if (typeof swfobject == 'undefined') {
      document.write("<scr" + "ipt src='/vrtx/__vrtx/static-resources/flash/SMP_2.0.2494-patched/10.2/lib/swfobject.js' type='text/javascript'><\/script>");
    }
  // -->
  </script>
  <script type="text/javascript"><!--
    var flashvars = {
      <#if autoplay?exists>
        autoPlay: "${autoplay}"
      </#if>
    };
    var flashparams = {};
    
    <#-- Video -->
    <#if !isAudio>
	  <#if !isSWF>
        flashvars.src = "${url}";
        <#if isStream>
          flashvars.streamType = "live";
        </#if>
        <#if poster??>
          flashvars.poster = "${poster?url("UTF-8")}";
        <#else>
          flashvars.poster = "/vrtx/__vrtx/static-resources/themes/default/icons/video-noflash.png";
        </#if>
	    flashparams = {																																														
	      allowFullScreen: "true",
	      allowscriptaccess: "always"
	    };
	    
	  <#-- SWF -->
	  <#else>
	    <#local flashUrl = url />
	  </#if>
	  
	<#-- Audio -->
    <#else>
	  flashvars.playerID = "1";
  	  flashvars.soundFile = "${url}";
	  flashparams = {
		quality: "high",
		menu: "false",
	    wmode: "transparent"
	  };	
	  <#local flashUrl = audioFlashPlayerFlashURL />
	  <#local width = "290" />
	  <#local height = "24" />
    </#if>
    
    swfobject.embedSWF("${flashUrl}", "mediaspiller-${dateStr}", "${width}", "${height}", "${flashPlayerVersion}", false, flashvars, flashparams);
  // -->
  </script>
</#macro>

<#--
 * genDownloadLink
 *
 * Generate download link to media file
 *
 * @param url the HTML-escaped URL to the media file
 * @param type (optional) can specify more narrow file type: "audio" or "video"
 * @param bypass (optional) can bypass check for showDL-boolean
 *
-->
<#macro genDownloadLink url type="media" bypass=false>
  <#if bypass || (hideVideoDownloadLink?? == false && 
      (showDL?? && showDL == "true"))>
    <a class="vrtx-media" href="${url}">
      <#if type = "video">
        <@vrtx.msg code="article.video-file" />
      <#elseif type = "audio">
        <@vrtx.msg code="article.audio-file" />
      <#else>
        <@vrtx.msg code="article.media-file" />
      </#if>
    </a>
  </#if>
</#macro>
