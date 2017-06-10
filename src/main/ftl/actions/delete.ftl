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
    <#if command?exists && !command.done>
      <div class="globalmenu expandedForm">
        <form name="deleteResourceService" id="deleteResourceService-form" action="${command.submitURL}" method="post">
          ${vrtx.getMsg("collectionListing.confirmation.delete")}: <strong>${command.name}</strong>
          <@actionsLib.genOkCancelButtons "save" "cancelAction" "confirm-delete.ok" "confirm-delete.cancel" />
        </form>
      </div>
    </#if>
  </body>
</html>
