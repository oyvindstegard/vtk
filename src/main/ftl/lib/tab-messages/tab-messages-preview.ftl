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
  <#if isWorkingCopy>
    <@tabMessages.wrapper>
      <@vrtx.msg code="preview.workingCopyMsg" args=[versioning.currentVersionURL] escape=false />
    </@tabMessages.wrapper>
  </#if>
  
  <#if false><#-- TODO: RST -->
    <@tabMessages.wrapper>
	  <@vrtx.msg code="preview.externEditWarnMsg" />
    </@tabMessages.wrapper>
  </#if>
</#macro>