<#if resourceDetail?? && resourceDetail.hasWorkingCopy?? && resourceDetail.hasWorkingCopy>
  <p>${title} er endret. Endringen må godkjennes før den blir synlig på nettstedet.</p>
<#else>
  <p>${title} er klar for publisering. Publiseringen må godkjennes før ressursen blir synlig på nettstedet.</p>
</#if>

<p><a href="${uri?html}">${uri?html}</a></p>

<#if comment?has_content>
<p>Kommentar:</p>
<pre>${comment}</pre>
</#if>

<#t /><p>Mer om hvordan du godkjenner:<br/><#t />
<#t /><a href="http://www.uio.no/tjenester/it/web/vortex/hjelp/admin/rettigheter/godkjenning/">http://www.uio.no/tjenester/it/web/vortex/hjelp/admin/rettigheter/godkjenning/</a></p><#t />