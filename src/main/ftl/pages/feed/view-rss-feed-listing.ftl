<#ftl strip_whitespace=true output_format="XML" auto_esc=true>
<?xml version="1.0" encoding="utf-8"?>
<#import "/lib/vtk.ftl" as vrtx />
<rss xmlns:atom="http://www.w3.org/2005/Atom" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
  <channel>
    <title>${feedContent.title}</title>
    <link>${feedContent.link}</link>
    <description>${feedContent.description}</description>
    <itunes:summary>${feedContent.description}</itunes:summary>
    <#if feedContent.atomLink??>
    <atom:link href="${feedContent.atomLink}" rel="self" type="application/rss+xml" />
    </#if>
    <#if feedContent.feedLogoPath??>
    <itunes:image href="${vrtx.linkConstructor(feedContent.feedLogoPath, 'viewService')}" />
    </#if>
    <#list feedContent.feedItems as feedItem>
    <item>
      <title>${feedItem.title}</title>
      <#if feedItem.description??>
      <description>${feedItem.description}</description>
      </#if>
      <link>${feedItem.link}</link>
      <guid>${feedItem.guid}</guid>
      <pubDate>${feedItem.pubDate}</pubDate>
      <#if feedItem.category??>
      <category>${feedItem.category}</category>
      <#if feedItem.enclosure??>
      <enclosure url="${feedItem.enclosure}" length="${feedItem.length}" type="${feedItem.type}" />
      </#if>
      </#if>
    </item>
    </#list>
  </channel>
</rss>
