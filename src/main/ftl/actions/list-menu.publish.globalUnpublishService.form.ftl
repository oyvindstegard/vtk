<#ftl strip_whitespace=true output_format="HTML" auto_esc=false>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if type = "publish.globalUnpublishResourceConfirmedService">
  <div class="globalmenu expandedForm">
    <form name="vrtx-unpublish-document" id="vrtx-unpublish-document-form" action="${url}" method="post">
      <h3>${vrtx.getMsg("confirm-publish.confirmation.unpublish")}?</h3>
      <@actionsLib.genOkCancelButtons "publishResourceAction" "publishResourceCancelAction" "confirm-delete.ok" "confirm-delete.cancel" />
    </form>
  </div>
</#if>

<#recover>
${.error}
</#recover>
