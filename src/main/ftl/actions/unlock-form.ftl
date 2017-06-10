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
    <div class="globalmenu expandedForm">
      <form id="manage.unlockFormService-form" method="post" action="${unlockFormCommand.submitURL}" name="unlockForm">
        <h3><@vrtx.msg code="resourceMenuRight.manage.unlockFormService" default="Unlock"/></h3>
        <#if resourceContext.currentResource.lock?exists>
          <#assign owner = resourceContext.currentResource.lock.principal.qualifiedName />
          <#assign currentPrincipal = resourceContext.principal.qualifiedName />
        </#if>
        <#if owner?exists && owner != currentPrincipal>
          <p>${vrtx.getMsg("unlockwarning.steal")}: <strong>${owner}</strong>.</p> 
          <p>${vrtx.getMsg("unlockwarning.modified")}: <strong>${resourceContext.currentResource.lastModified?datetime}</strong>.</p>
          <p>${vrtx.getMsg("unlockwarning.explanation")}</p>
        </#if>
        <@actionsLib.genOkCancelButtons "unlock" "cancel" "unlockwarning.unlock" "unlockwarning.cancel" />
      </form>
    </div>
  </body>
</html>
