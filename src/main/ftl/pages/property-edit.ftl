<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <#assign propName = vrtx.getMsg("property." + property, property)?lower_case />
  <#assign title = vrtx.getMsg("property.edit", "Edit " + propName, [propName]) />
  <title>${title}</title>
</head>
<body id="vrtx-property-edit">
  <div class="propertyForm">
    <form action="${(form.action)}" method="post" id="editPropertyService-form">
      <h3>${title}</h3>
      <#assign firstSubmit = false />
      <#list form.inputs as input>
        <#if input.type == "submit" && !firstSubmit>
          <div class="submitButtons">
        </#if>
        <input class="<#if input.type == "text">vrtx-textfield<#elseif input.type == "submit">vrtx-focus-button</#if>"
               name="${(input.name)?default('')}" type="${(input.type)?default('')}"
               value="<#if input.type == "submit">${vrtx.getMsg("propertyEditor.${(input.value)?default('')?lower_case}")}<#else>${(input.value)?default('')}</#if>" />
        <#if !input_has_next>
          <input class="vrtx-button" name="cancel" type="submit" value="${vrtx.getMsg("propertyEditor.cancel")}" />
        </#if>
        <#if input.type == "submit" && !firstSubmit>
          </div>
          <#assign firstSubmit = true />
        </#if>
     </#list>
    </form>
  </div>
</body>
