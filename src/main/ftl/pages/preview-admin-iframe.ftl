<#ftl strip_whitespace=true>

<#--
  - File: preview-admin-iframe.ftl
  - 
  - Description: A HTML page with a <iframe> tag to the previewed resource
  -              Loads from the admin domain. The src of the iframe points to the
  -              corresponding view domain.
  - 
  - Dynamic resizing of iframe only works in IE and Firefox. 
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
<#if !resourcePrincipalPermissions?exists>
  <#stop "Unable to render model: required submodel
  'resourcePrincipalPermissions' missing">
</#if>

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head><title>${(title.title)?default(resourceContext.currentResource.name)}</title> 
  </head>
  <body>

    <#if !previewViewParameter?exists>
      <#assign previewViewParameter = 'vrtx=previewViewIframe' />
    </#if>

    <#assign url = resourceReference />
    <#if url?contains("?")>
      <#assign url = url + "&amp;" + previewViewParameter />
    <#else>
      <#assign url = url + "?" + previewViewParameter />
    </#if>
    
    <#-- Do not show preview if resource is "Allowed for all" and we are on https. Should not normally happen -->
    <#if (resourcePrincipalPermissions.permissionsQueryResult = 'true') && (resourcePrincipalPermissions.requestScheme = 'https')>
      <p class="previewUnavailable">${vrtx.getMsg("preview.httpOnly")}</p>
    
    <#else>
      <iframe class="preview" name="previewIframe" id="previewIframe" src="${url}" marginwidth="0" marginheight="0" scrolling="auto" frameborder="0" vspace="0" hspace="0" style="overflow:visible; width:100%; ">
        [Your user agent does not support frames or is currently configured
        not to display frames. However, you may visit
        <a href="${resourceReference}">the related document.</a>]
      </iframe>

    </#if>

  </body>
</html>


