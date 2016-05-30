<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: preview-text-too-large.ftl
  - 
  - Description: Display a warning that the document is too large
  - and cannot be previewed
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Document too large</title>
  </head>
  <body>
    <h1>Document too large</h1>
    This document is too large to be previewed.
  </body>
</html>
