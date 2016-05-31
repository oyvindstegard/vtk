<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: tabs.ftl
  - 
  - Description: Simple tabs implementation
  - 
  - Required model data:
  -   tabs
  - NOTE: this template wll be deprecated by list-menu.ftl
  -->
<#import "/lib/vtk.ftl" as vrtx />

<#if !tabs?exists>
  <#stop "Unable to render model: required submodel 'tabs' missing">
</#if>
<div id="app-tabs">
  <ul class="${tabs.label}">
    <#list tabs.items as tab>
      <#if tab.url?exists>
        <#if tab.active>
          <li class="current activeTab ${tab.label}">
            <a id="${tab.label}" href="${tab.url}" title="<@vrtx.msg code="describe.${tab.label}" default="${tab.title}"/>">${tab.title}</a>
          </li>
        <#else>
          <li class="${tab.label}"><a id="${tab.label}" href="${tab.url}" title="<@vrtx.msg code="describe.${tab.label}" default="${tab.title}"/>">${tab.title?no_esc}</a></li>
        </#if>
      </#if>
    </#list>
  </ul>
</div>

