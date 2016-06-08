<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: subfolder-menu.ftl
  - 
  - Description: Sub folder menu implementation
  - 
  - Required model data:
  -   subFolderMenu
  -
  -->

<#import "../lib/vtk.ftl" as vrtx />

<#-- RECURSIVE MENU BUILD -->

<#assign commaSeparated = false />
<#if subFolderMenu?exists>
  <#if subFolderMenu.display?exists && subFolderMenu.display = "comma-separated">
    <#assign commaSeparated = true />
  </#if>
  <@displaySubFolderMenu subFolderMenu />
</#if>

<#macro displaySubFolderMenu subFolderMenu>
    <#if subFolderMenu.size &gt; 0>
      <#assign "counter" = 0>
      <#assign "counter2" = 0>
      <#assign "c" = 0>
         
      <#if subFolderMenu.resultSets?exists>
        <#if commaSeparated>
          <div class="vrtx-subfolder-menu comma-separated vrtx-subfolder-menu-sets-${subFolderMenu.resultSets?size}">
        <#else>
          <div class="vrtx-subfolder-menu vrtx-subfolder-menu-sets-${subFolderMenu.resultSets?size}">
        </#if>
          <#if subFolderMenu.title?exists>
            <div class="menu-title">${subFolderMenu.title}</div>
          </#if>
          <#list subFolderMenu.resultSets as resultSet>
            <#assign counter = counter+1>
            <#assign counter2 = counter2+1>
            <#if subFolderMenu.groupResultSetsBy?exists && (subFolderMenu.groupResultSetsBy?number = counter2 || counter = 1)>
               <#assign "counter2" = 0>
               <#assign c = c+1>
               <@displayParentMenu menu=resultSet currentCount=counter groupCount=c newDiv=true subFolderMenu=subFolderMenu />
            <#else>
               <@displayParentMenu menu=resultSet currentCount=counter groupCount=c newDiv=false subFolderMenu=subFolderMenu />
            </#if>
          </#list>
        </div>
      </#if>
</#if>

</#macro>

<#-- MACROS: -->
<#macro displayItem item separator="none" >
  <#if item.menu?exists>
    <a href="${item.url}">${item.label}</a>
    <@displaySubMenu item.menu displaySubMenu />
  <#else>
    <#if separator = "none">
      <a href="${item.url}">${item.label}</a>
    <#else>
      <a href="${item.url}">${item.label}</a>${separator}
    </#if>
  </#if>
</#macro>

<#macro displayParentMenu menu currentCount groupCount newDiv subFolderMenu >
  <#if newDiv>
    <#if currentCount != 1>
      </div>
    </#if>
    <div class="vrtx-group-${groupCount}">
  </#if>
  	
  <ul class="resultset-${currentCount}">
    <#list menu.itemsSorted as item>
      <li><@displayItem item=item /></li>
    </#list>
  </ul>
  <#if subFolderMenu.groupResultSetsBy?exists && subFolderMenu.groupResultSetsBy?number &gt; 0 && currentCount == subFolderMenu.resultSets?size>
    </div>
  </#if>
</#macro>

<#macro displaySubMenu menu displaySubMenu >
  <ul>
    <#assign i = 0 />
    <#assign sized = menu.itemsSorted?size />
    <#if (menu.maxNumberOfItems < sized)>
      <#assign sized = menu.maxNumberOfItems />
    </#if>
    <#list menu.itemsSorted as item>
      <#if (i = sized)>
        <#break />
      </#if>
      <li>
        <#if commaSeparated>
          <#if (i < (sized-1))>
            <@displayItem item=item separator="," />
          <#else>
            <@displayItem item=item separator="none" />
          </#if>
        <#else>
          <@displayItem item=item />
        </#if>
      </li>
      <#assign i = i + 1 />
    </#list>
    
  	<#if (menu.totalNumberOfItems > menu.maxNumberOfItems)>
      <li class="vrtx-more">
        <a href="${menu.moreUrl}"><@vrtx.msg code="subfolder.morelinkTitle" /></a>
      </li>
    </#if>
  </ul>
</#macro>
