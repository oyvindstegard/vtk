
<#ftl strip_whitespace=true>
<#import "/spring.ftl" as spring />
<#import "/lib/vortikal.ftl" as vrtx />

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Trash Can</title>
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
  </head>
<body id="vrtx-trash-can">

<#-- WTF???? Why the F*** does this work??? -->
<#-- Without it, stupid freemarker puts spaces between digits in numbers -->
<#setting number_format="0">
<#-- END WTF -->

<@spring.bind "trashcan.trashCanObjects" />
<#if (spring.status.value?size > 0) >
<@spring.bind "trashcan.submitURL" />
<form class="trashcan" action="${spring.status.value?html}" method="post">

  <table id="vrtx-trash-can-table" class="directoryListing">
    <@spring.bind "trashcan.sortLinks" />
    <tr id="vrtx-trash-can-header" class="directoryListingHeader">
      <@setHeader "name" "trash-can.name" />
      <th class="checkbox">
        <a href="#" class="vrtx-check-all" > <@vrtx.msg code="collectionListing.all" default="All"/></a> | 
        <a href="#" class="vrtx-uncheck-all"> <@vrtx.msg code="collectionListing.none" default="None"/></a>
      </th>
      <@setHeader "deleted-by" "trash-can.deletedBy" />
      <@setHeader "deleted-time" "trash-can.deletedTime" />
    </tr>

    <@spring.bind "trashcan.trashCanObjects" />
    <#list spring.status.value as tco>
    <#assign rr = tco.recoverableResource />
    <#if (tco_index % 2 == 0)>
      <tr class="odd ${rr.resourceType}">
    <#else>
      <tr class="even ${rr.resourceType}">
    </#if>
        <td class="vrtx-trash-can-name name trash">${rr.name?html}</td>
        <td class="checkbox">
        <@spring.bind "trashcan.trashCanObjects[${tco_index}].selectedForRecovery" />
        <#assign checked = "" />
        <#if spring.status.value?string = 'true' >
          <#assign checked = "checked" />
        </#if>
        <input type="checkbox" name="${spring.status.expression}" title="${rr.name?html}" value="true" ${checked} />
        </td>
        <td class="vrtx-trash-can-deleted-by">${rr.deletedBy}</td>
        <td class="vrtx-trash-can-deleted-time"><@printDeletedTime tco.recoverableResource.deletedTime /></td>
      </tr>
    </#list>

  </table>
  <input class="recoverResource" type="submit" name="recoverAction"
               value="<@vrtx.msg code="trash-can.recover" default="Recover"/>"/>
  <input class="deleteResourcePermanent" type="submit" name="deletePermanentAction"
               value="<@vrtx.msg code="trash-can.delete-permanent" default="Delete permanently"/>"/>
</form>
<#else>
  <@vrtx.msg code="trash-can.empty" default="The trash can contains no garbage." />
</#if>

</body>
</html>

<#macro printDeletedTime time>
  ${time?string("yyyy-MM-dd HH:mm:ss")}
</#macro>

<#macro setHeader id code >
  <#assign sortLink = spring.status.value[id] />
  <#if sortLink.selected>
    <th id="vrtx-${code}" class="sortColumn" >
  <#else>
    <th id="vrtx-${code}">
  </#if>
    <a href="${sortLink.url?html}" id="${id}">
      <@vrtx.msg code="${code}" default="${id}" />
    </a>
  </th>
</#macro>
