<p>Hei!</p>

<p>${site} har ein artikkel eg trur kan vera interessant for deg:</p>

<h2>${title}</h2>

<#if comment?exists && comment?has_content>
<p>${comment}</p>
</#if>

<p>Les heile artikkelen her: <a href="${articleURI?html}">${articleURI?string}</a></p>

<p>Med venleg helsing ${mailFrom}</p>

<hr />
<p>
Denne meldinga er sendt på oppmoding frå ${mailFrom}.
Di e-postadresse vert ikkje lagra.
Du vil ikkje få tilsendt fleire meldinger som denne,
med mindre nokon tipsar deg om andre artiklar på ${site}.
</p>
