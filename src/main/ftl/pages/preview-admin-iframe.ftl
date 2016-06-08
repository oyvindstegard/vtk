<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: preview-admin-iframe.ftl
  - 
  - Description: A HTML page with a <iframe> tag to the previewed resource
  -              Loads from the admin domain. The src of the iframe points to the
  -              corresponding view domain.
  -
  - Required model data:
  -   resourceReference
  -   resourceContext
  -  
  - Optional model data:
  -   title
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />

<#if !resourceReference?exists>
  <#stop "Unable to render model: required submodel
  'resourceReference' missing">
</#if>
<#if !resourceContext?exists>
  <#stop "Unable to render model: required submodel
  'resourceContext' missing">
</#if>
<#if !permissions_ACTION_READ?exists>
  <#stop "Unable to render model: required submodel
  'permissions_ACTION_READ' missing">
</#if>
<#if !permissions_ACTION_READ_PROCESSED?exists>
  <#stop "Unable to render model: required submodel
  'permissions_ACTION_READ_PROCESSED' missing">
</#if>
<#if !webProtocol?exists>
  <#stop "Unable to render model: required submodel
  'webProtocol' missing">
</#if>

<#-- Used for switching off Ajax POST on preview for image/audio/video and use default height when those are unpublished -->
<#assign resourceType = resourceContext.currentResource.getResourceType() />
<#assign isImageAudioVideo = (resourceType = "image" || resourceType = "audio" || resourceType = "video") />
<#assign hasNotPreviewIframeCommunication = isImageAudioVideo && !resourceContext.currentResource.isPublished() />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>${(title.title)?default(resourceContext.currentResource.name)}</title>
    <@vrtx.javascriptPlaceholder place="preview:head" />
    <script type="text/javascript"><!--
      var previewLoadingMsg = "${vrtx.getMsg('preview.loadingMsg')}";
      var hasPreviewIframeCommunication = <#if hasNotPreviewIframeCommunication>false<#else>true</#if>;
      var isImageAudioVideo = <#if isImageAudioVideo>true<#else>false</#if>;
    // --> 
    </script> 
  </head>
  <body id="vrtx-preview">

    <#if workingCopy?exists>
      <div class="tabMessage-big">
        <@vrtx.rawMsg code="preview.workingCopyMsg" args=[versioning.currentVersionURL] />
      </div>
    </#if>
    
    <#assign previewRefreshParameter = 'outer-iframe-refresh' />
    <#assign constructor = "freemarker.template.utility.ObjectConstructor"?new() />
    <#assign dateStr = constructor("java.util.Date")?string("yyyymmddhhmmss") />

    <#if !previewViewParameter?exists>
      <#assign previewViewParameter = 'vrtx=previewViewIframe' />
    </#if>
    
    <#if previewImage?exists >
      <#assign url = previewImage.URL />
      <#-- Hack for image as web page -->
      <#if resourceReference?starts_with("https://") && url?starts_with("http://")>
        <#assign url = url?replace("http://", "https://") />
      </#if>
    <#elseif resourceReference?exists >
      <#assign url = resourceReference />	  
    </#if>
    
    <#if url?contains("?")>
      <#assign url = url + "&amp;" + previewViewParameter />
    <#else>
      <#assign url = url + "?" + previewViewParameter />
    </#if>
    <#assign url = url + "&amp;" + previewRefreshParameter + "=" + dateStr + "&amp;vrtxPreviewUnpublished"/>

    <div id="preview-mode-actions">
      <div id="preview-mode-actions-inner">
        <!-- Preview mode -->
        <ul id="preview-mode">
          <li class="active-mode"><span id="preview-mode-normal">${vrtx.getMsg("preview.view-mode.normal")}</span></li>
          <li><a id="preview-mode-mobile" href="javascript:void(0);">${vrtx.getMsg("preview.view-mode.mobile")}</a></li>
        </ul>
        <script type="text/javascript"><!--
          var fullscreenToggleOpen = '${vrtx.getMsg("preview.actions.fullscreen-toggle.open")}',
              fullscreenToggleClose = '${vrtx.getMsg("preview.actions.fullscreen-toggle.close")}';
        // -->
        </script>
        <!-- Preview actions -->
        <#assign mailSubject = resourceContext.currentResource.title?url('UTF-8') />  
        <ul id="preview-actions">
          <li><a id="preview-actions-share" href="mailto:?subject=${mailSubject}&amp;body=${vrtx.getMsg('preview.actions.share.mail.body', '', ['${resourceContext.currentServiceURL?url("UTF-8")}', '${resourceContext.principal.description?url("UTF-8")}'])}">${vrtx.getMsg("preview.actions.share")}</a></li>
          <#if !hasNotPreviewIframeCommunication>
          <li><a id="preview-actions-print" href="javascript:void(0);">${vrtx.getMsg("preview.actions.print")}</a></li>
          <li><a id="preview-actions-fullscreen-toggle" href="javascript:void(0);">${vrtx.getMsg("preview.actions.fullscreen-toggle.open")}</a></li>
          </#if>
        </ul>
      </div>
    </div>
    <div id="previewIframeWrapper">
      <a href='javascript:void(0);' id='preview-mode-mobile-rotate-hv'>${vrtx.getMsg("preview.actions.mobile.rotate")}</a>
      <span id="previewIframeMobileBg"></span>
      <div id="previewIframeInnerWrapper">
        <iframe title="${vrtx.getMsg("iframe.title.preview")}" class="preview" name="previewIframe" id="previewIframe" src="${url?no_esc}" marginwidth="0" marginheight="0" scrolling="auto" frameborder="0" style="overflow:visible; width:100%; ">
          ${vrtx.getMsg("iframe.not-supported")} ${vrtx.getMsg("iframe.not-supported.title-prefix")} "${vrtx.getMsg("iframe.title.preview")}". <@vrtx.msg code="iframe.not-supported.link" args=[resourceReference] />
        </iframe>
      </div>
    </div>
  </body>
</html>
