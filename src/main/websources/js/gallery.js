/*
 * Vortex Simple Gallery jQuery plugin
 * w/ paging, centered thumbnail navigation, fullscreen mode,
 * [n-1, n+1] full image prefetching and crossfade effect (dimensions from server)
 *
 * Copyright (C) 2010- Øyvind Hatland - University Of Oslo / USIT
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

(function ($) {
  $.fn.vrtxSGallery = function (wrapper, container, unique, options) {
    settings = jQuery.extend({ // Default animation settings
      fadeInOutTime: 250, fadedOutOpacity: 0,
      fadeThumbsInOutTime: 250, fadedThumbsOutOpacity: 0.6,
      fadeNavInOutTime: 250, fadedInActiveNavOpacity: 0.5,
      fadedNavOpacity: 0.2, loadNextPrevImagesInterval: 20
    }, options || {});

    var wrp = $(wrapper);
    
    // Unobtrusive
    wrp.find(container + "-pure-css").addClass(container.substring(1));
    wrp.find(container + "-nav-pure-css").addClass(container.substring(1) + "-nav");
    wrp.find(container.replace("-container", "") + "-thumbs-pure-css").addClass(container.substring(1).replace("-container", "") + "-thumbs");
    
    // Cache containers and image HTML with src as hash
    var wrpContainer = wrp.find(container + "-pure-css"),
        wrpThumbs = wrp.find("ul"),
        wrpThumbsLinks = wrpThumbs.find("li a"),
        wrpNav = wrp.find(container + "-nav"),
        wrpNavNextPrev = wrpNav.find("a"),
        wrpNavNext = wrpNavNextPrev.filter(".next"),
        wrpNavPrev = wrpNavNextPrev.filter(".prev"),
        wrpNavNextPrevSpans = wrpNavNextPrev.find("span"),
        images = {}, imageUrlsToBePrefetchedLen = imageUrlsToBePrefetched.length - 1,
        isFullscreen = false, isResponsive = false,
        widthProp = "width", heightProp = "height",
        maxRegularWidth = 507, maxRegularHeight = 380,
        // TODO: account for description variable padding/width also (instead of -30), but could be none descriptions added at init
        wrpDescriptionBorderPaddingWidth = 30,
        wrpContainerBorderPaddingWidth = parseInt(wrpContainer.css("paddingLeft"), 10) + parseInt(wrpContainer.css("paddingRight"), 10) +
                                         parseInt(wrpContainer.css("borderLeftWidth"), 10) + parseInt(wrpContainer.css("borderRightWidth"), 10);
        
    var maxHeight = 4/3;
    if(settings.maxHeight === "none") {
      maxHeight = 99999;
    } else if(settings.maxHeight === "4-3") {
      maxHeight = 4/3; // 1.33:1
    } else if(settings.maxHeight === "16-9") {
      maxHeight = 16/9; // 1.77:1
    }
    
    if(isNaN(wrpContainerBorderPaddingWidth)) {
      wrpContainerBorderPaddingWidth = 0;
    }
        
    
    // Init first active image
    var firstImage = wrpThumbsLinks.filter(".active");
    if(!firstImage.length) return this; 
    showImage(firstImage.find("img.vrtx-thumbnail-image"), true);
    wrpNavNextPrev.fadeTo(0, 0);
    wrpNavNextPrevSpans.fadeTo(0, 0);
    
    $("html").addClass("fullscreen-gallery-big-arrows");
    
    // Thumbs interaction
    wrp.on("mouseover mouseout click", "li a", function (e) {
      var elm = $(this);
      if (e.type === "mouseover" || e.type === "mouseout") {
        elm.filter(":not(.active)").find("img").stop().fadeTo(settings.fadeThumbsInOutTime, (e.type === "mouseover") ? 1 : settings.fadedThumbsOutOpacity);
      } else {
        navigate(elm);
        e.stopPropagation();
        e.preventDefault();
      }
    });

    // Navigation interaction
    $(document).keydown(function (e) {
      if (e.keyCode === 39) {
        nextPrevNavigate(e, 1);
      } else if (e.keyCode === 37) {
        nextPrevNavigate(e, -1);
      }
    });
    wrp.on("click mouseover mouseout", "a.next, " + container + "-link", function (e) {
      nextPrevNavigate(e, 1);
    });
    wrp.on("click mouseover mouseout", "a.prev", function (e) {
      nextPrevNavigate(e, -1);
    });
    
    // Fullscreen toggle interaction
    wrp.on("click", "a.toggle-fullscreen", function (e) {
      var htmlTag = $("html");
      htmlTag.toggleClass("fullscreen-gallery");
      isFullscreen = htmlTag.hasClass("fullscreen-gallery");
      wrp.parents().toggleClass("fullwidth");
      if(!isFullscreen) {
        widthProp = "width";
        heightProp = "height";
      } else {
        widthProp = "fullWidth";
        heightProp = "fullHeight";
        if(!wrp.find("> .fullscreen-gallery-topline").length) {
          var link = $(this);
          var extraHtml = typeof vrtxSGalleryFullscreenAddExtraHtml === "function" ? vrtxSGalleryFullscreenAddExtraHtml() : "";
          wrp.prepend("<div class='fullscreen-gallery-topline'>" + extraHtml + "<a href='javascript:void(0);' class='toggle-fullscreen'>" + settings.i18n.closeFullscreen + "</a></div>");
        }
        toggleFullscreenResponsive(htmlTag);
        window.scrollTo(0, 0);
      }
      resizeFullscreen(true);
      e.stopPropagation();
      e.preventDefault();
    });
    
    // Fullscreen resize
    var maxRuns = 0;
    $(window).resize($.throttle(250, function () {
      // IE and iPad only run resize event 2 times
      if (maxRuns < 2 || (!$.browser.msie && !/iPad/.test(window.navigator.userAgent))) {
        if(isFullscreen || isResponsive) {
          resizeFullscreen(true);
        }
        maxRuns++;
      } else {
        maxRuns = 0; /* IE8: Let it rest */
      }
    }));
    $(window).on("orientationchange", function (e) {
      resizeFullscreen(true);
    });
    $.vrtxSGalleryToggleResponsive = function(responsive) {
      isResponsive = responsive;
      
      if(!isFullscreen && !isResponsive) {
        resizeFullscreen(true);
      }
      var htmlTag = $("html");
      if(isResponsive) {
        wrp.find(".toggle-fullscreen.minimized").text(settings.i18n.showFullscreenResponsive);
        htmlTag.removeClass("fullscreen-gallery-big-arrows");
      } else {
        wrp.find(".toggle-fullscreen.minimized").text(settings.i18n.showFullscreen);
        htmlTag.addClass("fullscreen-gallery-big-arrows");
      }
      toggleFullscreenResponsive(htmlTag);
    };
    
    var imgs = this;
    processLinkImages(imgs);

    // Prefetch current, next and prev full images in the background
    prefetchCurrentNextPrevNthImages(1);
    
    wrp.removeClass("loading");
  
    return imgs; /* Make chainable */
    
    
    function navigate(elm) {
      var img = elm.find("img.vrtx-thumbnail-image");
      showImage(img, false);
      elm.addClass("active");
      prefetchCurrentNextPrevNthImages(1);
      img.stop().fadeTo(0, 1);
    }
    
    function nextPrevNavigate(e, dir) {
      var isNext = dir > 0;	
      if (e.type === "mouseover") {
        wrpNavNext.stop().fadeTo(settings.fadeNavInOutTime, isNext ? 1 : settings.fadedInActiveNavOpacity);
        wrpNavPrev.stop().fadeTo(settings.fadeNavInOutTime, isNext ? settings.fadedInActiveNavOpacity : 1);
        wrpNavNextPrevSpans.stop().fadeTo(settings.fadeNavInOutTime, settings.fadedNavOpacity);   /* XXX: some filtering instead below */
      } else if (e.type === "mouseout") {
        wrpNavNextPrevSpans.stop().fadeTo(settings.fadeNavInOutTime, 0);
        wrpNavNextPrev.stop().fadeTo(settings.fadeNavInOutTime, 0); 
      } else {
        var activeThumb = wrpThumbsLinks.filter(".active").parent();
        var elm = isNext ? activeThumb.next("li") : activeThumb.prev("li");
        if (elm.length) {
          navigate(elm.find("a"));
        } else {
          navigate(wrp.find("li").filter(isNext ? ":first" : ":last").find("a"));
        }
        if(!e.keyCode) {
          e.stopPropagation();
          e.preventDefault();
        }
      }
    }
    
    function loadImage(src) {      
      if(src.indexOf("//") === 0) {
        src = window.location.protocol + src;
      }
      var id = genId(src);
      if(wrp.find("a#" + id).length) return;
      var description = "<div id='" + id + "-description' class='" + container.substring(1) + "-description" + (!images[src].desc ? " empty-description" : "") + "' style='display: none; width: " + Math.max(0, ((images[src].width - wrpDescriptionBorderPaddingWidth) + wrpContainerBorderPaddingWidth)) + "px'>" + 
                        images[src].desc + "<div class='toggle-fullscreen-container'><a href='javascript:void(0);' class='toggle-fullscreen minimized'>" + (isResponsive ? settings.i18n.showFullscreenResponsive : settings.i18n.showFullscreen) + "</a></div></div>";
      $($.parseHTML(description)).insertBefore(wrpThumbs);
      wrpContainer.append("<a id='" + id + "' style='display: none' href='" + src + "' class='" + container.substring(1) + "-link'>" +
                            "<img src='" + src + "' alt='" + images[src].alt + "' style='width: " + images[src][widthProp] + "px; height: " + images[src][heightProp] + "px;' />" +
                          "</a>");
    }
    
    function prefetchCurrentNextPrevNthImages(n) {
      var active = wrpThumbsLinks.filter(".active"),
          activeIdx = active.parent().index() - 1,
          activeSrc = active.find("img.vrtx-thumbnail-image")[0].src.split("?")[0],
          alternate = false,
          i = 1;
      loadImage(activeSrc);
      var loadNextPrevImages = setTimeout(function() {   
        if(alternate) {
          var activeIdxMinus = activeIdx - i;
          if(activeIdxMinus < 0) {
            activeIdxMinus = imageUrlsToBePrefetchedLen - (~activeIdxMinus); // ~n = (-|+)n-1
          }
          loadImage(imageUrlsToBePrefetched[activeIdxMinus].url);
          i++;
        } else {
          var activeIdxPlus = activeIdx + i;
          if(activeIdxPlus > imageUrlsToBePrefetchedLen) {
            activeIdxPlus = ~(imageUrlsToBePrefetchedLen - activeIdxPlus);
          }
          loadImage(imageUrlsToBePrefetched[activeIdxPlus].url);
        }
        alternate = !alternate;
        if(i <= n) {
          setTimeout(arguments.callee, settings.loadNextPrevImagesInterval);
        }
      }, settings.loadNextPrevImagesInterval);
    }

    function showImageCrossFade(current, active, activeSrc, activeDesc) {
      current.wrap("<div class='over' />").fadeTo(settings.fadeInOutTime, settings.fadedOutOpacity, function () {
        $(this).unwrap().removeClass("active-full-image").hide();
      });
      resizeContainers(activeSrc, active, activeDesc);
      active.addClass("active-full-image").fadeTo(0, 0).fadeTo(settings.fadeInOutTime, 1);
    }
    
    function showImageToggle(current, active) {
      current.removeClass("active-full-image");
      active.addClass("active-full-image");
    }
    
    function showImageDescStrategy(current, active, activeSrc, currentDesc, activeDesc, init) {
      currentDesc.removeClass("active-description");
      activeDesc.addClass("active-description");
      toggleFullscreenResponsiveShowHideLink(activeDesc);
      if(init) {
        active.addClass("active-full-image");
        resizeContainers(activeSrc, active, activeDesc);
      } else if(settings.fadeInOutTime > 0 ) {
        showImageCrossFade(current, active, activeSrc, activeDesc);
      } else {
        showImageToggle(current, active);
        resizeContainers(activeSrc, active, activeDesc);
      }
    }
    
    function showImage(image, init) {
      var activeSrc = image[0].src.split("?")[0];

      if (init) {
        cacheGenerateLinkImage(activeSrc);
      }
      var activeId = genId(activeSrc);
      
      var active = wrp.find("a#" + activeId);
      var activeDesc = wrp.find("#" + activeId + "-description");
      var current = wrp.find("a" + container + "-link.active-full-image");
      var currentDesc = wrp.find(container + "-description.active-description");
      if(active.length) {
        showImageDescStrategy(current, active, activeSrc, currentDesc, activeDesc, init);
      } else {
        var waitForActive = setTimeout(function() {
          active = wrp.find("a#" + activeId);
          activeDesc = wrp.find("#" + activeId + "-description");
          if(!active.length && !activeDesc.length) { // Are we (image and description) ready?
            setTimeout(arguments.callee, 5);
          } else {
            showImageDescStrategy(current, active, activeSrc, currentDesc, activeDesc, init);
          }
        }, 5);
      }
      if(!init) {
        wrpThumbsLinks.filter(".active").removeClass("active").find("img").stop().fadeTo(settings.fadeThumbsInOutTime, settings.fadedThumbsOutOpacity);
      } else {
        wrpThumbsLinks.filter(":not(.active)").find("img").stop().fadeTo(0, settings.fadedThumbsOutOpacity);
      }
    }
    
    function processLinkImages(imgs) {
      var centerThumbnailImageFunc = centerThumbnailImage, 
          cacheGenerateLinkImageFunc = cacheGenerateLinkImage,
          link, image;
      for(var i = 0, len = imgs.length; i < len; i++) {
        link = $(imgs[i]);
        image = link.find("img.vrtx-thumbnail-image");
        centerThumbnailImageFunc(image, link);
        cacheGenerateLinkImageFunc(image[0].src.split("?")[0]);
      }
    }

    function cacheGenerateLinkImage(src) {
      images[src] = {};
      // Find image width and height "precalculated" from Vortex (properties)
      var protocolRelativeSrc = "//" + src.split("//")[1];
      for(var i = 0, len = imageUrlsToBePrefetched.length; i < len; i++) {
        var dims = imageUrlsToBePrefetched[i];
        if(dims.url === protocolRelativeSrc) break;
      }
      maxRegularWidth = wrpThumbs.is(":visible") ? Math.min(wrpThumbs.width(), wrp.parent().width()) : wrp.parent().width();
      maxRegularWidth -= wrpContainerBorderPaddingWidth;
      maxRegularHeight = maxHeight === 99999 ? maxHeight : Math.round(maxRegularWidth/(maxHeight));
      images[src].fullWidthOrig = parseInt(dims.fullWidth.replace(/[^\d]*/g, ""), 10);
      images[src].fullHeightOrig = parseInt(dims.fullHeight.replace(/[^\d]*/g, ""), 10);
      var regularDims = calculateImageDimensions(images[src].fullWidthOrig, images[src].fullHeightOrig, maxRegularWidth, maxRegularHeight);
      images[src].width = regularDims[0];
      images[src].height = regularDims[1];

      // HTML unescape and encode quotes in alt and title if not already encoded
      var alt = dims.alt;
      var desc = dims.desc;
      var title = dims.title;
      images[src].alt = alt !== "" ? $("<div/>").html(alt).text().replace(/\'/g, "&#39;") : null;
      images[src].desc = desc !== "" ? $("<div/>").html(desc).text().replace(/\'/g, "&#39;") : null;
      images[src].title = title !== "" ? $("<div/>").html(title).text().replace(/\'/g, "&#39;") : null;
      
      // Add description
      desc = "";
      if (images[src].title) desc += "<p class='" + container.substring(1) + "-title'>" + images[src].title + "</p>";
      if (images[src].desc)  desc += images[src].desc;
      images[src].desc = desc;
    }

    function centerThumbnailImage(thumb, link) {
      centerDimension(thumb, thumb.width(), link.width(), "marginLeft"); // Horizontal dimension
      centerDimension(thumb, thumb.height(), link.height(), "marginTop"); // Vertical dimension
    }

    function centerDimension(thumb, tDim, tCDim, cssProperty) { // Center thumbDimension in thumbContainerDimension
      thumb.css(cssProperty, ((tDim > tCDim) ? ((tDim - tCDim) / 2) * -1 : (tDim < tCDim) ? (tCDim - tDim) / 2 : 0) + "px");
    }
    
    var runnedOnce = false;
    function toggleFullscreenResponsive(htmlTag) {
      if(!isFullscreen) return;
      if(isResponsive) {
        if(!runnedOnce) {
          wrp.find("> .fullscreen-gallery-topline").prepend("<a style='display: none' href='javascript:void(0);' class='fullscreen-gallery-responsive-toggle-description'>" + settings.i18n.showImageDescription + "</a>");
          wrp.on("click", "a.fullscreen-gallery-responsive-toggle-description", function(e) {
            var link = $(this);
            if(link.text() == settings.i18n.showImageDescription) { // "true" = true
              link.text(settings.i18n.hideImageDescription);
              wrp.removeClass("hidden-descriptions");
            } else {
              link.text(settings.i18n.showImageDescription);
              wrp.addClass("hidden-descriptions");
            }
            e.stopPropagation();
            e.preventDefault();
          });
          wrp.addClass("hidden-descriptions");
          runnedOnce = true;
        }
        toggleFullscreenResponsiveShowHideLink(wrp.find(container + "-description.active-description"));
      }
    }
    
    function toggleFullscreenResponsiveShowHideLink(activeDesc) {
      if(isResponsive && isFullscreen) {
        var hasDescription = !activeDesc.hasClass("empty-description");
        if(hasDescription && !wrp.hasClass("has-description")) {
          wrp.addClass("has-description");
        } else if(!hasDescription) {
          wrp.removeClass("has-description");
        }
      }
    }
    
    function resizeContainers(activeSrc, active, activeDesc) {
      var width = Math.max(images[activeSrc][widthProp], (isFullscreen ? 500 : 250)); // 250x188px and 500x375px (fullscreen) min. containers
      var height = Math.max(images[activeSrc][heightProp], (isFullscreen ? 375 : 188));
      active.css("height", height + "px");
      wrpNavNextPrev.css("height", height + "px");
      wrpNavNextPrevSpans.css("height", height + "px");
      wrpNav.css("width", width + "px");
      wrpContainer.css("width", width + "px");
      activeDesc.css("width", Math.max(0, ((width - wrpDescriptionBorderPaddingWidth) + wrpContainerBorderPaddingWidth))); 
    }

    function resizeToggleFullscreen() {
      // Update all images dimensions
      var loadedImages = wrp.find("a" + container + "-link img");
      for(var i = loadedImages.length; i--;) {
        loadedImages[i].style.width = images[loadedImages[i].src][widthProp] + "px";
        loadedImages[i].style.height = images[loadedImages[i].src][heightProp] + "px";
      }
      // Resize active containers
      var active = wrp.find("a" + container + "-link.active-full-image");
      var activeSrc = active[0].href.split("?")[0];
      var activeDesc = wrp.find(container + "-description.active-description");
      resizeContainers(activeSrc, active, activeDesc);
    }
    
    var curWinWidth = 0, curWinHeight = 0;
    function resizeFullscreen(forceResize) {
      var winWidth = $(window).width();
      var winHeight = $(window).height();
      if(forceResize || (curWinWidth !== winWidth && curWinHeight !== winHeight)) { /* Only occur on init or window resize */
        var toplineHeight = wrp.find(".fullscreen-gallery-topline").outerHeight(true);
        var cacheCalculateImageDimensions = calculateImageDimensions;
        var cacheCalculateFullscreenImageDimensions = calculateFullscreenImageDimensions;
        var descriptionContainers = wrp.find(container + "-description").filter(":not(.empty-description)"); // Don't calculate empty descriptions
        descriptionContainers.addClass("active-description-recalc");
        maxRegularWidth = wrpThumbs.is(":visible") ? Math.min(wrpThumbs.width(), wrp.parent().width()) : wrp.parent().width();
        maxRegularWidth -= wrpContainerBorderPaddingWidth;
        maxRegularHeight = maxHeight === 99999 ? maxHeight : Math.round(maxRegularWidth/(maxHeight));
        for(var key in images) {
          var image = images[key];
          var dimsRegular = cacheCalculateImageDimensions(image.fullWidthOrig, image.fullHeightOrig, maxRegularWidth, maxRegularHeight);   
          image.width = dimsRegular[0];
          image.height = dimsRegular[1];
          var dimsFull = cacheCalculateFullscreenImageDimensions(image.fullWidthOrig, image.fullHeightOrig, genId(key), winWidth, winHeight, toplineHeight);
          image.fullWidth = dimsFull[0];
          image.fullHeight = dimsFull[1];
        }
        curWinWidth = winWidth, curWinHeight = winHeight;
        var timer = setTimeout(function() { // Give time to find new heights for loop above
          descriptionContainers.removeClass("active-description-recalc");
          resizeToggleFullscreen();
        }, 50);
      } else {
        resizeToggleFullscreen();
      }
    }
  
    function calculateFullscreenImageDimensions(w, h, id, winWidth, winHeight, toplineHeight) {
      var desc = wrp.find("#" + id + "-description");
      var descHeight = !desc.hasClass("empty-description") ? desc.outerHeight(true) : 0;
      winHeight = winHeight - (descHeight + toplineHeight) - 20;
      return calculateImageDimensions(w, h, winWidth, winHeight);
    }
    
    function calculateImageDimensions(w, h, maxW, maxH) {
      if(isNaN(w) || isNaN(h)) return [1, 1];
    
      var gcdVal = gcd(w, h);
      var aspectRatio = (w/gcdVal) / (h/gcdVal);
      if(w > maxW || h > maxH) {
        if(h > maxH) {
          var newDim = [Math.round(maxH * aspectRatio), maxH];
          if(newDim[0] > maxW) {
            var newDim = [maxW, Math.round(maxW / aspectRatio)];
          }
        } else {
          var newDim = [maxW, Math.round(maxW / aspectRatio)];
          if(newDim[1] > maxH) {
            var newDim = [Math.round(maxH * aspectRatio), maxH];
          }
        }
        return [newDim[0], newDim[1]];
      } else {
        return [w, h];
      }
    }

    function gcd(a, b) {
      return (b === 0) ? a : gcd (b, a%b);
    }

    function genId(src) {
      return encodeURIComponent(src + "-" + unique).replace(/(%|\.)/gim, "");
    }
  };
})(jQuery);

/*
 * jQuery throttle / debounce - v1.1 - 3/7/2010
 * http://benalman.com/projects/jquery-throttle-debounce-plugin/
 * 
 * Copyright (c) 2010 "Cowboy" Ben Alman
 * Dual licensed under the MIT and GPL licenses.
 * http://benalman.com/about/license/
 */
(function(b,c){var $=b.jQuery||b.Cowboy||(b.Cowboy={}),a;$.throttle=a=function(e,f,j,i){var h,d=0;if(typeof f!=="boolean"){i=j;j=f;f=c}function g(){var o=this,m=+new Date()-d,n=arguments;function l(){d=+new Date();j.apply(o,n)}function k(){h=c}if(i&&!h){l()}h&&clearTimeout(h);if(i===c&&m>e){l()}else{if(f!==true){h=setTimeout(i?k:l,i===c?e-m:e)}}}if($.guid){g.guid=j.guid=j.guid||$.guid++}return g};$.debounce=function(d,e,f){return f===c?a(d,e,false):a(d,f,e!==false)}})(this);

/* ^ Vortex Simple Gallery jQuery plugin */