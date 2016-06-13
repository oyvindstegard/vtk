<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: error-detail.ftl
  - 
  - Description: Component for displaying error info, such as the
  - error message and exception stack traces
  - 
  - Required model data:
  -   error
  -  
  - Optional model data:
  -   debug (produces stack traces)
  -
  -->

<#-- Print exception detail and stack trace if debug is set: -->
  <h1 class="errorHeader">${error.errorDescription}</h1>
  <p class="errorMessage">${error.exception.message?default('No message')}</p>
<#if !debug><!--</#if>
  <div class="exception">
    <h2>Error detail (for debugging purposes):</h2>
    <div class="exceptionClass">Exception: ${error.exception.class.name}</div>
    <div class="exceptionMessage">Message: ${error.exception.message?default('No message')}</div>
    
    <div class="exceptionStacktrace">
      Stacktrace:
      <ul class="stacktrace">
        <#list error.exception.stackTrace as traceLine>
          <li>${traceLine}</li>
        </#list>
      </ul>
    </div>
  </div>
<#if !debug>--></#if>

<#-- If the exception has a cause, print it too: -->
<#if error.exception.cause?exists>
<#if !debug><!--</#if>
  <div class="exception">
    <h2>Caused by:</h2>
    <div class="exceptionClass">Exception: ${error.exception.cause.class.name}</div>
    <div class="exceptionMessage">Message: ${error.exception.cause.message?default('No message')}</div>
    
    <div class="exceptionStacktrace">
      Stacktrace:
      <ul class="stacktrace">
        <#list error.exception.cause.stackTrace as traceLine>
          <li>${traceLine}</li>
        </#list>
      </ul>
    </div>
  </div>
<#if !debug>--></#if>
</#if>
