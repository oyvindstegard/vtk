<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: toc.ftl
  - 
  - Description: Displays a Table of Contents based on h2/h3 in the document
  - 
  -->

<#import "/lib/vtk.ftl" as vrtx/>

<#if !url?exists>
  <#stop "Missing 'url' in model"/>
</#if>

<#assign headerTitle><@vrtx.msg code="decorating.tocComponent.header" default="Table of Contents"/></#assign>

<#if title?has_content>
  <#assign headerTitle = title />
</#if>

<#assign tocHeader>'<span class="vrtx-toc-header">${headerTitle}<\/span>'</#assign>

<script type="text/javascript"><!--
  document.write(${tocHeader});
// -->
</script>

<div id="toc"></div>

<script type="text/javascript" src="${url}"></script>
