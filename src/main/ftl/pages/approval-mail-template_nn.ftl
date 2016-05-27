<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#if resourceDetail?? && resourceDetail.hasWorkingCopy?? && resourceDetail.hasWorkingCopy>
  <p><a href="${uri}">${uri}</a> er endra. Endringa må godkjennast før den blir synleg på nettstaden.</p>
<#else>
  <p><a href="${uri}">${uri}</a> er klar for publisering. Publiseringa må godkjennast før ressursen blir synleg på nettstaden.</p>
</#if>

<#if comment?has_content>
<p>Kommentar:</p>
<pre>${comment}</pre>
</#if>

<#t /><p>Meir om korleis du godkjenner:<br/><#t />
<#t /><a href="http://www.uio.no/tjenester/it/web/vortex/hjelp/admin/rettigheter/godkjenning/">http://www.uio.no/tjenester/it/web/vortex/hjelp/admin/rettigheter/godkjenning/</a></p><#t />
