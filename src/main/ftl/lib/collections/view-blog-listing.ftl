<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>

<#macro displayBlogs blogListing collection>
  <#assign introduction = vrtx.getIntroduction(collection) />
  <#assign introductionImage = vrtx.propValue(collection, "picture") />
  <#assign additionalContent = vrtx.propValue(collection, "additionalContents") />
  <#local listingView = "regular">
  <@articles.displayArticles page=page collectionListings=searchComponents listingView=listingView hideNumberOfComments=hideNumberOfComments displayMoreURLs=true />
</#macro>

<#macro listComments>

  <#if (comments?size > 0) >
    <div class="vrtx-recent-comments">
      <a class="comments-title" href="${moreCommentsUrl}"><@vrtx.msg code="commenting.comments.recent" /></a>
      <ul class="items">
        <#list comments as comment >
          <#local url = urlMap[comment.URI] + '#comment-' + comment.ID />
          <#local title = resourceMap[comment.URI].title />
          <li class="comment">
            <a class="item-title" href="${url}">
              ${comment.author.description} <@vrtx.msg code="commenting.comments.on" default="about" /> "${title}"
            </a>
            <span class="published-date"><@vrtx.date value=comment.time format='long' /></span>
            <div class="item-description">
              <@vrtx.limit nchars=50 elide=true>
                <@vrtx.flattenHtml value=comment.content escape=false />
              </@vrtx.limit>
            </div>
          </li>
        </#list>
      </ul>
      <a href="${moreCommentsUrl}" class="more-url"><@vrtx.msg code="commenting.comments.more" /></a>
     </div>
  </#if>
</#macro>
