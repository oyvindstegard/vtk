<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: tab-messages.ftl
  - 
  - Description: Library for displaying tab messages in editor
  -   
  -->
  
<#import "../vtk.ftl" as vrtx />
<#import "tab-messages.ftl" as tabMessages />

<#macro display isWorkingCopy versioning>
  <@tabMessages.workingCopy isWorkingCopy>
    <@vrtx.msg code="editor.workingCopyMsg" args=[versioning.currentVersionURL] escape=false />
  </@tabMessages.workingCopy>
  
  <@tabMessages.externalEdit>
    <@vrtx.msg code="editor.externEditWarnMsg" />
      
    <@tabMessages.openMsgDialog title='${vrtx.getMsg("preview.externEditWarnMsg")}'
                                msg='${vrtx.getMsg("editor.externEditWarnMsg")}' />
  </@tabMessages.externalEdit>
</#macro>