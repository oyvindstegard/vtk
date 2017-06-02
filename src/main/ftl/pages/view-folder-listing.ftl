<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/layouts/subfolder-menu.ftl" as subfolder />

<#assign resource = collection />
<#assign title = vrtx.propValue(resource, "title") />
<#if overriddenTitle?has_content>
  <#assign title = overriddenTitle />
</#if>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${title}</title>
  <#if cssURLs?exists>
    <#list cssURLs as cssURL>
      <link rel="stylesheet" href="${cssURL}" type="text/css" />
    </#list>
  </#if>
  <#if printCssURLs?exists>
    <#list printCssURLs as cssURL>
      <link rel="stylesheet" href="${cssURL}" media="print" type="text/css" />
    </#list>
  </#if>
</head>
<body id="vrtx-folder-listing">
  <div id="vrtx-content">
    <div id="vrtx-main-content">
      <@vrtx.displayLinkOtherLang resource />
      <h1>${title}</h1>

      <#if subFolderMenu?exists && subFolderMenu.size &gt; 0>
        <#assign "counter2" = 0>
        <#assign "counter" = 0>
        <#assign "c" = 0>

        <#if subFolderMenu.resultSets?exists>
          <div id="vrtx-folder-menu" class="vrtx-folder-menu">
            <#list subFolderMenu.resultSets as resultSet>
              <#assign counter = counter+1>
              <#assign counter2 = counter2+1>
              <#if subFolderMenu.groupResultSetsBy?exists && (subFolderMenu.groupResultSetsBy?number = counter2 || counter = 1)>
                <#assign "counter2" = 0>
                <#assign c = c+1>
                <@subfolder.displayParentMenu menu=resultSet currentCount=counter groupCount=c newDiv=true subFolderMenu=subFolderMenu />
              <#else>
                <@subfolder.displayParentMenu menu=resultSet currentCount=counter groupCount=c newDiv=false subFolderMenu=subFolderMenu />
              </#if>
            </#list>
          </div>
        </#if>
      </#if>

      <#assign intro = vrtx.propValue(resource, "introduction")! />
      <#if intro?has_content>
        <div class="vrtx-introduction">
          ${intro?no_esc}
        </div>
      </#if>
    </div>
  </div>
  <#if jsURLs?has_content>
    <#list jsURLs as jsURL>
      <script type="text/javascript" src="${jsURL}"></script>
</#list>
</#if>
</body>
</html>
