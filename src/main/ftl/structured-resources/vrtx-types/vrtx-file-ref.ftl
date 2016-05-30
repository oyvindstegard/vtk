<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#macro vrtxFileRef title tooltip classes>
  <div class="vrtx-file-ref ${classes}">
	<div>
      <label for="${inputFieldName}">${title}</label>
	</div>
	<input class="vrtx-textfield" type="file" id="${inputFieldName}" />
  </div>
</#macro>
