<#ftl strip_whitespace=true>
<#--
  - File: email-a-friend.ftl
  - 
  - Description: Displays a email-a-friend form.
  -
  - Optional model data:
  -   form
  -   model
  -->
<#import "/lib/vortikal.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>${resource.title}</title>
  <meta name="robots" content="noindex"/> 
 </head>
 <body>
   <#if tipResponse?has_content && tipResponse = "OK">
     <p><@vrtx.msg code="email.form.success" args=[emailSentTo] /></p>
   <#else>
     <#assign uri = vrtx.linkConstructor(resource.URI, "emailApprovalService") />

     <form id="email-approval-form" method="post" action="${uri}">
       <@vrtx.csrfPreventionToken uri />
      
       <label for="emailToField" class="first"><@vrtx.msg code="email.form.to" default="Send e-mail to" /></label> 
       <div class="vrtx-textfield">  
         <#if emailSavedTo?exists && emailSavedTo?has_content>
           <input type="text" id="emailToField" name="emailTo" value="${emailSavedTo?html}" />
         <#else>
           <input type="text" id="emailToField" name="emailTo" value="" />
         </#if>
       </div>
       <div class="email-help"><@vrtx.msg code="email.form.to-tooltip" default="Use comma as a separator if sending to more than one e-mail recipient" /></div> 
      
       <label for="emailFromField"><@vrtx.msg code="email.form.from" default="Your e-mail address" /></label>  
       ${emailSavedFrom?html}
       
       <label for="yourCommentTxtArea"><@vrtx.msg code="email.form.yourcomment" default="Your comment" /></label> 
       <#if yourSavedComment?exists && yourSavedComment?has_content>
         <textarea class="round-corners" rows="6" cols="10" id="yourCommentTxtArea" name="yourComment">${yourSavedComment?html}</textarea>
       <#else>
         <textarea class="round-corners" rows="6" cols="10" id="yourCommentTxtArea" name="yourComment" value=""></textarea> 
       </#if>
      
       <div id="submitButtons">
         <div class="vrtx-focus-button"> 
           <input type="submit" class="submit-email-form" value="Send" name="submit" />
         </div>
         <div class="vrtx-button"> 
           <input type="button" onclick="javascript:vrtxSimpleDialogs.closeDialog('#dialog-html-send-approval');" class="cancel-email-form" value="${vrtx.getMsg('editor.cancel')}" name="cancel" />
         </div>  
       </div>
    </form>
       
    <#-- Postback from Controller -->
    <#if tipResponse?has_content>
      <div id="email-response">
      <#if tipResponse = "FAILURE-NULL-FORM">
        <span class="failure"><@vrtx.msg code="email.form.fail.null" default="One or more of the e-mail addresses are empty" />.</span>
      <#elseif tipResponse = "FAILURE-INVALID-EMAIL">
        <span class="failure"><@vrtx.msg code="email.form.fail.invalidate" default="One of the e-mail addresses is not valid" />.</span>
      <#elseif tipResponse = "FAILURE">
        <span class="failure"><@vrtx.msg code="email.form.fail.general" default="E-mail was not sent" /><#if tipResponseMsg?exists && tipResponseMsg?has_content>${tipResponseMsg}</#if>.</span>
      </#if> 
      </div>
    </#if>  
  </#if>
</body>
</html>
