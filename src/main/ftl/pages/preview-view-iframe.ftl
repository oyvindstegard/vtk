<#ftl strip_whitespace=true>

<#--
  - File: preview-view-iframe.ftl
  - 
  - Description: A HTML page with a <iframe> tag to the previewed resource
  - 
  - Loads from the view domain, so that Javascript contained on this page can
  - manipulate the contents of the iframe (which is the main purpose of having
  - this "extra" iframe).
  - 
  - Processes the contents of the iframe by changing link targets and (optionally)
  - visualizing broken links.
  -
  - Adds a force-refresh parameter with a timestamp to the url to prevent caching 
  - of the contents, and a parameter to display unpublished pages (only makes a difference for 
  - resources that can be published).
  - 
  - Adds authTarget=http to ensure authentication (e.g. needed to view unpublished). 
  - Can be hardcoded as the value and "http" is only respected for world readable resources 
  - (where preview should in fact be on http) 
  -
  - Directly includes"/system/javascript.ftl" since this 
  - page is not part of admin and therefore not decorated
  -
  - TODO: Hack based on Ny UiO web styles/structure with regard to visualizing broken links should ideally not be here in vortikal
  - 
  - Required model data:
  -   resourceReference
  -   resourceContext
  -  
  - Optional model data:
  -   title
  -
  -->

<#import "/lib/vortikal.ftl" as vrtx />

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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
  <title>${(title.title)?default(resourceContext.currentResource.name)}</title>
  <#include "/system/css.ftl"/> 
  <#include "/system/javascript.ftl"/>
  <script type="text/javascript"><!--

  function linkCheckResponseLocalizer(status) {
      switch (status) {
         case 'NOT_FOUND':
         case 'MALFORMED_URL':
            return '<@vrtx.msg code="linkcheck.status.NOT_FOUND" default="broken"/>';
         case 'TIMEOUT':
            return '<@vrtx.msg code="linkcheck.status.TIMEOUT" default="timeout"/>';
         default:
            return '<@vrtx.msg code="linkcheck.status.ERROR" default="error"/>';
      }
  }
  
  function linkCheckCompleted(requests, brokenLinks) {
    if(brokenLinks > 0) {
      $("body").find("#vrtx-link-check-spinner")
        .html(brokenLinks + ' <@vrtx.msg code="linkcheck.brokenlinks" default=" broken links"/>')
        .addClass("vrtx-broken-links");
    } else {
      $("body").find("#vrtx-link-check-spinner").remove();
    }
  }

  $(document).ready(function() {
     $('iframe').load(function() {
        $(this).contents().find("a").attr("target", "vrtx-preview-window");
        
        <#if visualizeBrokenLinks?exists && visualizeBrokenLinks = 'true'> 
        
        $("body").prepend('<span id="vrtx-link-check-spinner"><@vrtx.msg code="linkcheck.spinner" default="Checking links..."/></span>');
        
        var linkCheckURL = '${linkcheck.URL?html}';
        var authTarget = '${authTarget}';
        var href = location.href;
        linkCheckURL = (authTarget === "https" && linkCheckURL.match(/^http:\/\//)) ? linkCheckURL.replace("http://", "https://") : linkCheckURL;
        
        visualizeBrokenLinks({
            selection : 'iframe',
            validationURL : linkCheckURL,
            chunk : 10,
            responseLocalizer : linkCheckResponseLocalizer,
            completed : linkCheckCompleted
        });
        </#if>
     });
  });	
  //-->
  </script>
  </head>
  <body>

    <#if !previewRefreshParameter?exists>
      <#assign previewRefreshParameter = 'vrtxPreviewForceRefresh' />
    </#if>

    <#assign constructor = "freemarker.template.utility.ObjectConstructor"?new() />
    <#assign dateStr = constructor("java.util.Date")?string("yyyymmddhhmmss") />

    <#assign url = resourceReference />
    <#if url?contains("?")>
      <#assign url = url + "&amp;link-check=" + visualizeBrokenLinks?default('false')
               + "&amp;" + previewRefreshParameter + "=" + dateStr + "&amp;authTarget=" + authTarget />
    <#else>
      <#assign url = url + "?" + "&amp;link-check=" + visualizeBrokenLinks?default('false')
               + "&amp;" + previewRefreshParameter + "=" + dateStr + "&amp;authTarget=" + authTarget />
    </#if>

    <div id="previewViewIframeWrapper">
      <iframe title="${vrtx.getMsg("iframe.title.preview")}" class="previewView" name="previewViewIframe" id="previewViewIframe" src="${url}" marginwidth="0" marginheight="0" scrolling="auto" frameborder="0" style="overflow:visible; width:100%; ">
        ${vrtx.getMsg("iframe.not-supported")} ${vrtx.getMsg("iframe.not-supported.title-prefix")} "${vrtx.getMsg("iframe.title.preview")}". <@vrtx.msg code="iframe.not-supported.link" args=[resourceReference?html] />
      </iframe>
    </div>
  </body>
</html>


