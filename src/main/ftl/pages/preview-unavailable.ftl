<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: preview-unavailable.ftl
  - 
  - Description: A HTML page that displays a message that the
  - preview mode is not available for the current resource.
  - 
  - Required model data:
  -   resourceDetail
  -  
  - Optional model data:
  -   title
  -
  -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>${(title.title)?default(resourceContext.currentResource.name)}</title>
  </head>
  <body>
    <p class="previewUnavailable">Preview not available.<br /><br />
    See the resource on: <a href="${resourceDetail.viewURL}">${resourceDetail.viewURL}</a></p>
  </body>
</html>
