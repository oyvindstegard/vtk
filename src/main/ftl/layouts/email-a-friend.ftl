<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: email-a-friend.ftl
  - 
  - Description: Displays a link to the Email-A-Friend service
  - 
  -->

<#import "/lib/vtk.ftl" as vrtx/>

<#-- VTK-4124: generate mailto:-link instead
<#if !emailLink?exists || !emailLink.url?exists>
  <#stop "Missing 'emailLink' entry in model"/>
</#if>
-->

<#assign title = vrtx.propValue(resourceContext.currentResource, "title") />
<#assign url = resourceContext.currentServiceURL />

<a class="vrtx-email-friend" title='<@vrtx.msg code="tip.emailtitle" default="E-mail this page" />'
   href="mailto:?subject=${title?url('UTF-8')}&amp;body=${url?url('UTF-8')}">
   <@vrtx.msg code="decorating.emailAFriendComponent.emaillink" default="E-mail this page" />
</a>
