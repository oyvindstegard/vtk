<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

  <#macro printPropertyEditView title inputFieldName value="" tooltip="" classes="" inputFieldSize=20 valuemap="" dropdown=false defaultValue="">
    <div class="vrtx-string ${classes}">
      <label for="${inputFieldName}">${title}</label>
      <div class="inputfield">
        <#if value=="" >
          <#local value=defaultValue />
        </#if>
        <select name="${inputFieldName}" id="${inputFieldName}">
          <option value="" <#if value=="">selected="selected"</#if>><@vrtx.msg code="shared-text.no-shared-text" default="No shared text" /></option>
          <#list sharedTextProps[inputFieldName]?keys as y >
            <option value="${sharedTextProps[inputFieldName][y]['id']}" <#if value==sharedTextProps[inputFieldName][y]['id']>selected="selected"</#if>>${sharedTextProps[inputFieldName][y]['title']}</option>
          </#list>
	</select>
	<div id="${inputFieldName}Descriptions">
	  <#list sharedTextProps[inputFieldName]?keys as y >
            <#local language = resourceLocaleResolver.resolveLocale(null)?string />
    	    <#if language == "no_NO">
    	      <#local language = "no" />
    	    <#elseif language == "no_NO_NY">
              <#local language = "nn" />
    	    </#if>
    	    <#if sharedTextProps[inputFieldName][y]['description-' + language]?exists>
    	      <div class="${sharedTextProps[inputFieldName][y]['id']} shared-text-description">
    	        ${(sharedTextProps[inputFieldName][y]['description-' + language])?no_esc}
              </div>
    	    <#else>
    	      <div class="${sharedTextProps[inputFieldName][y]['id']} shared-text-description unavailable">
    	        <@vrtx.msg code="shared-text.not-available" default="This shared text is not available in" /> <@vrtx.msg code="language.${language}" default="${language}" />.
    	      </div>
    	    </#if>
	  </#list>
	</div>
        <#if "${tooltip}" != ""><div class="tooltip">${tooltip}</div></#if>
      </div>
    </div>
  <script type="text/javascript"><!--
    function sharedTextChange() {
      $("#${inputFieldName}Descriptions").find(".shared-text-description").hide();
      var val = $("#${inputFieldName}").val();
      if(val != "") {
        var clazz = val.replace(/\./g, "\\.")
                       .replace(/\+/g, "\\+")
                       .replace(/\//g, "\\/");
        $("#${inputFieldName}Descriptions").find("." + clazz).show();
      }
    }
    $(document).ready(sharedTextChange); 
    $("#${inputFieldName}").change(sharedTextChange); 
  // -->
  </script>
</#macro>
