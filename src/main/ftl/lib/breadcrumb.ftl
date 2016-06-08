<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: breadcrumb.ftl
  - 
  - Description: Macro generating a breadcrumb
  - 
  - Required model data:
  -   resourceContext
  -  
  - Optional model data:
  -   
  -->
<#import "vtk.ftl" as vrtx />
  
<#macro breadCrumb crumbs downcase=false hidePrefix=false stopLevel=0 startLevel=1>

<#if !crumbs?exists>
  <#stop "Unable to render model: required submodel
  'breadcrumb' missing">
</#if>

<#assign crumbsSize = crumbs?size />

<#if (crumbsSize > 0)>
  <div id="vrtx-breadcrumb-wrapper">
    <div id="vrtx-breadcrumb" class="breadcrumb">
      <#if !hidePrefix>
        <span class="breadcrumb-prefix"><@vrtx.msg code="breadcrumb.locationTitle" default="You are here"/>:</span>
      </#if>
      <#assign counter = startLevel />
      <#list crumbs as elem>
        <#assign name = elem.title/>
        <#if downcase>
          <#assign name = name?lower_case/>
        </#if>
        <#if (elem_index < (crumbsSize - 1))>
          <#if elem.URL??>
            <span class="vrtx-breadcrumb-level vrtx-breadcrumb-level-${counter}<#if (elem_index == (crumbsSize - 2))> vrtx-breadcrumb-before-active</#if>">
            <a href="${elem.URL}">${name}</a>
          <#else>
            <span class="vrtx-breadcrumb-level-no-url vrtx-breadcrumb-level-${counter}<#if (elem_index == (crumbsSize - 2))> vrtx-breadcrumb-before-active</#if>">
            <span class="vrtx-no-url">${name}</span>
          </#if>
        <#else>
          <span class="vrtx-breadcrumb-level vrtx-breadcrumb-level-${counter} vrtx-breadcrumb-active">${name}
        </#if>
        <#if elem.delimiter?exists>
      	  <span class="vrtx-breadcrumb-delimiter">${elem.delimiter}</span>
        </#if>
        </span>
        <#if counter = stopLevel>
          <#break>
        </#if>
        <#assign counter = counter+1>
      </#list>
    </div>
  </div>
</#if>
</#macro>
