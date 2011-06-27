<#ftl strip_whitespace=true>

<#--
  - File: preview-unavailable_nn.ftl
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

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>${(title.title)?default(resourceContext.currentResource.name)}</title>
  </head>
  <body>
    <p class="previewUnavailable">Innholdet kan ikke forh&aring;ndsvisast innenfor "Administrasjon av webdokumenter".<br /><br /> 
    Men du kan sj&aring; p&aring; eller lasta dokumentet p&aring; webadressen:<br><a href="${resourceDetail.viewURL}">${resourceDetail.viewURL}</a></p>
  </body>
</html>


