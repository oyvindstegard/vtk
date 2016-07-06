<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

<#if systemInfoMessage?exists && systemInfoMessage?has_content>
  <div id="system-message-wrapper">
    <div class="system-message">
      <div class="system-message-text">${systemInfoMessage?no_esc}</div>
    </div>
  </div>
</#if>
