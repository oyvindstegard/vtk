<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#macro printPropertyEditView title inputFieldName value="" tooltip="" classes="">
  <div class="vrtx-string ${classes}">
    <label for="${inputFieldName}">${title}</label>
    <input size="12" type="text" name="${inputFieldName}" id="${inputFieldName}" value="${value}" class="inputfield vrtx-textfield date" />
    <#if "${tooltip}" != ""><div class="tooltip">${tooltip}</div></#if>
  </div>
</#macro>
