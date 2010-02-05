
<#import "/lib/vortikal.ftl" as vrtx />

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
  </head>
  <body>
  <div class="resourceInfo">
  <div class="vrtx-report-nav">
  	<div class="back"> 
    <a href="${serviceURL}" ><@vrtx.msg code="report.back" default="Back" /></a>
    </div>
  </div>
  <h2><@vrtx.msg code="report.last-modified" /></h2>
  <p>
  <@vrtx.msg code="report.last-modified.about" />
  </p>
  <div class="vrtx-report">
    <table cellpadding="3" border="1">
      <thead>
        <tr>
          <th><@vrtx.msg code="report.title" default="Title" /></th>
          <th><@vrtx.msg code="report.location" default="Location" /></th>
          <th><@vrtx.msg code="report.last-modified" default="Last modified" /></th>
          <th><@vrtx.msg code="report.modified-by" default="Modified by" /></th>
          <!-- <th><@vrtx.msg code="report.permission-set" default="Permissions set" /></th> -->
          <th><@vrtx.msg code="report.published" default="Published" /> </th>
        </tr>
      </thead>
      <tbody>
      <#list report.lastModifiedList as lastModified>
        <tr>
       	  <#assign title=lastModified.getProperty(report.type) >
          <td>${title.value}</td>
          <td><a href="${lastModified.URI}?vrtx=admin">${lastModified.URI}</a></td>
          <#assign lastModifiedTime = vrtx.propValue(lastModified, 'lastModified') />
          <td>${lastModifiedTime}</td>
          <#assign modifiedBy = vrtx.propValue(lastModified, 'modifiedBy') />
          <td><a href="http://www.uio.no/sok?person=${modifiedBy}">${modifiedBy}</a></td>
          <!--
          <#assign aclIsInherited = vrtx.getMsg("report.yes", "Yes")>
          <#if lastModified.isInheritedAcl() >
          	<#assign aclIsInherited = vrtx.getMsg("report.no", "No")>
          </#if>
          <td>${aclIsInherited}</td>
          -->
          <#assign published = vrtx.propValue(lastModified, 'published') />
          <#assign publishedStatus = vrtx.getMsg("report.yes", "Yes")>
          <#if published = "false">
            <#assign publishedStatus = vrtx.getMsg("report.no", "No")>
          </#if>
          <td>${publishedStatus}</td>
        </tr>
      </#list>
      </tbody>
    </table>
  </div>
  </div>
  </body>
</html>
