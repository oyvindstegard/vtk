<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#if docUrl?exists && collUrl?exists && upUrl?exists>
  <#if docUrl.url?exists && collUrl.url?exists && upUrl.url?exists>
    <#assign docFinalUrl = docUrl.url />
    <#assign collFinalUrl = collUrl.url />
    <#assign upFinalUrl = upUrl.url />

    <#assign resourceType = resourceContext.currentResource.resourceType />

    <#-- TMP fix for image -- get service from folder instead of resource (you cant create on resource anyway) -->
    <#if resourceType == "image">
      <#assign docFinalUrl = docFinalUrl?substring(0, docFinalUrl?last_index_of("/"))
                           + "/" + docFinalUrl?substring(docFinalUrl?index_of("?"), docFinalUrl?length) />
      <#assign collFinalUrl = collFinalUrl?substring(0, collFinalUrl?last_index_of("/"))
                            + "/" + collFinalUrl?substring(collFinalUrl?index_of("?"), collFinalUrl?length) />
      <#assign upFinalUrl = upFinalUrl?substring(0, upFinalUrl?last_index_of("/"))
                          + "/" + upFinalUrl?substring(upFinalUrl?index_of("?"), upFinalUrl?length) />
    </#if>

    <ul class="manage-create button-row">
      <li class="manage-create-drop first">
        <a id="manage-create-drop-document" title="<@vrtx.msg code="manage.choose-location.document" default="Choose where you would like to create document" />" href="${docFinalUrl}">
          <@vrtx.msg code="manage.document" default="Create document" />
        </a>
      </li>
      <li class="manage-create-drop">
        <a id="manage-create-drop-collection" title="<@vrtx.msg code="manage.choose-location.collection" default="Choose where you would like to create folder" />" href="${collFinalUrl}">
          <@vrtx.msg code="manage.collection" default="Create folder" />
        </a>
      </li>
      <li class="manage-create-drop">
        <a id="manage-create-drop-upload" title="<@vrtx.msg code="manage.choose-location.upload-file" default="Choose where you would like to upload file" />" href="${upFinalUrl}">
          <@vrtx.msg code="manage.upload-file" default="Upload file" />
        </a>
      </li>
    </ul>
  </#if>
</#if>
