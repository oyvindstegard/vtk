<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/menu/list-menu.ftl" as listMenu />
<#if tabMenuRight?exists>
  <@listMenu.listMenu menu=tabMenuRight displayForms=true/>
</#if>
