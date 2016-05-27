<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#macro printPropertyEditView title inputFieldName tooltip="" classes="" value="" name="" baseFolder="/ " inputFieldSize=40>
  <div class="vrtx-resource-ref ${classes}">
    <label for="${inputFieldName}">${title}</label>
	<div class="vrtx-resource-ref-browse">
	  <input class="vrtx-textfield" type="text" id="${inputFieldName}" name="${inputFieldName}" value="${value}" size="${inputFieldSize}" />
      <button class="vrtx-button" type="button" onclick="browseServer('${inputFieldName}', '${fckeditorBase.url}', '${baseFolder}','${fckBrowse.url.pathRepresentation}','File');"><@vrtx.msg code="editor.browseImages"/></button>
	</div>
  </div>
</#macro>
