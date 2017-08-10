<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: metrics.ftl
  -
  - Description:
  -
  - Required model data:
  - - "gauges" - map of Metrics gauges
  - - "meters" - map of Metrics meters
  - - "counters" - map of Metrics counters
  -->

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title>Metrics</title>
  <style type="text/css">
    td { padding-right: 2em; }
    .value { font-weight: bold }
  </style>
</head>
<body>

<#if meters?has_content>
  <h2>Meters</h2>
  <table>
  <#list meters as key, value>
    <tr>
      <td>${key}</td><td class="value">${value.getCount()}</td>
    </tr>
  </#list>
  </table>
</#if>

<#if counters?has_content>
  <h2>Counters</h2>
  <table>
  <#list counters as key, value>
    <tr>
      <td>${key}</td><td class="value">${value.getCount()}</td>
    </tr>
  </#list>
  </table>
</#if>

<#if histograms?has_content>
  <h2>Histograms</h2>
  <table>
  <#list histograms as key, value>
    <#assign snapshot = value.getSnapshot() >
    <tr>
      <td>${key}</td><td>mean: <span class="value">${snapshot.getMean()}</span>,
        min: <span class="value">${snapshot.getMin()}</span>, max: <span class="value">${snapshot.getMax()}</span></td>
    </tr>
  </#list>
  </table>
</#if>

<#if gauges?has_content>
  <h2>Gauges</h2>
  <table>
  <#list gauges as key, value>
    <tr>
      <td>${key}</td>
      <td class="value">
        <#if value.getValue()?is_enumerable>
          ${value.getValue()?join(", ")}
        <#else>
          ${value.getValue()}
        </#if>
      </td>
    </tr>
  </#list>
  </table>
</#if>

<#if timers?has_content>
  <h2>Timers</h2>
  <table>
  <#list timers as key, value>
    <#assign snapshot = value.getSnapshot() >
    <tr>
      <td>${key}</td><td>mean: <span class="value">${snapshot.getMean()}</span>,
min: <span class="value">${snapshot.getMin()}</span>, max: <span class="value">${snapshot.getMax()}</span></td>
    </tr>
  </#list>
  </table>
</#if>

</body>
