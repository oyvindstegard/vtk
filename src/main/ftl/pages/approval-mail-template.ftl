<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#if resourceDetail?? && resourceDetail.hasWorkingCopy?? && resourceDetail.hasWorkingCopy>
  <p><a href="${uri}">${uri}</a> is changed. The change must be approved before it becomes visible on the web page.</p>
<#else>
  <p><a href="${uri}">${uri}</a> is ready to be published. The publishing must be approved before the resource becomes visible on the web page:</p>
</#if>

<#if comment?has_content>
<p>Comment:</p>
<pre>${comment}</pre>
</#if>

<#t /><p>More about how to approve:<br/><#t />
<#t /><a href="http://www.uio.no/english/services/it/web/vortex/help/getting-started/permissions.html">http://www.uio.no/english/services/it/web/vortex/help/getting-started/permissions.html</a></p><#t />
