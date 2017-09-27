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
  <#if isWorkingCopy>
    <@tabMessages.wrapper>
      <@vrtx.msg code="editor.workingCopyMsg" args=[versioning.currentVersionURL] escape=false />
    </@tabMessages.wrapper>
  </#if>
  
  <#if false><#-- TODO: RST -->
    <@tabMessages.wrapper>
      <@vrtx.msg code="editor.externEditWarnMsg" />
      
      <@tabMessages.openMsgDialog title='${vrtx.getMsg("preview.externEditWarnMsg")}'
                                    msg='${vrtx.getMsg("editor.externEditWarnMsg")}' />
    </@tabMessages.wrapper>
  </#if>
</#macro>