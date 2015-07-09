<#ftl strip_whitespace=true>
<#macro printPropertyEditView title inputFieldName tooltip="" classes="" value="" name="" baseFolder="/">
  <div class="vrtx-image-ref ${classes}">
	<div class="vrtx-image-ref-label">
      <label for="${inputFieldName}">${title}</label>
	</div>
	<div class="vrtx-image-ref-browse">
	  <input type="text" class="vrtx-textfield preview-image-inputfield" id="${inputFieldName}" name="${inputFieldName}" value="${name?html}" size="30" />
      <button class="vrtx-button" type="button" onclick="browseServer('${inputFieldName}', '${fckeditorBase.url?html}', '${baseFolder}','${fckBrowse.url.pathRepresentation}');"><@vrtx.msg code="editor.browseImages"/></button>
	</div>
	<div id="${inputFieldName}.preview" class="vrtx-image-ref-preview">
	  <label for="${inputFieldName}.preview"><@vrtx.msg code="editor.image.preview-title"/></label>
	  <div id="${inputFieldName}.preview-inner" class="vrtx-image-ref-preview-inner">
	    <img src="<#if value?has_content>${value?html}</#if>" alt="preview" />
	  </div>
	</div>
  </div>
</#macro>
