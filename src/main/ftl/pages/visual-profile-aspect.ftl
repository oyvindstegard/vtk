<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>

<#--
  - File: visual-profile-aspect.ftl
  - 
  - Description: Editor page for the visual profile aspect in 'aspects' JSON property
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/propertyList.ftl" as propList />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.hotkeys.js"></script> 
  <script type="text/javascript"><!--
    var ajaxSaveText = "<@vrtx.msg code='editor.save-visual-profile-ajax-loading-title' />"; 
  // --> 
  </script>
  <title><@vrtx.msg code="visualProfileAspect.edit" default="Edit visual profile"/></title>
</head>
<body id="vrtx-visual-profile">
  <#if form.configError?exists>
    Error in configuration file: ${form.configError}
  <#else>
  <div class="resourceInfo visualProfile">
  <h2><@vrtx.msg code="visualProfileAspect.edit" default="Edit visual profile"/></h2>
  
  <#if !resourceContext.currentResource.isCollection()>
    <#assign backupURL = vrtx.linkConstructor(".", 'copyBackupService') />
    <#assign backupViewURL = vrtx.relativeLinkConstructor("", 'viewService') />
    <form id="backupForm" action="${backupURL}" method="post" accept-charset="UTF-8">
      <@vrtx.csrfPreventionToken url=backupURL />
      <input type="hidden" name="uri" value="${backupViewURL}" />
    </form>
  </#if>
  
  <form id="editor" action="${form.submitURL}" method="post">
    <#assign formElementsSize = form.elements?size />
    <#list form.elements as element>
      <#if (element_index == (formElementsSize-1))>
        <div class="vrtx-visual-profile-rows last">
      <#else>
        <div class="vrtx-visual-profile-rows">
      </#if>
      <#if element.type == 'flag'>
        <input type="checkbox" name="${element.identifier}"
               value="true" <#if element.value?exists>checked="checked"</#if> /> 
          ${element.label}
      <#elseif element.type == 'string'>
        <h3>${element.label}</h3>
        <input class="vrtx-textfield" type="text" name="${element.identifier}" value="${element.value?default('')}" />

      <#elseif element.type == 'enum'>
        <h3>${element.label}</h3> 
        <#if element.possibleValues?exists>
        <ul class="radio-buttons">
        <#list element.possibleValues as value>
          <#assign id = element.identifier + '.' + value.value?default('null') />
          <li><input type="radio" id="${id}"  name="${element.identifier}" 
                 value="${value.value?default('')}"<#if value.selected>checked="checked"</#if> />
          <label for="${id}">${value.label}</label></li>
        </#list>
        </ul>
        </#if>
      <#else>
        unknown type: ${element.type}
      </#if>
      <#if element.inheritable && element.inheritedValue?exists>
        <div class="tooltip">(<@vrtx.msg code="default.inheritsValue" args=[element.inheritedValue] default="inherits" + element.inheritedValue />)</div>
      </#if>
      </div>
    </#list>
    <div class="submitButtons">
      <input class="vrtx-focus-button vrtx-save-button" type="submit" id="saveAction" name="saveAction" value="<@vrtx.msg code="editor.save" default="Save"/>" />
      <input class="vrtx-button" type="submit" id="cancelAction" name="cancelAction" value="<@vrtx.msg code="editor.cancel" default="Cancel"/>" />
    </div>
  </form>
  </div>
  </#if>
</body>
</html>
