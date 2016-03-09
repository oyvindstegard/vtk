/*  
 *  Iframe resizing for cross domain (admin)
 *
 *  Not essential functionality. Only works in browsers which support postMessage (>IE7).
 *
 *  - Sends available window v.space to iframe (minimum height) after receives msg about iframe is loaded
 *  - Receives computed height from inner iframes or unchanged command
 *  - Shows loading overlay while rendering/loading
 *  - Animates changed height visible in window
 *
 */
 
(function ($) {

  var isPreviewMode,
      htmlTag,
      appFooterHeight,
      extras = 0,
      surplusReduce,
      previewIframeMinHeight,
      previewLoading,
      animationOff = false,
      surplusAnimationSpeed = 200;
 
  var crossDocComLink = new CrossDocComLink();
  crossDocComLink.setUpReceiveDataHandler(function(cmdParams, source) {
    postback = true;
    var vrtxAdm = vrtxAdmin;
    var previewIframe = $("iframe#previewIframe")[0];
    
    switch(cmdParams[0]) {
      case "preview-loaded":
        crossDocComLink.postCmdToIframe(previewIframe, "admin-min-height|" + previewIframeMinHeight);
        break;
      case "preview-height-update":
        var dataHeight = (cmdParams.length === 2) ? cmdParams[1] : 0;
        var newHeight = Math.min(Math.max(dataHeight, previewIframeMinHeight), 20000); // Keep height between available window pixels and 20000 pixels
        previewIframe.style.height = newHeight + "px";
        break; 
      case "preview-height":
        var dataHeight = (cmdParams.length === 2) ? cmdParams[1] : 0;
        var newHeight = Math.min(Math.max(dataHeight, previewIframeMinHeight), 20000); // Keep height between available window pixels and 20000 pixels
        
        if(!vrtxAdm.isIE8 && !animationOff) {
          var diff = newHeight - previewIframeMinHeight;
          var surplus = (appFooterHeight + 13 + 20) - surplusReduce; // +contentBottomMargin+border+contentBottomPadding+border
          if(surplus <= 0) { // If surplus have been swallowed by minimum height
            previewLoadingComplete(previewIframe, newHeight, previewLoading, vrtxAdm);
          } else {
            var animatedPixels = (diff > surplus) ? (previewIframeMinHeight + surplus) : newHeight;
            previewLoading.animate({height: animatedPixels + "px"}, surplusAnimationSpeed);
            vrtxAdm.cachedContent.animate({height: (animatedPixels + extras) + "px"}, surplusAnimationSpeed, function() {
              previewLoadingComplete(previewIframe, newHeight, previewLoading, vrtxAdm);
            });
          }
        } else {
          previewLoadingComplete(previewIframe, newHeight, previewLoading, vrtxAdm);
        }
        break;
      case "preview-keep-min-height":
        previewLoadingComplete(previewIframe, previewIframeMinHeight, previewLoading, vrtxAdm);
        break;
      default:
    }
  });
  
  var initHashParamsRunned = false;
  function initHashParams() {   
    if(initHashParamsRunned) return;
    
    var isMobilePreview = $.bbq.getState("mobile") === "on",
        isFullscreen = $.bbq.getState("fullscreen") === "on";
 
    if(isMobilePreview) {
      $("#preview-mode a").click();
      var isMobilePreviewLandscape = $.bbq.getState("orientation") === "horizontal";
      if(isMobilePreviewLandscape) {
        $("#preview-mode-mobile-rotate-hv").click();
      } else {
        animationOff = false;
      }
    } else {
      animationOff = false;
    }
    if(isFullscreen) {
      $("#preview-actions-fullscreen-toggle").click();
    }
    
    var link = $("#preview-actions-share");
    if(link.length) {
      updateHashShareLink(link);
      $(window).bind('hashchange', function(e) {
        updateHashShareLink(link);
      });
    }
    
    initHashParamsRunned = true;
  }
  
  function updateHashShareLink(link) {
    if(window.location.hash.length === 1) {
      var hash = "";
      if(window.history && window.history.pushState) { 
        window.history.pushState("", "", window.location.pathname + window.location.search);
      }
    } else {
      var hash = window.location.hash;
    }
  
    var msg = link[0].href;
    var msgLines = decodeURI(msg).split("\n");
      
    var msgUrlLineNr = 2;
    var replacedUrl = encodeURIComponent($.trim(decodeURIComponent(msgLines[msgUrlLineNr]).replace(/#.*$/, "")) + window.location.hash);
    msgLines[msgUrlLineNr] = replacedUrl;

    link[0].href = msgLines.join(encodeURI("\n")); // Beware: encodeURI() around msgLines gives too much encoding in Thunderbird e-mail
  }

  // Remove preview-loading overlay and set height
  function previewLoadingComplete(previewIframe, newHeight, previewLoading, vrtxAdm) {
    previewIframe.style.height = newHeight + "px";
    if(!vrtxAdm.isIE8 && !animationOff) {
      previewLoading.find("#preview-loading-inner").remove();
      previewLoading.fadeOut(surplusAnimationSpeed, function() {
        previewLoadingCompleteAfter(previewLoading);
      });
    } else {
      previewLoadingCompleteAfter(previewLoading);
    }
  }
  
  function previewLoadingCompleteAfter(previewLoading) {
    vrtxAdmin.cachedContent.removeAttr('style');  
    previewLoading.remove();
    initHashParams();
    vrtxAdmin.ariaBusy("#previewIframe", false);
  }

  // Find min-height
  $(document).ready(function() {
    var vrtxAdm = vrtxAdmin;

    vrtxAdm.cacheDOMNodesForReuse();
    
    if(window.location.hash.length > 1) {
      animationOff = true;
    }
  
    isPreviewMode = $("#vrtx-preview").length;
    if(isPreviewMode) {
   
      htmlTag = $("html");
    
      // As we can't check on matchMedia and Modernizr is not included in admin yet - hide if <= IE8
      if(vrtxAdm.isIE8 || vrtxAdm.isMobileWebkitDevice || !hasPreviewIframeCommunication) {
        $("#preview-mode").hide();
      } else {
        vrtxAdm.cachedContent.on("click", "#preview-mode a", function(e) {
          var previewIframe = $("iframe#previewIframe")[0];
          if(!htmlTag.hasClass("mobile")) {
            $.bbq.pushState({"mobile": "on"});
            $("#previewIframeWrapper").css("height", $("#previewIframe").height());
            if(hasPreviewIframeCommunication) crossDocComLink.postCmdToIframe(previewIframe, "update-height-vertical");
          } else {
            $.bbq.removeState("mobile", "orientation");
            $("#previewIframeWrapper").css("height", "auto");
            if(hasPreviewIframeCommunication) crossDocComLink.postCmdToIframe(previewIframe, "restore-height");
            htmlTag.removeClass("horizontal");
            htmlTag.removeClass("animated");
            htmlTag.removeClass("change-bg");
          }
      
          htmlTag.toggleClass("mobile");

          var notLink = $("#preview-mode span");
        
          notLink.parent().removeClass("active-mode");
          $(this).parent().addClass("active-mode");
        
          notLink.replaceWith("<a id='" + notLink.attr("id") + "' href='javascript:void(0);'>" + notLink.text() + "</span");
          $(this).replaceWith("<span id='" + this.id + "'>" + $(this).text() + "</span>");
          e.preventDefault();
          e.stopPropagation();
        });
        vrtxAdm.cachedDoc.on("keyup", function(e) {
          if(!hasPreviewIframeCommunication) return;
          if(htmlTag.hasClass("mobile")) {
            var isHorizontal = $("html").hasClass("horizontal");
            if((e.which === 39 && isHorizontal) || (e.which === 37 && !isHorizontal)) {
              $("#preview-mode-mobile-rotate-hv").click();
            } else if(e.which === 107) {
              var previewIframe = $("iframe#previewIframe")[0];
              crossDocComLink.postCmdToIframe(previewIframe, "zoom-in");
            } else if(e.which === 109) {
              var previewIframe = $("iframe#previewIframe")[0];
              crossDocComLink.postCmdToIframe(previewIframe, "zoom-out");
            } else if(e.which === 106) {
              var previewIframe = $("iframe#previewIframe")[0];
              crossDocComLink.postCmdToIframe(previewIframe, "restore-zoom");
            }
          }
        });

        var waitForTheEnd = null;
        vrtxAdm.cachedContent.on("click", "#preview-mode-mobile-rotate-hv", function(e) {
          if(waitForTheEnd !== null) return;
          
          $("#previewIframeInnerWrapper").stop().fadeTo((!animationOff ? 150 : 0), 0, "easeInCubic", function() {
            /* Make shadow "follow along" rotation */
            if(htmlTag.hasClass("change-bg")) {
              htmlTag.removeClass("change-bg");
            } else {
              waitForTheEnd = setTimeout(function() {
                htmlTag.addClass("change-bg");
                animationOff = false;
                waitForTheEnd = null;
              }, (!animationOff ? 250 : 0));
            }

            /* Update iframe height */
            var previewIframe = $("iframe#previewIframe")[0];
            if(!htmlTag.hasClass("horizontal")) {
              $.bbq.pushState({"orientation": "horizontal"});
              if(hasPreviewIframeCommunication) crossDocComLink.postCmdToIframe(previewIframe, "update-height-horizontal");
            } else {
              $.bbq.removeState("orientation");
              if(hasPreviewIframeCommunication) crossDocComLink.postCmdToIframe(previewIframe, "update-height-vertical");
            }
            if(!animationOff) {
              if(!htmlTag.hasClass("animated")) {
                htmlTag.addClass("animated"); 
              }
            }
            htmlTag.toggleClass("horizontal");
            
          }).delay((!animationOff ? 250 : 0)).fadeTo((!animationOff ? 150 : 0), 1, "easeOutCubic");

          e.preventDefault();
          e.stopPropagation();
        });
      }
      if(hasPreviewIframeCommunication) {
        vrtxAdm.cachedContent.on("click", "#preview-actions-print", function(e) {
          var previewIframe = $("iframe#previewIframe")[0];
          crossDocComLink.postCmdToIframe(previewIframe, "print");
          e.preventDefault();
          e.stopPropagation();
        });
        
        var editorStickyBar = null;
        vrtxAdm.cachedContent.on("click", "#preview-actions-fullscreen-toggle", function(e) {
          htmlTag.toggleClass('fullscreen-toggle-open');
          if(htmlTag.hasClass('fullscreen-toggle-open')) {
            vrtxAdmin.runReadyLoad = false;
            $.bbq.pushState({"fullscreen": "on"});
            $(this).text(fullscreenToggleClose);
            var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
            var futureStickyBar = (typeof VrtxStickyBar === "undefined") ? getScriptFn("/vrtx/__vrtx/static-resources/js/vrtx-sticky-bar.js") : $.Deferred().resolve();
            $.when(futureStickyBar).done(function() {     
              editorStickyBar = new VrtxStickyBar({
                 wrapperId: "#preview-mode-actions",
                 stickyClass: "vrtx-sticky-preview-mode-actions",
                 contentsId: "#contents",
                 outerContentsId: "#main",
                 extraWidth: 2
              });
            });
            $(window).trigger("scroll");
          } else {
            vrtxAdmin.runReadyLoad = true;
            $.bbq.removeState("fullscreen");
            $(this).text(fullscreenToggleOpen);
            if(editorStickyBar !== null) {
              editorStickyBar.destroy();
            }
            $(window).trigger("resize");
          }
          e.preventDefault();
          e.stopPropagation();
        });
      }

      var appContentHeight = vrtxAdm.cachedAppContent.height();
      var appHeadWrapperHeight = vrtxAdm.cachedBody.find("#app-head-wrapper").outerHeight(true);
      appFooterHeight = vrtxAdm.cachedBody.find("#app-footer").outerHeight(true);
      var windowHeight = $(window).outerHeight(true);

      var msg = $(".tabMessage-big");
      if(msg.length) {
        extras = msg.outerHeight(true);
      }
      var absMinHeight = 150;
      var availWinHeight = (windowHeight - (appContentHeight + appHeadWrapperHeight + appFooterHeight)) + absMinHeight; // + iframe height
      if(availWinHeight < absMinHeight) {
        surplusReduce = absMinHeight - availWinHeight;
        previewIframeMinHeight = absMinHeight;
      } else {
        previewIframeMinHeight = availWinHeight;
      }
      
      vrtxAdmin.ariaBusy("#previewIframe", true);
      
      vrtxAdm.cachedContent.append("<span id='preview-loading'><span id='preview-loading-inner'><span>" + previewLoadingMsg + "...</span></span></span>")
                           .css({ position: "relative",
                                    height: (previewIframeMinHeight + extras + 2) + "px" });
      
      previewLoading = vrtxAdm.cachedContent.find("#preview-loading");
      previewLoading.css({
        height: previewIframeMinHeight + "px",
        top: extras + "px"
      });
      
      previewLoading.find("#preview-loading-inner").css({
        height: availWinHeight + "px"
      });
      
      if(!hasPreviewIframeCommunication) {
        previewLoadingComplete($("iframe#previewIframe")[0], previewIframeMinHeight, previewLoading, vrtxAdm);
      }
    }
  });

}(jQuery));

/*
 * jQuery BBQ: Back Button & Query Library - v1.2.1 - 2/17/2010
 * http://benalman.com/projects/jquery-bbq-plugin/
 * 
 * Copyright (c) 2010 "Cowboy" Ben Alman
 * Dual licensed under the MIT and GPL licenses.
 * http://benalman.com/about/license/
 */
(function($,p){var i,m=Array.prototype.slice,r=decodeURIComponent,a=$.param,c,l,v,b=$.bbq=$.bbq||{},q,u,j,e=$.event.special,d="hashchange",A="querystring",D="fragment",y="elemUrlAttr",g="location",k="href",t="src",x=/^.*\?|#.*$/g,w=/^.*\#/,h,C={};function E(F){return typeof F==="string"}function B(G){var F=m.call(arguments,1);return function(){return G.apply(this,F.concat(m.call(arguments)))}}function n(F){return F.replace(/^[^#]*#?(.*)$/,"$1")}function o(F){return F.replace(/(?:^[^?#]*\?([^#]*).*$)?.*/,"$1")}function f(H,M,F,I,G){var O,L,K,N,J;if(I!==i){K=F.match(H?/^([^#]*)\#?(.*)$/:/^([^#?]*)\??([^#]*)(#?.*)/);J=K[3]||"";if(G===2&&E(I)){L=I.replace(H?w:x,"")}else{N=l(K[2]);I=E(I)?l[H?D:A](I):I;L=G===2?I:G===1?$.extend({},I,N):$.extend({},N,I);L=a(L);if(H){L=L.replace(h,r)}}O=K[1]+(H?"#":L||!K[1]?"?":"")+L+J}else{O=M(F!==i?F:p[g][k])}return O}a[A]=B(f,0,o);a[D]=c=B(f,1,n);c.noEscape=function(G){G=G||"";var F=$.map(G.split(""),encodeURIComponent);h=new RegExp(F.join("|"),"g")};c.noEscape(",/");$.deparam=l=function(I,F){var H={},G={"true":!0,"false":!1,"null":null};$.each(I.replace(/\+/g," ").split("&"),function(L,Q){var K=Q.split("="),P=r(K[0]),J,O=H,M=0,R=P.split("]["),N=R.length-1;if(/\[/.test(R[0])&&/\]$/.test(R[N])){R[N]=R[N].replace(/\]$/,"");R=R.shift().split("[").concat(R);N=R.length-1}else{N=0}if(K.length===2){J=r(K[1]);if(F){J=J&&!isNaN(J)?+J:J==="undefined"?i:G[J]!==i?G[J]:J}if(N){for(;M<=N;M++){P=R[M]===""?O.length:R[M];O=O[P]=M<N?O[P]||(R[M+1]&&isNaN(R[M+1])?{}:[]):J}}else{if($.isArray(H[P])){H[P].push(J)}else{if(H[P]!==i){H[P]=[H[P],J]}else{H[P]=J}}}}else{if(P){H[P]=F?i:""}}});return H};function z(H,F,G){if(F===i||typeof F==="boolean"){G=F;F=a[H?D:A]()}else{F=E(F)?F.replace(H?w:x,""):F}return l(F,G)}l[A]=B(z,0);l[D]=v=B(z,1);$[y]||($[y]=function(F){return $.extend(C,F)})({a:k,base:k,iframe:t,img:t,input:t,form:"action",link:k,script:t});j=$[y];function s(I,G,H,F){if(!E(H)&&typeof H!=="object"){F=H;H=G;G=i}return this.each(function(){var L=$(this),J=G||j()[(this.nodeName||"").toLowerCase()]||"",K=J&&L.attr(J)||"";L.attr(J,a[I](K,H,F))})}$.fn[A]=B(s,A);$.fn[D]=B(s,D);b.pushState=q=function(I,F){if(E(I)&&/^#/.test(I)&&F===i){F=2}var H=I!==i,G=c(p[g][k],H?I:{},H?F:2);p[g][k]=G+(/#/.test(G)?"":"#")};b.getState=u=function(F,G){return F===i||typeof F==="boolean"?v(F):v(G)[F]};b.removeState=function(F){var G={};if(F!==i){G=u();$.each($.isArray(F)?F:arguments,function(I,H){delete G[H]})}q(G,2)};e[d]=$.extend(e[d],{add:function(F){var H;function G(J){var I=J[D]=c();J.getState=function(K,L){return K===i||typeof K==="boolean"?l(I,K):l(I,L)[K]};H.apply(this,arguments)}if($.isFunction(F)){H=F;return G}else{H=F.handler;F.handler=G}}})})(jQuery,this);
/*
 * jQuery hashchange event - v1.2 - 2/11/2010
 * http://benalman.com/projects/jquery-hashchange-plugin/
 * 
 * Copyright (c) 2010 "Cowboy" Ben Alman
 * Dual licensed under the MIT and GPL licenses.
 * http://benalman.com/about/license/
 */
(function($,i,b){var j,k=$.event.special,c="location",d="hashchange",l="href",f=$.browser,g=document.documentMode,h=f.msie&&(g===b||g<8),e="on"+d in i&&!h;function a(m){m=m||i[c][l];return m.replace(/^[^#]*#?(.*)$/,"$1")}$[d+"Delay"]=100;k[d]=$.extend(k[d],{setup:function(){if(e){return false}$(j.start)},teardown:function(){if(e){return false}$(j.stop)}});j=(function(){var m={},r,n,o,q;function p(){o=q=function(s){return s};if(h){n=$('<iframe src="javascript:0"/>').hide().insertAfter("body")[0].contentWindow;q=function(){return a(n.document[c][l])};o=function(u,s){if(u!==s){var t=n.document;t.open().close();t[c].hash="#"+u}};o(a())}}m.start=function(){if(r){return}var t=a();o||p();(function s(){var v=a(),u=q(t);if(v!==t){o(t=v,u);$(i).trigger(d)}else{if(u!==t){i[c][l]=i[c][l].replace(/#.*/,"")+"#"+u}}r=setTimeout(s,$[d+"Delay"])})()};m.stop=function(){if(!n){r&&clearTimeout(r);r=0}};return m})()})(jQuery,this);