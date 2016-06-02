<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />
<#import "/lib/view-utils.ftl" as viewutils />

<#assign resource = resourceContext.currentResource />
<#assign title = vrtx.propValue(resource, "title") />

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>${title} - ${vrtx.getMsg('publishing.published.false.title', 'This webpage is not published')}</title>
    <meta name="robots" content="noindex, nofollow" />
  </head>
  <body>
    <h1>${vrtx.getMsg('publishing.published.false.title', 'This webpage is not published')}</h1>
    <p>${vrtx.getMsg('publishing.published.false.desc', 'To see this webpage you have to manage the page', [manageLink.url])?no_esc}.<p>

  </body>
</html>
