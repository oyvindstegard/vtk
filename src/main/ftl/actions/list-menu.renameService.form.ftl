<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if renameCommand?exists>
  <div class="globalmenu expandedForm">
    <form name="renameService" id="renameService-form" action="${renameCommand.submitURL}" method="post">
      <h3><@vrtx.msg code="actions.renameService" default="Change name"/>:</h3>
      <@spring.bind "renameCommand" + ".name" /> 
      <@actionsLib.genErrorMessages spring.status.errorMessages />
      <#assign confirm = renameCommand.confirmOverwrite />
      <input class="vrtx-textfield" type="text" size="40" name="name" value="${spring.status.value}" <#if confirm> readonly="readonly" </#if> />
      <div id="submitButtons">
      	<#if confirm>
      	  <input class="vrtx-focus-button" type="submit" name="overwrite" value="<@vrtx.msg code="actions.renameService.overwrite" default="Overwrite"/>" />
      	<#else>
          <input class="vrtx-focus-button" type="submit" name="save" value="<@vrtx.msg code="actions.renameService.save" default="Save"/>" />
        </#if>
        <input class="vrtx-button" type="submit" name="cancel" value="<@vrtx.msg code="actions.renameService.cancel" default="Cancel"/>" />
      </div>
    </form>
  </div>
</#if>
  
<#recover>
${.error}
</#recover>
