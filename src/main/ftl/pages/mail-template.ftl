<p>Hi!</p>

<p>${site} has an article I believe you will find interesting:</p>

<h2>${title}</h2>

<#if comment?exists && comment?has_content>
<p>${comment}</p>
</#if>

<p>Read the entire article here: <a href="${articleURI?html}">${articleURI?string}</a></p>

<p>Best regards, ${mailFrom}</p>

<hr />

<p>
This message is sent on behalf of ${mailFrom}.
Your email address will not be saved.
You will not receive further messages of this kind
unless someone tips you of other articles on ${site}.
</p>
