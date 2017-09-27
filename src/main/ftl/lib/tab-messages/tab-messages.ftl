<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: tab-messages.ftl
  - 
  - Description: Library for displaying tab messages
  -   
  -->

<#macro workingCopy isWorkingCopy>
  <#if isWorkingCopy>
    <@wrapper>
	  <#nested />
    </@wrapper>
  </#if>
</#macro>

<#macro externalEdit>
  <#local exernalEdited = resourceContext.currentResource.getPropertyByPrefix(nullArg, "extern-edit-warn")?default("") />
  <#if exernalEdited != "">
    <@wrapper>
	  <#nested />
    </@wrapper>
  </#if>
</#macro>

<#macro wrapper>
  <div class="tabMessage-big">
    <#nested />
  </div>
</#macro>

<#macro openMsgDialog title msg width=400>
  <script type="text/javascript"><!--
    var d = new VrtxMsgDialog({
      title: '${title}',
      msg: '${msg}',
      width: ${width}
    });
    d.open();
    // -->
  </script>
</#macro>