<p>Hei!</p>

<p>${site} har ein artikkel eg trur kan vera interessant for deg:</p>

<h2>${title}</h2>

<#if comment?has_content>
<pre>${comment}</pre>
</#if>

<p>Les heile artikkelen her: <a href="${uri?html}">${uri?html}</a></p>

<p>Med vennleg helsing ${mailFrom}</p>

<hr />
<p>
Denne meldinga er sendt på oppmoding frå ${mailFrom}.
Di e-postadresse vert ikkje lagra.
Du vil ikkje få tilsendt fleire meldinger som denne,
med mindre nokon deler andre artiklar på ${site}.
</p>
