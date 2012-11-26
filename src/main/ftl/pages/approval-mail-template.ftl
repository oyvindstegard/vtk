<p>Hi!</p>

<#if resourceDetail?? && resourceDetail.hasWorkingCopy?? && resourceDetail.hasWorkingCopy>
  <p>Can you make the working copy for &laquo;${title}&raquo; into the public version?</p>
<#else>
  <p>Can you publish &laquo;${title}&raquo;?</p>
</#if>

<p>Link to document: <a href="${uri?html}">${uri?html}</a></p>

<#if comment?has_content>
<pre>${comment}</pre>
</#if>

<p>Link til documentation: <a href="${uri?html}">${uri?html}</a></p>

<#t /><p>Best regards,<br/><#t />
<#t />${mailFromFullName}</p><#t />
