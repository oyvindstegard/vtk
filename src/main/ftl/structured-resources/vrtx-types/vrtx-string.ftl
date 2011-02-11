<#macro printPropertyEditView title inputFieldName value="" tooltip="" classes="" inputFieldSize=20 valuemap="" dropdown=false defaultValue="">
<div class="vrtx-string ${classes}">
  <label for="${inputFieldName}">${title}</label>
  <div class="inputfield">
  <#if dropdown && valuemap?exists && valuemap?is_hash>
  <#if value=="" >
  <#local value=defaultValue />
  </#if> 
  <select name="${inputFieldName}" id="${inputFieldName}">
    <#list valuemap?keys as key>
    <#if key = "range">
      <#local rangeList = valuemap[key] />
      <#list rangeList as rangeEntry >
        <option value="<#if rangeEntry?string != defaultValue>${rangeEntry?html}</#if>" <#if value == rangeEntry?string> selected </#if>>${rangeEntry}</option>
      </#list>
    <#else>
      <#if value != "">
        <option value="${key?html}" <#if value == key> selected </#if>>${valuemap[key]}</option>
      <#else>
        <option value="${key?html}" <#if key == "undefined"> selected </#if>>${valuemap[key]}</option>
      </#if>
    </#if>
	</#list>
	</select>
  <#else>
	<input size="${inputFieldSize}" type="text" name="${inputFieldName}" id="${inputFieldName}" value="${value?html}"/>
  </#if>
  <#if "${tooltip}" != ""><div class="tooltip">${tooltip}</div></#if>
  </div>
</div>
</#macro>
