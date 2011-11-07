<#ftl strip_whitespace=true>
<#--
  - File: email-a-friend.ftl
  - 
  - Description: Displays a link to the Email-A-Friend service
  - 
  -->

<#import "/lib/vortikal.ftl" as vrtx/>

<#if !emailLink?exists || !emailLink.url?exists>
  <#stop "Missing 'emailLink' entry in model"/>
</#if>

<!-- begin email a friend js -->
<#if jsURLs?exists>
  <#list jsURLs as jsURL>
    <script type="text/javascript" src="${jsURL}"></script>
  </#list>
</#if>
<!-- end email a friend js -->

<a class="vrtx-email-friend thickbox" title='<@vrtx.msg code="tip.emailtitle" default="E-mail a friend" />' href="${emailLink.url?html}&amp;height=400&amp;width=345"><@vrtx.msg code="decorating.emailAFriendComponent.emaillink" default="E-mail a friend" /></a>