<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>

<#--
  - File: browse.ftl
  - 
  - Description: A HTML page that displays a listing of resources
  - with the purpose of locating a resource through navigation and
  - creating a link to that resource.
  - 
  - Required model data:
  -   resourceContext
  -
  - Optional model data:
  -
  -->

<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/collectionlisting.ftl" as col />

<#if !resourceContext?exists>
  <#stop "Unable to render model: required model data
  'resourceContext' missing">
</#if>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Browse</title>
</head>
<body>
  <#if resourceContext.currentResource.collection>
    <@col.listCollection />
  </#if>
</body>
</html>
