<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: tab-messages.ftl
  - 
  - Description: Library for displaying tab messages in preview
  -   
  -->
  
<#import "../vtk.ftl" as vrtx />
<#import "tab-messages.ftl" as tabMessages />

<#macro display isWorkingCopy versioning>
  <@tabMessages.workingCopy isWorkingCopy>
    <@vrtx.msg code="preview.workingCopyMsg" args=[versioning.currentVersionURL] escape=false />
  </@tabMessages.workingCopy>
  
  <@tabMessages.externalEdit>
    <@vrtx.msg code="preview.externEditWarnMsg" />
  </@tabMessages.externalEdit>
</#macro>