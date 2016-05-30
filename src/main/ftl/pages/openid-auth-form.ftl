<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: openid-auth-form.ftl
  - 
  - Description: Displays an OpenID authentication form
  - 
  - Required model data:
  -   resource
  -   resourceReference
  -  
  - Optional model data:
  -   title
  -
  -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
  <html xmlns="http://www.w3.org/1999/xhtml">
    <head><title>${(title.title)?default('Log in')}</title>
  </head>
  <body>

    <h1>Log in</h1>
    <form action="${resourceReference}" method="POST">
      Log in using OpenID: <input type="text" name="openid_identifier" />
      <input type="submit" value="OK" />
    </form>
  </body>
</html>
