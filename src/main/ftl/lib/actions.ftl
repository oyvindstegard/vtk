<#macro genOkCancelButtons nameOk nameCancel msgOk msgCancel>
  <div id="submitButtons">
    <div class="vrtx-focus-button">
      <input type="submit" name="${nameOk}" value="<@vrtx.msg code="${msgOk}" default="Ok"/>" />
    </div>
    <div class="vrtx-button">
      <input type="submit" name="${nameCancel}" value="<@vrtx.msg code="${msgCancel}" default="Cancel"/>" />
    </div>
  </div>
</#macro>

<#macro genErrorMessages errors>
  <#if (errors?size > 0)>
    <div class="errorContainer">
      <ul class="errors">
        <#list spring.status.errorMessages as error> 
          <li>${error}</li> 
        </#list>
	  </ul>
    </div>
  </#if>
</#macro>