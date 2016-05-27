<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#--
  - File: lost-post.ftl
  - 
  - Description: "replay" lost POST
  - 
  - Required model data:
  -   postURL - the URL to post to
  -   body - map of form input fields
  -   autosubmit - whether to autosubmit the form
  -
  -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Lost post</title>
  </head>
<#if autosubmit?exists && autosubmit == "true">
  <body onload="document.vrtxLostPost.submit.click()">
<#else>
  <body>
</#if>
    <form name="vrtxLostPost" action="${postURL}" method="post">
      <p>A problem has occured while processing your posted data. Your login session
        was not found or was invalid.</p><p>This can typically happen in the following situations:</p>
      <ul>
        <li>Logging out in a different browser window/tab while editing a document.</li>
        <li>Switching internet connection while editing a document.</li>
        <li>Leaving the editor open without any activity for a long time.</li>
      </ul>
      <#list body?keys as name>
        <#if 'csrf-prevention-token' == name>
          <@vrtx.csrfPreventionToken url=postURL />
        <#else>
          <#list body[name] as value>
            <input type="hidden" name="${name}" value="${value}" />
          </#list>
        </#if>
      </#list>
      You can retry the operation by clicking on this button:
      <input name="submit" type="submit" value="Submit form again" />
    </form>
  </body>
</html>
