<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if command?exists && !command.done>
  <div class="globalmenu expandedForm">
    <form name="form" action="${command.submitURL}" method="post">
      <h3><@vrtx.msg code="actions.transformHtmlToXhtmlService" default="Make webeditable copy"/>:</h3>
      <@spring.bind "command.name" /> 
      <@actionsLib.genErrorMessages spring.status.errorMessages />
      <input class="vrtx-textfield" type="text" size="30" name="${spring.status.expression}" value="${spring.status.value?if_exists}" />
      <@actionsLib.genOkCancelButtons "save" "cancelAction" "actions.transformHtmlToXhtmlService.save" "actions.transformHtmlToXhtmlService.cancel" />
    </form>
  </div>
</#if>
  
<#recover>
${.error}
</#recover>
