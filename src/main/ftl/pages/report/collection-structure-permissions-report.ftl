
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/layouts/subfolder-menu.ftl" as subfolder />

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
  <#if cssURLs?exists>
    <#list cssURLs as cssURL>
    <link rel="stylesheet" href="${cssURL}" />
    </#list>
  </#if>
  <#if jsURLs?exists>
    <#list jsURLs as jsURL>
    <script type="text/javascript" src="${jsURL}"></script>
    </#list>
  </#if>
  <script type="text/javascript">
     <!--
     $(window).ready(function(){
       $(".resultset-1").treeview({
         animated: "fast"
       });
     });
     // -->
  </script>
  </head>
  <body>
  <div class="resourceInfo">
    <div class="vrtx-report-nav">
  	  <div class="back">
	    <a href="${serviceURL}"><@vrtx.msg code="report.back" default="Back" /></a>
	  </div>
	</div> 
	<h2><@vrtx.msg code="report.collection-structure-permissions" /></h2>
	<p>
	  <@vrtx.msg code="report.collection-structure-permissions.about" />
	</p>
	<div class="vrtx-report">
	  <@subfolder.displaySubFolderMenu report.subFolderMenu true true />
	</div>
  </div>
  </body>
</html>
