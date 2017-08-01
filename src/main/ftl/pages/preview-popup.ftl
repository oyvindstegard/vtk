<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: preview-popup.ftl
  - 
  - Description: 
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" id="vrtx-preview-popup">
  <head>
    <title><@vrtx.msg code="preview.popup.title" args=["${(title.title)?default(resourceContext.currentResource.name)}"] /></title>
    <link type="text/css" href="/vrtx/__vrtx/static-resources/themes/default/default.css" rel="stylesheet" />
    <#include "/system/css.ftl"/> 
    <#include "/system/javascript.ftl"/>
    <#-- TODO: externalize more of the script -->
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
          var text = (brokenLinks > 1) ? '<@vrtx.msg code="linkcheck.brokenlinks" default="broken links"/>'
                                       : '<@vrtx.msg code="linkcheck.brokenlink" default="broken link"/>';
          $("body").find("#vrtx-link-check-spinner")
            .html(brokenLinks + ' ' + text)
            .addClass("vrtx-broken-links")
	        .attr("aria-busy", "false");
	    } else {
	      $("#vrtx-link-check-spinner").remove();
	    }
	  }
	
	  $(document).ready(function() {
	     $('iframe').on("load", function() {
	        $(this).contents().find("a").attr("target", "_top");
	        
	        <#if visualizeBrokenLinks?exists && visualizeBrokenLinks = 'true'> 
	        
	        $("#vrtx-preview-popup-iframe-wrapper").prepend('<span id="vrtx-link-check-spinner" aria-busy="true"><@vrtx.msg code="linkcheck.spinner" default="Checking links..."/></span>');
	        
	        var linkCheckURL = '${linkcheck.URL}';
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
	     
	     $(document).on("click", "#vrtx-preview-popup-close", function(e) {
	       window.close();
	       e.stopPropagation();
	       e.preventDefault();
	     });
	  });	
	  //-->
    </script>
  </head>
  <body>
    <h1>
      <@vrtx.msg code="preview.popup.title" args=["${(title.title)?default(resourceContext.currentResource.name)}"] />
      <span id="vrtx-preview-popup-close"></span>
    </h1>

    <#assign previewRefreshParameter = 'vrtxPreviewForceRefresh' />

    <#assign constructor = "freemarker.template.utility.ObjectConstructor"?new() />
    <#assign dateStr = constructor("java.util.Date")?string("yyyymmddhhmmss") />

    <#assign url = resourceReference />
    <#if url?contains("?")>
      <#assign url = url + "&link-check=" + visualizeBrokenLinks?default('false')
               + "&" + previewRefreshParameter + "=" + dateStr + "&authTarget=" + authTarget />
    <#else>
      <#assign url = url + "?link-check=" + visualizeBrokenLinks?default('false')
               + "&" + previewRefreshParameter + "=" + dateStr + "&authTarget=" + authTarget />
    </#if>

    <#if workingCopy?exists>
      <#assign url = url + "&revision=WORKING_COPY" />
      <div id="vrtx-preview-message-box">${vrtx.getMsg('preview.popup.working-copy')}</div>
    <#else>
      <#assign published = vrtx.propValue(resourceContext.currentResource, "published")! />
      <#if !published?has_content || published = "false">
        <div id="vrtx-preview-message-box">${vrtx.getMsg('preview.popup.unpublised')}</div>
      </#if>
    </#if>

    <div id="vrtx-preview-popup-iframe-wrapper">
      <iframe title="${vrtx.getMsg("iframe.title.preview")}" class="previewView" name="previewViewIframe" id="previewViewIframe" src="${url}" marginwidth="0" marginheight="0" scrolling="auto" frameborder="0" style="overflow:visible; width:100%; ">
        ${vrtx.getMsg("iframe.not-supported")} ${vrtx.getMsg("iframe.not-supported.title-prefix")} "${vrtx.getMsg("iframe.title.preview")}". <@vrtx.msg code="iframe.not-supported.link" args=[url] />
      </iframe>
    </div>

  </body>
</html>
