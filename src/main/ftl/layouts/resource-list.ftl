<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#assign numberofcolumns = (folders?size / resultSets)?ceiling>
<#list folders?sort as folder>

  <#assign i = 1>
  <#if (folder_index >= i)>
    <#assign i = i + 1>
  </#if>
  <div class="vrtx-list-articles">
    <div class="vrtx-list-articles-col vrtx-list-articles-col-${i}">
    <#if goToFolderLink?exists>
      <a href="${folder.URI}">${folder.title}</a>
    <#else>
      ${folder.title}
    </#if>
    <ul>
    <#list .vars[folder.URI] as result>
      <#assign resourceUri = vrtx.getUri(result) />
      <#assign title = vrtx.propValue(result, 'title') />
      <li><a href="${resourceUri}">${title}</a></li>
    </#list>
    </ul>
    </div>
  </div>

</#list>
