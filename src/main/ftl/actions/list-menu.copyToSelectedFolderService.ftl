<#ftl strip_whitespace=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vortikal.ftl" as vrtx />

<#assign headerMsg = vrtx.getMsg("copyMove.copy-resources.header") />
<#assign titleMsg = vrtx.getMsg("copyMove.copy.title") />
<#assign filesI18n = vrtx.getMsg("copyMove.files") /> 
<#assign actionURL = item.url />
<#assign method = "post" />
<#if resourcesDisclosed?exists>
  <#assign actionURL =  warningDialogURL + '&showAsHtml=true&height=110&width=250' />
  <#assign method = "get" />
</#if>

<h3>${headerMsg}</h3>
<#if session.filesToBeCopied?exists>
  <script type="text/javascript" src="/vrtx/__vrtx/static-resources/jquery/plugins/jquery.vortexTips.js"></script>
  <script type="text/javascript"><!--
    $("#titleContainer").vortexTips("abbr", "#titleContainer", 200, 300, 250, 300, 20, 0);
  // -->
  </script>
  <p>
    <abbr title="<@fileNamesAsLimitedString session.filesToBeCopied />">
      ${session.filesToBeCopied?size} ${filesI18n}
    </abbr>
  </p>
</#if>

<form id="vrtx-copy-to-selected-folder" action="${actionURL?html}" method="${method}" class="vrtx-admin-button">
   <div class="vrtx-button-small"><button title="${titleMsg}" type="submit"
            id="vrtx-copy-to-selected-folder.submit"
            value="copy-resources-to-this-folder" name="action">${item.title?html}</button></div>
</form>

<#recover>
${.error}
</#recover>

<#macro fileNamesAsLimitedString files>
   <#local maxNumberOfFiles = 10 />
   <#local numberOfRemainingFiles = (files?size - maxNumberOfFiles)  />
   <ul>
   <#local more = false />
   <#list files as file>
     <li>${file?split("/")?last}</li>
     <#if file_index == (maxNumberOfFiles-1)>
       <#local more = true />
       <#break />
     </#if>
   </#list>
   </ul>
   <#if more>
     <p>... <@vrtx.msg code="trash-can.permanent.delete.confirm.and" default="and"/> ${numberOfRemainingFiles} <@vrtx.msg code="trash-can.permanent.delete.confirm.more" default="mode"/></p>
   </#if>
</#macro>