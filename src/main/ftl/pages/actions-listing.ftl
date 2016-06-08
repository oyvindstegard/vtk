<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Collection listing</title>
    <#if cssURLs?exists>
      <#list cssURLs as cssUrl>
        <link href="${cssUrl}" type="text/css" rel="stylesheet" />
      </#list>
    </#if>
    <!--[if lte IE 8]>
      <link rel="stylesheet" href="/vrtx/__vrtx/static-resources/themes/default/default-ie8.css" type="text/css"/>
    <![endif]--> 
    <!--[if lte IE 7]>
      <link rel="stylesheet" href="/vrtx/__vrtx/static-resources/themes/default/default-ie7.css" type="text/css"/> 
    <![endif]-->
    <#if jsURLs?exists>
      <#list jsURLs as jsURL>
        <script type="text/javascript" src="${jsURL}"></script>
      </#list>
    </#if>
    <#include "/system/system.ftl" />
    
  </head>
  <body class="embedded2 forms-new">
    <#if entries?has_content>
      <table id="directory-listing" class="collection-listing">
        <tbody> 
          <#list entries as entry>
            <#assign url = (entry.actions['view'])?default('') />
            <#assign count = entry_index + 1 />
            <tr class="vrtx-directory-listing-${count} <#if entry_index % 2 == 0>odd<#else>even</#if> <@vrtx.resourceToIconResolver entry.resource /><#if entry.resource.collection> true</#if>">
              <td class="name"><a href="${url}">${entry.resource.title}</a></td>
              <td class="action">
                <#list entry.actions?keys as action>
                  <#if action != "view">
                    <a class="${action}-action" href="${entry.actions[action]}<#if action == "edit-title">&amp;default-value=${entry.resource.title?url("UTF-8")}</#if>">${vrtx.getMsg("embeddedListing.${action}")?lower_case}</a>&nbsp;&nbsp;&nbsp;
                  </#if>
                </#list>
              </td>
            </tr>
          </#list>
        <tbody> 
      </table>
    <#else>
      <span id="directory-listing"></span>
    </#if>
    
    <#list globalActions?keys as globalAction>
      <div class="globalaction">
        <a class="vrtx-button" id="upload-action" href="${globalActions[globalAction]}">${vrtx.getMsg("embeddedListing.${globalAction}")}</a>
        <#-- FIXME: Hack for showing info for medicine upload -->
        <#if globalAction == "upload"><p class='fixed-resources-permissions-info'>${vrtx.getMsg("embeddedListing.${globalAction}.fixed-resources-permissions-info")}</p></#if>
      </div>
    </#list>
  </body>
</html>
