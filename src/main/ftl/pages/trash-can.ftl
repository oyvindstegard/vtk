<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Trash Can</title>
  </head>
<body id="vrtx-trash-can">

  <#assign recoverTitle = vrtx.getMsg("trash-can.recover", "Recover") />
  <#assign deletePermanentlyTitle = vrtx.getMsg("trash-can.delete-permanent", "Delete permanently") />

  <script type="text/javascript"><!-- 
    var deletePermanentlyUncheckedMessage = '${vrtx.getMsg("trash-can.permanent.delete.unchecked-message")}';
    var confirmDeletePermanently = '${vrtx.getMsg("trash-can.permanent.delete.confirm")}';
    var confirmAnd = '${vrtx.getMsg("trash-can.permanent.delete.confirm.and")}';
    var confirmMore = '${vrtx.getMsg("trash-can.permanent.delete.confirm.more")}';
    var confirmDeletePermTitle = '${vrtx.getMsg("trash-can.delete-permanent.confirm")}';
    var recoverUncheckedMessage = '${vrtx.getMsg("trash-can.recovery.unchecked-message")}'; 
    
    var deletePermTitle = '${deletePermanentlyTitle}';
    var recoverTitle = '${recoverTitle}';
  //-->
  </script>

<#-- Without this, freemarker puts spaces between digits in numbers -->
<#setting number_format="0">

<@spring.bind "trashcan.trashCanObjects" />
<@spring.bind "trashcan.submitURL" />
<form class="trashcan" action="${spring.status.value}" method="post">
  <table id="directory-listing" class="trash-can-listing">
  
    <@spring.bind "trashcan.sortLinks" />
    <thead>
      <tr id="vrtx-trash-can-header" class="directoryListingHeader">
        <@setHeader "name" "trash-can.name" />
        <th scope="col" class="checkbox"></th>
        <@setHeader "deleted-by" "trash-can.deletedBy" />
        <@setHeader "deleted-time" "trash-can.deletedTime" />
      </tr>
    </thead>
    
    <@spring.bind "trashcan.trashCanObjects" />
    <#assign collectionSize = spring.status.value?size />
    <tbody>
      <#if (collectionSize > 0)>
        <#assign rowType = "odd">
        <#list spring.status.value as tco>
          <#assign rr = tco.recoverableResource />
          <#assign firstLast = ""  />
          <#if (tco_index == 0) && (tco_index == (collectionSize - 1))>
            <#assign firstLast = " first last" />
          <#elseif (tco_index == 0)>
            <#assign firstLast = " first" />
          <#elseif (tco_index == (collectionSize - 1))>
            <#assign firstLast = " last" />
          </#if>
        
          <#if rr.isCollection()>
            <tr class="${rowType} <@vrtx.recoverableResourceToIconResolver rr /> true${firstLast}">
          <#else>
            <tr class="${rowType} <@vrtx.recoverableResourceToIconResolver rr />${firstLast}">
          </#if>
              <td class="vrtx-trash-can-name name trash"><span class="vrtx-trash-can-name-text">${rr.name}</span></td>
              <td class="checkbox">
                <@spring.bind "trashcan.trashCanObjects[${tco_index}].selectedForRecovery" />
                <#assign checked = "" />
                <#if spring.status.value?string = 'true' >
                  <input type="checkbox" name="${spring.status.expression}" title="${rr.name}" value="true" checked="checked" />
                <#else>
                  <input type="checkbox" name="${spring.status.expression}" title="${rr.name}" value="true" />
                </#if>
              </td>
              <td class="vrtx-trash-can-deleted-by">${rr.deletedBy}</td>
              <td class="vrtx-trash-can-deleted-time"><@printDeletedTime tco.recoverableResource.deletedTime /></td>
            </tr>
          <#if rowType = "even">
            <#assign rowType = "odd">
          <#else>
            <#assign rowType = "even">
          </#if>
        </#list>
      <#else>
        <tr id="trash-can-empty" class="first last">
          <td colspan="4"><@vrtx.msg code="trash-can.empty" default="The trash can contains no garbage" /></td>
        </tr>
      </#if>
    </tbody>
  </table>

  <input class="recoverResource" type="submit" name="recoverAction"
               value="${recoverTitle}" />
  <input class="deleteResourcePermanent" type="submit" name="deletePermanentAction"
               value="${deletePermanentlyTitle}" />
</form>

</body>
</html>

<#macro printDeletedTime time>
  ${time?string("yyyy-MM-dd HH:mm:ss")}
</#macro>

<#macro setHeader id code >
  <#assign sortLink = spring.status.value[id] />
  <#if sortLink.selected>
    <th scope="col" id="vrtx-${code}" class="sortColumn" >
  <#else>
    <th scope="col" id="vrtx-${code}">
  </#if>
    <a href="${sortLink.url}" id="${id}">
      <@vrtx.msg code="${code}" default="${id}" />
    </a>
  </th>
</#macro>
