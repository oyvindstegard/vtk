<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

  <#function resourceDefinition name>
    <#if VRTX_STRUCTURED_RESOURCE_MANAGER??>
      <#assign def = VRTX_STRUCTURED_RESOURCE_MANAGER.get(name)! />
      <#if def??>
        <#return def />
      </#if>      
    </#if>      
  </#function>

  <#function propertyDef resourceDefinition name>
    <#if resourceDefinition??>
      <#list resourceDefinition.getPropertyDescriptions() as pdef>
        <#if pdef.getName() == name>
          <#return pdef />
        </#if>      
      </#list>
    </#if>      
  </#function>
