<#ftl strip_whitespace=true>
<#--
  - File: feedback.ftl
  - 
  - Description: Displays a link to the feedback service
  - 
  -->

<#import "/lib/vtk.ftl" as vrtx/>

<#if emailLink?exists && emailLink.url?exists>
  <#assign link = emailLink.url />
  
   <!-- begin feedback js -->
  <script type="text/javascript"><!--
    $(function() {
      if (typeof urchinTracker !== "undefined" && typeof uioTrackVirtual === "function") {
        $(".feedback-yes").click(function(e) {
          $(".vrtx-feedback ul").replaceWith('<p class="vrtx-feedback-thanks"><@vrtx.msg code="feedback.thanks" default="Thank you for giving us feedback" /><div class="vrtx-feedback-thanks-slider"></div></p>');
          $(".vrtx-feedback-thanks-slider").animate({ left: 200 },
                                                    { queue: false,
                                                      duration: 200 });
          uioTrackVirtual("/like" + document.location.pathname);
          e.stopPropagation();
          e.preventDefault(); 
        });
        $(".feedback-no").click(function() {
          uioTrackVirtual("/dislike" + document.location.pathname);
        });
      } else {
        var noLink = $(".vrtx-feedback a.feedback-no").parent();
        noLink.find("a").removeClass("feedback-no")
                        .addClass("feedback")
                        .text('<@vrtx.msg code="feedback.send" default="Give us feedback" />');
        $(".vrtx-feedback ul").replaceWith('<p>' + noLink.html() + '</p>');
      }
    });
  // -->
  </script>
  <!-- end feedback js -->
  
  <!-- start feedback css -->
  <style type="text/css">
    .vrtx-feedback,
    .vrtx-feedback-thanks {
      position: relative;
    }
    .vrtx-feedback-thanks-slider {
      background: #fff;
      width: 200px;
      position: absolute;
      left: 0px;
      top: 0px;
      height: 18px;
    }
  </style>
  <!-- end feedback css -->

  <div class="vrtx-feedback">
    <span class="vrtx-feedback-title"><@vrtx.msg code="feedback.title" default="Did you find what you were looking for?" /></span>
    <#if mailto?has_content>
      <#assign link = link + "&mailto=" + mailto?url('UTF-8') />
    </#if>
    <#if contacturl?has_content>
      <#assign link = link + "&contacturl=" + contacturl?url('UTF-8') />
    </#if>
    <ul>
      <li>
        <a target="_blank" class="feedback-yes" title='<@vrtx.msg code="feedback.send" default="Send us your feedback" />' href="#">
          <@vrtx.msg code="feedback.link.yes" default="Yes, my questions were answered" />
        </a>
      </li>
      <li>
        <a target="_blank" class="feedback-no dialog" title='<@vrtx.msg code="feedback.send" default="Send us your feedback" />' href="${link?html}">
          <@vrtx.msg code="feedback.link.no" default="No, I didn't find what I was looking for" />
        </a>
      </li>
    </ul>
    <span class="vrtx-feedback-bottom"></span>
  </div>
</#if>
