<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: display-revisions.ftl
  - 
  - Description: Lists revisions for a resource
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Revisions</title>
  </head>
  <body id="vrtx-revisions">
    <script type="text/javascript"><!--
      var versionsRestoredInfoMsg = "<@vrtx.msg code='versions.table.info-msg.restored-version' />";
      var versionsMadeCurrentInfoMsg = "<@vrtx.msg code='versions.table.info-msg.made-current-version' />";
    // -->
    </script>
    <table class="resourceInfo revisions">
      <caption><@vrtx.msg code="versions.title" /></caption>
      <thead>
        <tr>
          <th scope="col"><@vrtx.msg code="versions.table.title" />  #</th>
          <th scope="col"><@vrtx.msg code="versions.table.modified-by" /></th>
          <th scope="col"><@vrtx.msg code="versions.table.time" /></th>
          <#-- <th scope="col"><@vrtx.msg code="versions.table.change-amount" /></th> -->
          <th scope="col"></th>
        </tr>
      </head>
      <tbody>
        <#if workingCopy?exists>
          <tr id="vrtx-revisions-working-copy">
            <td><@vrtx.msg code="versions.title.named-version.working_copy" /></td>
            <td>${workingCopy.principal.description}</td>
            <td><@vrtx.date value=workingCopy.timestamp format="longlong" /></td>
            <td class="vrtx-revisions-buttons-column">
              <#if (workingCopy.displayURL)?exists>
                <a class="vrtx-revision-view vrtx-button-small" href="${workingCopy.displayURL}"><@vrtx.msg code="versions.table.buttons.view" /></a>
              </#if>
              <#if (workingCopy.diffURL)?exists>
                <a class="vrtx-revision-view-changes vrtx-button-small" href="${workingCopy.diffURL}"><@vrtx.msg code="versions.table.buttons.view-changes" /></a>
              </#if>
              <#if (workingCopy.deleteURL)?exists>
                <form action="${workingCopy.deleteURL}" method="post" class="vrtx-revisions-delete-form">
                  <input class="vrtx-button-small" type="submit" value="${vrtx.getMsg("versions.table.buttons.delete")}" />
                </form>
              </#if>
              <#if (workingCopy.restoreURL)?exists>
                <form action="${workingCopy.restoreURL}" method="post" id="vrtx-revisions-make-current-form">
                  <input class="vrtx-button-small" type="submit" value="${vrtx.getMsg("versions.table.buttons.make-current")}" />
                </form>
              </#if>
            </td>
          </tr>
        </#if>
        <tr>
          <td id="vrtx-revisions-current"><strong><@vrtx.msg code="versions.title.named-version.head" /></strong></td>
          <td>${resource.modifiedBy.description}</td>
          <td><@vrtx.date value=resource.lastModified format="longlong" /></td>
          <td class="vrtx-revisions-buttons-column">
            <a class="vrtx-revision-view vrtx-button-small" href="${displayURL}"><@vrtx.msg code="versions.table.buttons.view" /></a>
            <#if (diffURL)?exists>
              <a class="vrtx-revision-view-changes vrtx-button-small" href="${diffURL}"><@vrtx.msg code="versions.table.buttons.view-changes" /></a>
            </#if>
          </td>
        </tr>
        <#assign number = regularRevisions?size />
        
        <#list regularRevisions as revision>
          <tr>
            <!-- ID: ${revision.id?c}, ACL:${revision.acl} -->
            <td><@vrtx.msg code="versions.table.entry.name" args=[revision.name] /></td>
            <td>${revision.principal.description}</td>
            <td><@vrtx.date value=revision.timestamp format="longlong" /></td>
            <td class="vrtx-revisions-buttons-column">
              <#if (revision.displayURL)?exists>
                <a class="vrtx-revision-view vrtx-button-small" href="${revision.displayURL}"><@vrtx.msg code="versions.table.buttons.view" /></a>
              </#if>
              <#if (revision.diffURL)?exists>
                <a class="vrtx-revision-view-changes vrtx-button-small" href="${revision.diffURL}"><@vrtx.msg code="versions.table.buttons.view-changes" /></a>
              </#if>
              <#if (revision.deleteURL)?exists>
                <form action="${revision.deleteURL}" method="post" class="vrtx-revisions-delete-form">
                  <input class="vrtx-button-small" type="submit" value="${vrtx.getMsg("versions.table.buttons.delete")}" />
                </form>
              </#if>
              <#if (revision.restoreURL)?exists>
                <form action="${revision.restoreURL}" method="post" class="vrtx-revisions-restore-form">
                  <input class="vrtx-button-small" type="submit" value="${vrtx.getMsg("versions.table.buttons.restore")}" />
                </form>
              </#if>
            </td>
          </tr>
          <#assign number = number - 1 />
        </#list>
      </tbody>
    </table>
  </body>
</html>
