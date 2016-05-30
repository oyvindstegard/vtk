<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#attempt>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if command?exists && !command.done>
  <div class="globalmenu expandedForm">
    <form name="deleteResourceService" id="deleteResourceService-form" action="${command.submitURL}" method="post">
      ${vrtx.getMsg("collectionListing.confirmation.delete")}: <strong>${command.name}</strong>
      <@actionsLib.genOkCancelButtons "save" "cancelAction" "confirm-delete.ok" "confirm-delete.cancel" />
    </form>
  </div>
</#if>

<#recover>
${.error}
</#recover>
