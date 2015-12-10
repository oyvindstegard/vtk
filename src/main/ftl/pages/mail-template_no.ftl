<p>Hei!</p>

<p>${site} har en artikkel jeg tror kan være interessant for deg:</p>

<h2>${title}</h2>

<#if comment?has_content>
<pre>${comment}</pre>
</#if>


<p>Les hele artikkelen her: <a href="${uri?html}">${uri?html}</a></p>

<p>Med vennlig hilsen ${mailFrom}</p>

<hr />

<p>
Denne meldingen er sendt på oppfordring fra ${mailFrom}.
Din e-postadresse blir ikke lagret.
Du vil ikke motta flere meldinger av denne typen,
med mindre noen deler andre artikler på ${site}.
</p>
