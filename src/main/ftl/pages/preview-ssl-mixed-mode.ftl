<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: preview-ssl-mixed-mode.ftl
  - 
  - Description: Display a link to a popup window with a message
  - explaining the reason(s) why the resource cannot be previewed in
  - an iframe (would likely generate a mixed-content warning in the browser).
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<#function filter_reason val>
  <#-- XXX: patterns must be in sync with those defined in preview.mixedModeService assertions -->
  <#if val?matches(".*(img|iframe|embed|link|base|object|applet|property|xml:img:?):http://.*")>
    <#return true />
  </#if>
  <#if val?matches(".*style.*")>
    <#return true />
  </#if>
  <#if val?matches("script:.*")>
    <#return true />
  </#if>
  <#if val?matches("element:ssi:include:feed.*") && val?matches(".*item-picture=\\[true\\].*") && val?matches(".*url=\\[http:.*")>
    <#return true />
  </#if>
  <#return false />
</#function>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>${vrtx.getMsg('preview.sslMixedContent.title')}</title>
    <script type="text/javascript">
      $(document).on("click", "#vrtx-preview-popup-open", function(e) {
        var openedPreviewPopup = openRegular(this.href, 1082, 800, "vrtx_preview_popup");
        e.stopPropagation();
        e.preventDefault();
      });
    </script>
  </head>
  <body id="vrtx-preview-ssl-mixed-mode">
    <h2>${vrtx.getMsg('preview.sslMixedContent.title')}</h2>

    <#--
    <#if workingCopy?exists>
      <#if resourceReference?index_of("?") &gt; 0><#assign resourceReference = resourceReference + "&amp;revision=WORKING_COPY" />
        <#assign resourceReference = resourceReference + "&amp;revision=WORKING_COPY" />
      <#else>
        <#assign resourceReference = resourceReference + "?revision=WORKING_COPY" />
      </#if>
    </#if>
    -->

    <p class="larger-p">${vrtx.getMsg('preview.sslMixedContent.desc')}</p>

    <#--a class="vrtx-button" href="${resourceReference}" target="vrtx_preview_popup">${vrtx.getMsg('preview.sslMixedContent.open')}</a-->
    <a id="vrtx-preview-popup-open" class="vrtx-focus-button" href="${preview.popupURL}" target="vrtx_preview_popup">${vrtx.getMsg('preview.sslMixedContent.open')}</a-->
   
    <#assign prop = vrtx.getProp(resourceContext.currentResource, 'sslMixedMode') />
    <p class="previewUnavailableReasons"><strong>${vrtx.getMsg('preview.sslMixedContent.reasons.desc')}</strong></p>
    
    <ul>
    <#list prop.values as v>
      <#assign val = v?string />
      <#if filter_reason(val)>
      <#if val?starts_with("img:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.img"  args=[val?substring("img:"?length, val?length)] /></li>

      <#elseif val?starts_with("iframe:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.iframe"  args=[val?substring("iframe:"?length, val?length)] /></li>

      <#elseif val?starts_with("frame:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.frame"  args=[val?substring("frame:"?length, val?length)] /></li>

      <#elseif val?starts_with("embed:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.embed"  args=[val?substring("embed:"?length, val?length)] /></li>

      <#elseif val?starts_with("link:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.link"  args=[val?substring("link:"?length, val?length)] /></li>

      <#elseif val?starts_with("base:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.base"  args=[val?substring("base:"?length, val?length)] /></li>

      <#elseif val?starts_with("object:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.object"  args=[val?substring("object:"?length, val?length)] /></li>

      <#elseif val?starts_with("applet:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.applet"  args=[val?substring("applet:"?length, val?length)] /></li>

      <#elseif val?starts_with("property:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.property"  args=[val?substring("property:"?length, val?length)] /></li>

      <#elseif val?starts_with("xml:img::")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.img"  args=[val?substring("xml:img::"?length, val?length)] /></li>

      <#elseif val?starts_with("xml:img:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.img"  args=[val?substring("xml:img:"?length, val?length)] /></li>

      <#elseif val?starts_with("element:ssi:include:feed")>
        <#assign m = val?matches(".*url=\\[([^\\]]+)\\].*") />
        <#if m>
          <li><@vrtx.msg code="preview.sslMixedContent.reasons.feed"  args=[m?groups[1]] /></li>
        </#if>
      <#elseif val?starts_with("element:style")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.style" /></li>

      <#elseif val?starts_with("script:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.script" args=[val?substring("script:"?length, val?length)] /></li>

      <#elseif val?starts_with("element:script")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.localscript" /></li>

      <#elseif val?starts_with("element:esi:include")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.esi"  args=[val?substring("element:esi:include"?length, val?length)] /></li>

      <#elseif val?starts_with("attr:on")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.jsattr"  args=[val?substring("attr:"?length, val?length)] /></li>

      <#elseif val?starts_with("attr:style:")>
        <li><@vrtx.msg code="preview.sslMixedContent.reasons.styleattr"  args=[val?substring("attr:style:"?length, val?length)] /></li>
      <#else>
        <li>${v}</li>
      </#if>
      <#else>
        <!-- Ignored: ${val} -->
      </#if>
    </#list>
    </ul>
  </body>
</html>
