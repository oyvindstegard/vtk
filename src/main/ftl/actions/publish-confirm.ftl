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
        <form name="vrtx-publish-document" id="vrtx-publish-document-form" action="${url}" method="post">
          <#if type = "publish.globalPublishResourceConfirmedService">
            <h3>${vrtx.getMsg("confirm-publish.confirmation.publish")}?</h3>
          <#else>
            <h3>${vrtx.getMsg("confirm-publish.confirmation.unpublish")}?</h3>
          </#if>
          <@actionsLib.genOkCancelButtons "publishResourceAction" "publishResourceCancelAction" "confirm-delete.ok" "confirm-delete.cancel" />
        </form>
      </div>
  </body>
</html>
