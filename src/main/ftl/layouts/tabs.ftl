<#--
  - File: tabs.ftl
  - 
  - Description: Simple tabs implementation
  - 
  - Required model data:
  -   tabs
  - NOTE: this template wll be deprecated by list-menu.ftl
  -->
<#if !tabs?exists>
  <#stop "Unable to render model: required submodel
  'tabs' missing">
</#if>
<div class="tabs">
<ul class="${tabs.label}">
  <#list tabs.items as tab>
    <#if tab.url?exists>
      <#if tab.active>
        <li class="current activeTab ${tab.label}">
          <a href="${tab.url?html}">${tab.title}</a>
        </li>
      <#else>
        <li class="${tab.label}"><a href="${tab.url?html}">${tab.title}</a></li>
      </#if>
    </#if>
  </#list>
</ul>
</div>


