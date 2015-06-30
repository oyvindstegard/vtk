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
<#import "/lib/vtk.ftl" as vrtx />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title><@vrtx.msg code="feedback.send" default="Give us feedback" /></title>
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <#if cssURLs?exists>
    <#list cssURLs as cssUrl>
      <link href="${cssUrl}" type="text/css" rel="stylesheet" />
    </#list>
  </#if>
  <#if (displayUpscoping?exists && displayUpscoping = "true") || resourceContext.repositoryId = "uio.no">
    <link href="/vrtx/__vrtx/static-resources/themes/default/forms.css" type="text/css" rel="stylesheet" />
    <link href="/vrtx/__vrtx/static-resources/themes/default/forms-responsive.css" media="only screen and (max-width: 15.5cm) and (orientation : portrait), only screen and (max-width: 17.5cm) and (orientation : landscape)" type="text/css" rel="stylesheet" />

    <!--[if lte IE 7]>
      <link rel="stylesheet" href="/vrtx/__vrtx/static-resources/themes/default/default-ie7.css" type="text/css" /> 
    <![endif]--> 
  </#if>
  <#if jsURLs?exists>
    <#list jsURLs as jsURL>
      <script type="text/javascript" src="${jsURL}"></script>
    </#list>
  </#if>
  <meta name="robots" content="noindex" /> 
</head>
<body class="forms-new">
  <#assign closeURI = resourceContext.currentURI?split("?")[0]?html />

  <#if mailResponse?has_content && mailResponse = "OK">
    <p><@vrtx.msg code="feedback.form.success" args=[emailSentTo] /></p>
    <a class="vrtx-button vrtx-close-dialog" href="${closeURI}">
      <span><@vrtx.msg code="email.form.close" default="Close" /></span>
    </a>
    <script type="text/javascript"><!--
      var updatedTitle = '<@vrtx.msg code="feedback.thanks" default="Thank you for giving us feedback" />';
    // -->
    </script>
  <#else>
    <h1><@vrtx.msg code="feedback.send" default="Give us feedback" /></h1> 

    <#if !like?exists || (like?exists && like = "false")>
      <form id="feedback-form" method="post" action="?vrtx=send-feedback">
           
        <label for="yourComment" style="display: none;"><@vrtx.msg code="feedback.form.yourcomment" default="Your comment" /></label> 
        <#if yourSavedComment?exists && yourSavedComment?has_content>
          <textarea class="round-corners" rows="15" cols="10" id="yourComment" name="yourComment">${yourSavedComment?html}</textarea>
        <#else>
          <textarea class="round-corners" rows="15" cols="10" id="yourComment" name="yourComment"></textarea> 
        </#if>
        
        <input type="hidden" id="user-agent-viewport" name="userAgentViewport" value="" />
        <script type="text/javascript"><!--
          $("#user-agent-viewport").val(navigator.userAgent + " " + ((window.innerWidth && window.innerHeight) ? ("(" + window.innerWidth + "x" + window.innerHeight + "px)") : ""));
        // -->
        </script>
           
        <#if mailto?has_content>
          <input type="hidden" name="mailto" value="${mailto?html}" />
        </#if>
        <#if contacturl?has_content>
          <input type="hidden" name="contacturl" value="${contacturl?html}" />
        </#if>
        
        <p><@vrtx.msg code="feedback.cant-respond" default="Questions?" /></p>
        <p>
        <#if contacturl?has_content>
          <a id="vrtx-feedback-contact" target="_top" href='${contacturl?html}'>
        <#else>
          <a id="vrtx-feedback-contact" target="_top" href='<@vrtx.msg code="feedback.contact-link" default="http://www.uio.no/english/about/contact/" />'>
        </#if>
          <@vrtx.msg code="feedback.contact" default="Contact our official contact points." />
        </a>
        </p>
           
        <div id="submitButtons">
          <div class="vrtx-focus-button"> 
            <input type="submit" class="submit-email-form" value="Send" name="submit" />
          </div>
          <a class="vrtx-close-dialog" href="${closeURI}">
            <@vrtx.msg code="email.form.cancel" default="Cancel" />
          </a>
        </div>
      </form>
    </#if>
       
    <#if mailResponse?has_content>
      <div id="email-response"> 
        <#if mailResponse = "failure-empty-fields">
          <span class="failure"><@vrtx.msg code="feedback.form.fail.null" default="You have to write a comment" />.</span>
        <#elseif mailResponse = "failure-invalid-emails">
          <span class="failure"><@vrtx.msg code="feedback.form.fail.invalidate" default="One of the e-mail addresses is invalid" />.</span>
        <#elseif mailResponse = "failure-general">
          <span class="failure"><@vrtx.msg code="feedback.form.fail.general" default="Feedback was not sent" /><#if mailResponseMsg?has_content>${mailResponseMsg}</#if>.</span>
        </#if>
      </div>
    </#if>
  </#if>
</body>
</html>
