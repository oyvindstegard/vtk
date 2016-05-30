<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: list-menu.ftl
  - 
  - Description: Simple list menu implementation
  - 
  - Required model data:
  -   menu
  -
  -->
<#assign cssClassMap = {
         "none":"vrtx-uri-menu",
         "vertical":"vrtx-vertical-menu",
         "horizontal":"vrtx-horizontal-menu",
         "tabs":"vrtx-tab-menu" } />

<#-- RECURSIVE MENU BUILD -->
<#if menu?exists && menu.items?has_content>
  <ul class="${cssClassMap[menu.label]}">
    <@listMenu menu=menu />
  </ul>
</#if>

<#-- MACROS: -->
<#macro listMenu menu>
  <#list menu.items as item>
    <#if item.url?exists>
      <@listItem item=item />
    </#if>
  </#list>
</#macro>

<#macro listItem item>
  <#assign class="" />
  <#if item.active>
    <#assign class="vrtx-active-item" />
    <#if item.label?exists>
      <#assign class = class + " " +  item.label />
    </#if>
    <#if isCurrent(item)>
      <#assign class = class + " vrtx-current-item" />
    </#if>
  <#else>
    <#if item.label?exists>
      <#assign class = item.label />
    </#if>
  </#if>
  <#if class?has_content>
    <li class="${class}">
      <@displayItem item=item/>
    </li>
  <#else>
    <li>
      <@displayItem item=item/>
    </li>
  </#if>
</#macro>

<#function isCurrent item>
  <#if !item.menu?exists>
    <#return true />
  </#if>
  <#list item.menu.items as child>
    <#if child.active>
      <#return false />
    </#if>
  </#list>
  <#return true />
</#function>

<#macro displayItem item>
  <a href="${item.url}">${item.title}</a>
  <#if item.menu?exists && item.menu.items?size &gt; 0>
    <#if item.menu.label?exists>
      <ul class="${item.menu.label}">
        <@listMenu menu=item.menu />
      </ul>
    <#else>
      <ul>
        <@listMenu menu=item.menu />
      </ul>
    </#if>
  </#if>
</#macro>
