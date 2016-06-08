<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#macro printPropertyEditView title inputFieldName tooltip="" classes="" value="" baseFolder="/">
  <div class="vrtx-media-ref ${classes}">
    <div>
      <label for="${inputFieldName}">${title}</label>
    </div>
    <div>
	  <input class="vrtx-textfield" type="text" id="${inputFieldName}" name="${inputFieldName}" value="${value}" size="30"/>
      <button class="vrtx-button" type="button" onclick="browseServer('${inputFieldName}', '${fckeditorBase.url}', '${baseFolder}','${fckBrowse.url.pathRepresentation}','Media');"><@vrtx.msg code="editor.browseMediaFiles"/></button>
    </div>
  </div>
</#macro>
