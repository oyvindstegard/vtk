<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#--
  - File: properties-listing.ftl
  - 
  - Description: A HTML page that displays resource properties
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
  <head>
    <title>Stats</title>
  </head>
  <body>
    <#list managementStats?keys as key>
      <h2>${key}</h2>
      <pre><#list managementStats[key]?keys as itemKey>${itemKey}: ${managementStats[key][itemKey]}</#list></pre>
    </#list>
  </body>
</html>
