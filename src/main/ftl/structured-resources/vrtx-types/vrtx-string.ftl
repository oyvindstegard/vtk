<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#macro printPropertyEditView title inputFieldName value="" tooltip="" classes="" inputFieldSize=20 valuemap="" dropdown=false multiple=false defaultValue="">
<div class="vrtx-string ${classes}<#if multiple> vrtx-multiple</#if>">
  <label for="${inputFieldName}">${title}</label>
  <div class="inputfield">
    <#if dropdown && valuemap?exists && valuemap?is_hash && !multiple>
      <#if value=="" >
        <#local value=defaultValue />
      </#if>
      <select name="${inputFieldName}" id="${inputFieldName}">
        <#list valuemap?keys as key>
          <#if key = "range">
            <#local rangeList = valuemap[key] />
            <#list rangeList as rangeEntry >
              <option value="<#if rangeEntry?string != defaultValue>${rangeEntry}</#if>" <#if value == rangeEntry?string> selected="selected" </#if>>${rangeEntry}</option>
            </#list>
          <#else>
            <#if value != "">
              <option value="${key}" <#if value == key> selected="selected" </#if>>${valuemap[key]}</option>
            <#else>
              <option value="${key}" <#if key == "undefined"> selected="selected" </#if>>${valuemap[key]}</option>
            </#if>
          </#if>
	    </#list>
	  </select>
    <#else>
      <#if multiple && dropdown && valuemap?exists && valuemap?is_hash>
        <#if value=="" >
          <#local value=defaultValue />
        </#if>
        <script type="text/javascript"><!--
          var dropdown${inputFieldName} = [
            <#list valuemap?keys as key>
              { 
                key: "${key}",
                value: "${valuemap[key]}"
              } 
              <#if (key_index < (valuemap?size - 1))>, </#if>
            </#list>
          ];
        // -->
        </script>
      </#if>
	  <input <#if inputFieldName == "title">class="vrtx-textfield-big"<#elseif multiple && dropdown && valuemap?exists && valuemap?is_hash>class="vrtx-textfield vrtx-multiple-dropdown"<#else>class="vrtx-textfield"</#if> size="${inputFieldSize}" type="text" name="${inputFieldName}" id="${inputFieldName}" value="${value}" />
    </#if>
    <#if "${tooltip}" != ""><div class="tooltip">${tooltip}</div></#if>
  </div>
</div>
</#macro>
