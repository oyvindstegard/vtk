<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#attempt>
<#import "/spring.ftl" as spring />
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/actions.ftl" as actionsLib />

<#if type = "publish.globalPublishResourceConfirmedService">
  <div class="globalmenu expandedForm">
    <form name="vrtx-publish-document" id="vrtx-publish-document-form" action="${url}" method="post">
      <h3>${vrtx.getMsg("confirm-publish.confirmation.publish")}?</h3>
      <@actionsLib.genOkCancelButtons "publishResourceAction" "publishResourceCancelAction" "confirm-delete.ok" "confirm-delete.cancel" />
    </form>
  </div>
</#if>

<#recover>
${.error}
</#recover>
