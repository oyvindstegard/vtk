<#ftl strip_whitespace=true>
<#import "/lib/showhide.ftl" as showhide />

<#-- TODO: filter scripts in containsScript => getScripts -->

<#macro includeScripts scripts>
  <#local containsShowHideScripts = containsScripts(scripts, 'SHOWHIDE') />
  <#local containsMultipleInputFieldScripts = containsScripts(scripts, 'MULTIPLEINPUTFIELDS') />
  <script type="text/javascript"><!--
    var MULTIPLE_INPUT_FIELD_INITIALIZED;
    $(document).ready(function() {
      <#list scripts as script>
        <#if script.type == 'SHOWHIDE' >
          <@showhide.addShowHide script />
        </#if>
      </#list>
      <#if containsMultipleInputFieldScripts>
        MULTIPLE_INPUT_FIELD_INITIALIZED = $.Deferred();
        
        initMultipleInputFields();
        
        $.when(vrtxEditor.multipleFieldsBoxesDeferred).done(function() {
          <#list scripts as script>
            <#if script.type == 'MULTIPLEINPUTFIELDS' >
              enhanceMultipleInputFields('${script.name}', true, false, 999, null, false);
            </#if>
          </#list>
          MULTIPLE_INPUT_FIELD_INITIALIZED.resolve();
        });
      </#if>
    });
    //-->
  </script>
</#macro>

<#function containsScripts scripts scriptType>
  <#list scripts as script>
    <#if script.type == scriptType >
      <#return true />
    </#if>
  </#list>
  <#return false />
</#function>
