<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: response-info.ftl
  - 
  - Description: Page displaying various debug info related to the
  - current servlet request/response
  - 
  - Required model data:
  -  
  - Optional model data:
  -
  -->  
<#if !responseInfo?exists>
  <#stop "This template only works with 'responseInfo' model map supplied." />
</#if>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Response info</title>
</head>
<body>
  <h1>Response info</h1>
  <h2>Request</h2>
  <p>URL: <a href="${responseInfo.requestURL}">${responseInfo.requestURL}</a></p>
  <p>Method: ${responseInfo.method}</p>
  <p>
    Parameters: 
    <ul>
    <#list responseInfo.requestParameters?keys as name>
      <li>
        <pre>${name} = ${responseInfo.requestParameters[name]?if_exists}
      </li>
    </#list>
    </ul>
  </p>
  <h2>Response</h2>
  <p>Status: ${responseInfo.status}</p>
  <p>Duration: ${responseInfo.duration} ms</p>
  <ul>
  <#list responseInfo.headers?keys as name>
    <#list responseInfo.headers[name] as value>
      <li>
        <pre>${name}: ${value}</pre>
      </li>
    </#list>
  </#list>
  </ul>
</body>
</html>
