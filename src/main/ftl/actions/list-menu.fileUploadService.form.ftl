<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if uploadForm?exists && !uploadForm.done>
  <div class="expandedForm vrtx-admin-form">
    <form name="fileUploadService" id="fileUploadService-form" action="${uploadForm.submitURL}" method="post" enctype="multipart/form-data">
      <h3><@vrtx.msg code="actions.fileUploadService" default="Upload File"/></h3>
      <@spring.bind "uploadForm.file" />
      <@actionsLib.genErrorMessages spring.status.errorMessages />
      <div id="file-upload-container">
        <input id="file" type="file" name="file" />
      </div>
      <@actionsLib.genOkCancelButtons "save" "cancelAction" "actions.fileUploadService.save" "actions.fileUploadService.cancel" />
    </form>
    
    <form name="fileUploadCheckService" id="fileUploadCheckService-form" action="${uploadForm.submitURL}" method="post" enctype="multipart/form-data">
      <#if uploadForm.existingFilenames?has_content>
        <span id="file-upload-existing-filenames"><#list uploadForm.existingFilenames as filename>${filename}<#if filename_has_next>#</#if></#list></span>
      </#if>
      <#if uploadForm.existingFilenamesFixed?has_content>
        <span id="file-upload-existing-filenames-fixed"><#list uploadForm.existingFilenamesFixed as filename>${filename}<#if filename_has_next>#</#if></#list></span>
      </#if>
      <@actionsLib.genErrorMessages spring.status.errorMessages />
    </form>
    
  </div>
</#if>

<#recover>
${.error}
</#recover>
