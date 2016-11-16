<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Not Found</title>
</head>
<body>

<h1>Page does not exist</h1>

<p>
The web page <strong>${resourceContext.currentURI?if_exists}</strong>
that you seek cannot be found on this web site. Either the link that
you have used is wrong, or the page is outdated or moved to another
location.
</p>

<p>If you have followed a link from another web site, you can use the
&#148;back&#148; button on your web browser and inform the webmaster
of the referring site that the link does not work.
</p>

<#if locations?has_content>
  Are you looking for any of these resources?
  <ul>
  <#list locations as loc>
    <li><a href="${loc.URI}">${loc.title}</a> (${loc.URI})
  </#list>
  </ul>
</#if>

<p>Server-administrator: <a href="mailto:${webmaster}">${webmaster}</a></p>

</body>
</html>
