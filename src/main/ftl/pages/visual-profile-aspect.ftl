<#ftl strip_whitespace=true>

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
<#import "/lib/vortikal.ftl" as vrtx />
<#import "/lib/propertyList.ftl" as propList />
<#import "/lib/ping.ftl" as ping />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title><@vrtx.msg code="visualProfileAspect.edit" default="Edit visual profile"/></title>
  <@ping.ping url=pingURL['url'] interval=900 />
</head>
<body id="vrtx-visual-profile">
  <#if form.configError?exists>
    Error in configuration file: ${form.configError?html}
  <#else>
  <div class="resourceInfo visualProfile">
  <h2><@vrtx.msg code="visualProfileAspect.edit" default="Edit visual profile"/></h2>
  <form action="${form.submitURL}" method="post">
    <#assign formElementsSize = form.elements?size />
    <#list form.elements as element>
      <#if (element_index == (formElementsSize-1))>
        <div class="vrtx-visual-profile-rows last">      
      <#else>
        <div class="vrtx-visual-profile-rows">
      </#if>
      <#if element.type == 'flag'>
        <input type="checkbox" name="${element.identifier?html}"
               value="true" <#if element.value?exists>checked="checked"</#if>> 
          ${element.label?html}

      <#elseif element.type == 'string'>
        <h3>${element.label?html}</h3>
        <div class="vrtx-textfield">
          <input type="text" name="${element.identifier?html}" value="${element.value?default('')?html}" />
        </div>

      <#elseif element.type == 'enum'>
        <h3>${element.label?html}</h3> 
        <#if element.possibleValues?exists>
        <ul class="radio-buttons">
        <#list element.possibleValues as value>
          <#assign id = element.identifier + '.' + value.value?default('null') />
          <li><input type="radio" id="${id?html}"  name="${element.identifier?html}" 
                 value="${value.value?default('')?html}"<#if value.selected>checked="checked"</#if> />
          <label for="${id?html}">${value.label?html}</label></li>
        </#list>
        </ul>
        </#if>
      <#else>
        unknown type: ${element.type?html}
      </#if>
      <#if element.inheritable && element.inheritedValue?exists>
        <div class="tooltip">(<@vrtx.msg code="default.inheritsValue" args=[element.inheritedValue?html] default="inherits" + element.inheritedValue?html />)</div>
      </#if>
      </div>
    </#list>
    <div class="submitButtons">
      <div class="vrtx-focus-button">
        <input type="submit" id="saveAction" name="saveAction" onclick="checkSubmit()" value="<@vrtx.msg code="editor.save" default="Save"/>" />
      </div>
      <div class="vrtx-button">
        <input type="submit" id="cancelAction" name="cancelAction" value="<@vrtx.msg code="editor.cancel" default="Cancel"/>">
      </div>
    </div>
    
  </form>
  </div>
  </#if>
</body>
</html>

