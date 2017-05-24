<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>${(title.title)?default(resourceContext.currentResource.name)}</title>
  </head>
  <body>
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
    <#-- 
      <#assign titleMsg = vrtx.getMsg("actions.renameService.title") />
      <#assign actionURL = item.url />

      <a id="renameService" title="${titleMsg}" href="${actionURL}">${item.title}</a>
      -->

  </body>
</html>
