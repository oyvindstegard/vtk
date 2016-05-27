<#ftl strip_whitespace=true output_format="XHTML" auto_esc=true>
<#import "/lib/vtk.ftl" as vrtx />

<#assign number = comments?size />

<#assign includeIfEmpty = true />
 <#if .vars['include-if-empty']?exists && .vars['include-if-empty'] = 'false'>
  <#assign includeIfEmpty = false />
 </#if>

<#if number &gt; 0 || includeIfEmpty>
<div class="vrtx-recent-comments">

  <a class="comments-title" href="${recentCommentsURL}"><@vrtx.msg code='commenting.comments.recent'
                   args=[resource.title] default='Recent comments' /></a>
  
  <#-- XXX: -->
  <#if .vars['max-comments']?exists>
    <#attempt>
      <#assign number = vrtx.parseInt(.vars['max-comments']) />
    <#recover>
      <#stop "'max-comments' is not a number: " + .vars['max-comments'] />
    </#attempt>
  </#if>
    
  <#if number &lt; 0>
    <#stop "Number must be a positive integer" />
  </#if>
  
  <#if number &gt; comments?size>
    <#assign number = comments?size />
  </#if>
 
 <#assign includeIfEmpty = true />
 <#if .vars['include-if-empty']?exists && .vars['include-if-empty'] = 'false'>
  <#assign includeIfEmpty = false />
 </#if>
 
  <#if comments?has_content>
    <ul class="items">
      <#list comments as comment>
        <#if comment_index &gt; number - 1><#break /></#if>
        <li>
          <a class="item-title" href="${(commentURLMap[comment.ID] + '#comment-' + comment.ID)}">
            ${comment.author.description} <@vrtx.msg code="commenting.comments.on" default="about" />
              "${resourceMap[comment.URI].title}"
          </a>
          <span class="published-date"><@vrtx.date value=comment.time format='long' /></span>
          <div class="item-description">
            <#assign description>
              <@vrtx.limit nchars=50 elide=true>
                <@vrtx.flattenHtml value=comment.content escape=false />
              </@vrtx.limit>
            </#assign>
            ${description}
          </div>
        </li>
      </#list>
    </ul>
  </#if>
  
  <a class="all-comments" href="${recentCommentsURL}"><@vrtx.msg code="commenting.comments.more" default="More ..." /></a>
  
</div>
</#if>
