<#ftl strip_whitespace=true>

<#--
  - File: preview-text.ftl
  - 
  - Description: A HTML page with the contents of a text resource
  - inside a <pre> tag.
  - 
  - Required model data:
  -   resourceString
  -   resourceContext
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
    <pre class="preview">${resourceString?html}</pre>
  </body>
</html>
