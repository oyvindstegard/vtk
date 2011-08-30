<#ftl strip_whitespace=true>
<#import "/lib/vortikal.ftl" as vrtx />
<#assign docUrl = createDropDown.url?html />
<#if docUrl?contains("?")>
  <#assign docUrl = docUrl + "&display=create-document-from-drop-down" />
<#else>
  <#assign docUrl = docUrl + "?vrtx=admin&display=create-document-from-drop-down" />
</#if>
<#assign colUrl = createDropDown.url?html />
<#if colUrl?contains("?")>
  <#assign colUrl = colUrl + "&display=create-collection-from-drop-down" />
<#else>
  <#assign colUrl = colUrl + "?vrtx=admin&display=create-collection-from-drop-down" />
</#if>
<#assign upUrl = createDropDown.url?html />
<#if upUrl?contains("?")>
  <#assign upUrl = upUrl + "&display=upload-file-from-drop-down" />
<#else>
  <#assign upUrl = upUrl + "?vrtx=admin&display=upload-file-from-drop-down" />
</#if>
<ul class="manage-create">
  <li class="manage-create-top first">
    <a href="javascript:void(0);"><@vrtx.msg code="manage.create-new" default="Create new" /></a>
  </li>
  <li class="manage-create-drop">
    <a class="thickbox" title="<@vrtx.msg code="manage.choose-location" default="Choose location" />" href="${docUrl?html}">
      <@vrtx.msg code="manage.document" default="Create Document" />
    </a>
  </li>
  <li class="manage-create-drop">
    <a class="thickbox" title="<@vrtx.msg code="manage.choose-location" default="Choose location" />" href="${colUrl?html}">
      <@vrtx.msg code="manage.collection" default="Create Folder" />
    </a>
  </li>
  <li class="manage-create-drop">
    <a class="thickbox" title="<@vrtx.msg code="manage.choose-location" default="Choose location" />" href="${upUrl?html}">
      <@vrtx.msg code="manage.upload-file" default="Upload file" />
    </a>
  </li>
</ul>