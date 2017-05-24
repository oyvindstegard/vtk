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
        <form name="manage.expandArchiveService" id="manage.expandArchiveService-form" action="${command.submitURL}" method="post">
          <h3 class="nonul"><@vrtx.msg code="actions.expandArchive" default="Expand archive"/>:</h3>
          <@spring.bind "command.name" /> 
          <@actionsLib.genErrorMessages spring.status.errorMessages />
          <input class="vrtx-textfield" type="text" size="30" name="${spring.status.expression}" value="${spring.status.value?if_exists}" />
          <p>Enter comma separated list of paths to ignore. Paths <b>MUST</b> be entered as they are written in the manifest, i.e. all 
            start with a slash ("/") and collections also end with one.</p>
          <input class="vrtx-textfield" type="text" size="30" name="ignorableResources" id="ignorableResources" value="" />
          <p style="font-size: 0.769em">Disclaimer: If you don't know what the contents of this field does, then for the love of God don't put anything in it.</p>
          <@actionsLib.genOkCancelButtons "save" "cancelAction" "actions.expandArchive.save" "actions.expandArchive.cancel" />
        </form>
      </div>
    </#if>
  </body>
</html>
