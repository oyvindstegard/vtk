<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: view-collectionlisting.ftl
  - 
  - Description: A HTML page that displays a collection listing.
  - 
  - Required model data:
  -   resourceContext
  -   collectionListing
  -  
  - Optional model data:
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/collectionlisting.ftl" as col />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title><@vrtx.msg code="title.webdavCollectionListing" args=[resourceContext.currentResource.title] default="Collection listing" /></title>
</head>
<body>
  <@col.listCollection withForm=false />
  <#if (davMountService.url)?exists>
    <p style="float: left; margin-top: .5em; font-size: 80%;">
      <a href="${davMountService.url}">Open this collection in your WebDAV client</a> 
      (experimental; see <a href="http://www.ietf.org/rfc/rfc4709.txt">RFC 4709</a>)
    </p>
  </#if>
</body>
</html>
