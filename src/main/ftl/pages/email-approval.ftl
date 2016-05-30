<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: email-a-friend.ftl
  - 
  - Description: Displays a email-a-friend form.
  -
  - Optional model data:
  -   form
  -   model
  -->
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${resource.title}</title>
  <meta name="robots" content="noindex"/> 
</head>
<body>
  <#if mailResponse?has_content && mailResponse = "OK">
     <p><@vrtx.msg code="email.form.success" args=[emailSentTo] /></p>
     <button class="vrtx-button" id="email-approval-success" onclick='javascript:$("#dialog-html-send-approval").dialog("close");'><@vrtx.msg code="email.form.close" default="Close" /></button>
  <#else>
    <#assign uri = vrtx.linkConstructor("", "emailApprovalService") />

    <form id="email-approval-form" method="post" action="${uri}">
      <@vrtx.csrfPreventionToken uri />
      
      <label for="emailTo" class="first"><@vrtx.msg code="email.form.to" default="Send e-mail to" /></label> 
      <#if emailSavedTo?has_content>
        <input class="vrtx-textfield" type="text" id="emailTo" name="emailTo" value="${emailSavedTo}" />
      <#else>
        <input class="vrtx-textfield" type="text" id="emailTo" name="emailTo" value="<#if editorialContacts??>${editorialContacts}</#if>" />
      </#if>
      <div class="email-help"><@vrtx.msg code="email.form.to-tooltip" default="Use comma as a separator if sending to more than one e-mail recipient" /></div> 
      
      <#if userEmailFrom??>
        <label for="emailFrom"><@vrtx.msg code="email.form.from" default="Your e-mail address" /></label>
        <#if emailSavedFrom?has_content>
          <input class="vrtx-textfield" type="text" id="emailFrom" name="emailFrom" value="${emailSavedFrom}" />
        <#else>
          <input class="vrtx-textfield" type="text" id="emailFrom" name="emailFrom" value="" />
        </#if>
      </#if>
      
      <#if emailBody?has_content>
        <label><@vrtx.msg code="email.form.text" default="E-mail text" /></label>
        <div id="emailBody">
          <strong>${emailSubject}</strong>
          ${emailBody}
        </div>
      </#if>
       
      <label for="yourCommentTxtArea"><@vrtx.msg code="email.form.yourcomment" default="Your comment" /></label> 
      <#if yourSavedComment?has_content>
        <textarea class="round-corners" rows="6" cols="10" id="yourCommentTxtArea" name="yourComment">${yourSavedComment}</textarea>
      <#else>
        <textarea class="round-corners" rows="6" cols="10" id="yourCommentTxtArea" name="yourComment" value=""></textarea> 
      </#if>
      
      <div id="submitButtons">
        <input class="vrtx-focus-button submit-email-form" type="submit" value="${vrtx.getMsg('send-to-approval.submit')}" name="submit" />
        <input class="vrtx-button cancel-email-form" type="button" onclick='javascript:$("#dialog-html-send-approval").dialog("close");' value="${vrtx.getMsg('editor.cancel')}" name="cancel" />
      </div>
    </form>

    <#if mailResponse?has_content>
      <div id="email-response">
        <#if mailResponse = "failure-empty-fields">
          <span class="failure"><@vrtx.msg code="email.form.fail.null" default="One or more of the e-mail addresses are empty" />.</span>
        <#elseif mailResponse = "failure-invalid-emails">
          <span class="failure"><@vrtx.msg code="email.form.fail.invalidate" default="One of the e-mail addresses is not valid" />.</span>
        <#elseif mailResponse = "failure-general">
          <span class="failure"><@vrtx.msg code="email.form.fail.general" default="E-mail was not sent" /><#if mailResponseMsg?has_content>${mailResponseMsg}</#if>.</span>
        </#if> 
      </div>
    </#if>
  </#if>
</body>
</html>
