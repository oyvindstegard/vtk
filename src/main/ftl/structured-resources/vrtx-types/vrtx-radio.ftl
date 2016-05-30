<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#macro vrtxRadio title tooltip classes options>
  <div class="vrtx-radio ${classes}">
    <div>${title}</div>
    <#if "${tooltip}" != ""><div>${tooltip}</div></#if>
    <div>
      <#list options as option>
        <div>
          <input name="${option.name}-${option_index}" id="${option.name}-${option_index}" type="radio" value="${option.value}" />
          <label for="${option.name}-${option_index}">${option.value}</label> 
        </div> 
      </#list>
    </div>
  </div>
</#macro>
