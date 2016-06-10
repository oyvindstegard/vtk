<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: list-menu.ftl
  - 
  - Description: Macro printing a menu structure using an unordered HTML list.
  -
  -->

<#-- listMenu macro:
 * The objects used by this macro are
 * vtk.web.view.component.menu.ListMenu (holding the entire
 * menu), and vtk.web.view.component.menu.MenuItem,
 * representing the individual menu items. Some menu items are
 * "active", meaning that they represent a URL to the current
 * service. In these cases a form, or some other rendering may take
 * place after the menu list itself is rendered.
 *
 * Custom freemarker templates may be supplied both for generating the
 * links in the menu bar, or for generating the form following the
 * menu list. 
 * 
 * TODO: Document the include-features
 * @param menu - a vtk.web.view.component.menu.ListMenu object
 * @param displayForms - whether or not to attempt to display a form
 *        for the active menu item below the menu list (default 'false')
 * @param prepend - an optional string which is prepended to each link
 * @param between - an optional string which is inserted between links
 * @param append - an optional string which is appended to each link
 *
-->

<#import "/lib/vtk.ftl" as vrtx/>

<#macro listMenu menu displayForms=false prepend="" between="" append="">

  <#if (menu.items?size > 0 || menu.label == "resourceMenuRight")>

      <#assign size = 0 />
      <#list menu.items as item>
        <#if item.url?exists>
          <#assign size = size+1 />
        </#if>
      </#list>

      <#if (size > 0)>
        <#if menu.label == "resourceMenuLeft">
          <ul class="list-menu button-row" id="${menu.label}">
        <#else>
          <ul class="list-menu" id="${menu.label}">
        </#if>
        <#assign count = 1 />
        <#list menu.items as item>
          <#if item.url?exists>
            <#if count == 1 && count == size && menu.label != "resourceMenuRight">
              <li class="${item.label} first last">
            <#elseif count == 1>
              <li class="${item.label} first">
            <#elseif count == size && menu.label != "resourceMenuRight">
              <li class="${item.label} last">
            <#else>
              <li class="${item.label}">
            </#if>
            <#if item_index != 0 && item_index != menu.items?size>${between}</#if>
            <#attempt>
              <#include "/actions/list-menu.${item.label}.ftl" />
            <#recover>
                ${prepend}<a id="${item.label}" href="${item.url}">${item.title}</a>${append}
            </#attempt>
              </li>
            <#assign count = count+1 />
          </#if>
        </#list>
      </#if>
      
      <#if menu.label == "resourceMenuRight">
        <#assign size = size+1 />
        <#if size == 1>
          <ul class="list-menu" id="${menu.label}">
            <li class="readPermission first">
        <#else>
          <li class="readPermission">
        </#if>
            <h3>${vrtx.getMsg("collectionListing.permissions")}</h3>
            <#if !resourceContext.currentResource.readRestricted >
              <p><span class="allowed-for-all">${vrtx.getMsg("collectionListing.permissions.readAll")}</span></p>
            <#else>
              <p><span class="restricted">${vrtx.getMsg("collectionListing.permissions.restricted")}</span></p>
            </#if>
          </li>
          <li class="resourceType last">
            <h3>Type</h3>
            <p><span>${vrtx.getMsg("resourcetype.name.${resourceContext.currentResource.resourceType}")}</span></p>
          </li>
      </#if>
      
     <#if (size > 0)>
       </ul>
     </#if>
     
    <#-- Output the form if it exists -->
    <#if displayForms && menu.activeItem?exists>
      <#attempt>
        <#include "/actions/list-menu.${menu.activeItem.label}.form.ftl" />
      <#recover>
        <#-- Do nothing -->
      </#attempt>
    </#if>
  </#if>
</#macro>
