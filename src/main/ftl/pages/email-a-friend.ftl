<#ftl strip_whitespace=true output_format="HTML" auto_esc=false>
<#--
  - File: email-a-friend.ftl
  - 
  - Description: Displays a send to approval form.
  -
  - Optional model data:
  -   form
  -   model
  -->
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Send for approval - ${resource.title}</title>
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <#if cssURLs?exists>
    <#list cssURLs as cssUrl>
      <link href="${cssUrl}" type="text/css" rel="stylesheet" />
    </#list>
  </#if>
  <link href="/vrtx/__vrtx/static-resources/themes/default/forms.css" type="text/css" rel="stylesheet" />
  <link href="/vrtx/__vrtx/static-resources/themes/default/forms-responsive.css" media="only screen and (max-width: 15.5cm) and (orientation : portrait), only screen and (max-width: 17.5cm) and (orientation : landscape)" type="text/css" rel="stylesheet" />

  <!--[if lte IE 7]>
    <link rel="stylesheet" href="/vrtx/__vrtx/static-resources/themes/default/default-ie7.css" type="text/css" />
  <![endif]-->
  <meta name="robots" content="noindex" />
 </head>
 <body class="forms-new">
   <#assign closeURI = resourceContext.currentURI?split("?")[0] />
 
   <#if mailResponse?has_content && mailResponse = "OK">
     <p><@vrtx.msg code="email.form.success" args=[emailSentTo] /></p>
     <a class="vrtx-button vrtx-close-dialog" href="${closeURI}">
       <span><@vrtx.msg code="email.form.close" default="Close" /></span>
     </a>
   <#else>
    <h1><@vrtx.msg code="decorating.emailAFriendComponent.emaillink" /></h1>
    <h2>${resource.title}</h2>
     <form id="email-a-friend-form" method="post" action="${closeURI}?vrtx=email-a-friend">
       
       <label for="emailTo"><@vrtx.msg code="email.form.to" default="Send e-mail to" /></label>
       <div class="vrtx-textfield">
         <#if emailSavedTo?exists && emailSavedTo?has_content>
           <input type="text" id="emailTo" name="emailTo" value="${emailSavedTo}" />
         <#else>
           <input type="text" id="emailTo" name="emailTo" value="" />
         </#if>
       </div>
       <div class="email-help"><@vrtx.msg code="email.form.to-tooltip" default="Use comma as a separator if sending to more than one e-mail recipient" /></div>
      
       <label for="emailFrom"><@vrtx.msg code="email.form.from" default="Your e-mail address" /></label>
       <div class="vrtx-textfield">
         <#if emailSavedFrom?exists && emailSavedFrom?has_content>
           <input type="text" id="emailFrom" name="emailFrom" value="${emailSavedFrom}" />
         <#else>
           <input type="text" id="emailFrom" name="emailFrom" value="" />
         </#if>
       </div>
       
       <label for="yourComment"><@vrtx.msg code="email.form.yourcomment" default="Your comment" /></label>
       <#if yourSavedComment?exists && yourSavedComment?has_content>
         <textarea class="round-corners" rows="6" cols="10" id="yourComment" name="yourComment">${yourSavedComment}</textarea>
       <#else>
         <textarea class="round-corners" rows="6" cols="10" id="yourComment" name="yourComment" value=""></textarea>
       </#if>
  
       <div id="submitButtons">
         <div class="vrtx-focus-button">
           <input type="submit" class="submit-email-form" value="Send" name="submit" />
         </div>
          <a class="vrtx-close-dialog" href="${closeURI}">
            <@vrtx.msg code="email.form.cancel" default="Cancel" />
          </a>
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
