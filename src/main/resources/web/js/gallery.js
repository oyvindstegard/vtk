/*
 * Vortex Simple Gallery jQuery plugin
 * w/ paging, centered thumbnail navigation and crossfade effect (dimensions from server)
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
  $.fn.vrtxSGallery = function (wrapper, container, maxWidth, options) {
    settings = jQuery.extend({ // Default animation settings
      fadeInOutTime: 250,
      fadedOutOpacity: 0,
      fadeThumbsInOutTime: 250,
      fadedThumbsOutOpacity: 0.6,
      fadeNavInOutTime: 250
    }, options || {});
    
    var wrp = $(wrapper);

    // Unobtrusive
    wrp.find(container + "-pure-css").addClass(container.substring(1));
    wrp.find(container + "-nav-pure-css").addClass(container.substring(1) + "-nav");
    wrp.find(wrapper + "-thumbs-pure-css").addClass(wrapper.substring(1) + "-thumbs");

    var wrapperContainer = wrapper + " " + container;
    var wrapperContainerLink = wrapperContainer + " a" + container + "-link";
    var wrapperThumbsLinks = wrapper + " li a";
    
    // Cache containers and image HTML with src as hash
    var wrpContainer = $(wrapperContainer);
    var wrpContainerLink = $(wrapperContainer + " a" + container + "-link");
    var wrpThumbsLinks = $(wrapperThumbsLinks);
    var images = {}; 

    // Init first active image
    var firstImage = wrpThumbsLinks.filter(".active");
    if(!firstImage.length) return this; 
    
    calculateImage(firstImage.find("img.vrtx-thumbnail-image"), true);
    wrp.find("a.prev, a.prev span, a.next, a.next span").fadeTo(0, 0);

    // Thumbs
    wrp.on("mouseover mouseout click", "li a", function (e) {
      var elm = $(this);
      if(elm.find(".loading-image").length) return false;
      if (e.type == "mouseover" || e.type == "mouseout") {
        elm.filter(":not(.active)").find("img").stop().fadeTo(settings.fadeThumbsInOutTime, (e.type == "mouseover") ? 1 : settings.fadedThumbsOutOpacity);
      } else {
        var img = elm.find("img.vrtx-thumbnail-image");
        calculateImage(img, false);
        elm.addClass("active");
        img.stop().fadeTo(0, 1);
        e.preventDefault();
      }
    });

    // Navigation handlers
    $(document).keydown(function (e) {
      if (e.keyCode == 37) {
        nextPrevNavigate(e, -1);
      } else if (e.keyCode == 39) {
        nextPrevNavigate(e, 1);
      }
    });
    wrp.on("click mouseover mouseout", "a.next, " + container + "-link", function (e) {
      nextPrevNavigate(e, 1);
    });

    wrp.on("click mouseover mouseout", "a.prev", function (e) {
      nextPrevNavigate(e, -1);
    });

    // Rest of images
    var imgs = this,
        centerThumbnailImageFunc = centerThumbnailImage, 
        cacheGenerateLinkImageFunc = cacheGenerateLinkImage, link, image;
    for(var i = 0, len = imgs.length; i < len; i++) {
      link = $(imgs[i]);
      image = link.find("img.vrtx-thumbnail-image");
      if(i > 1) {
        $("<span class='loading-image'>" + loadImageMsg + "...</span>").insertBefore(image);
      }
      cacheGenerateLinkImageFunc(image.attr("src").split("?")[0], image, link); 
      centerThumbnailImageFunc(image, link);
    }
    
    // Load full images in the background
    var startAsyncIdx = 2;
    var j = startAsyncIdx, imagesLaterLen = imagesLater.length, imgLaters = new Array(imagesLaterLen), loadFullImage = function() {
      $(imgs).filter("[href^='" + this.src + "']").closest("a").find(".loading-image").remove();
    }, errorFullImage = function() {
      $(imgs).filter("[href^='" + this.src + "']").closest("a").find(".loading-image").addClass("loading-image-error").append("<p>" + loadImageErrorMsg + "</p>");
    };
    var loadRestOfImages = setTimeout(function() {
      imgLaters[j - startAsyncIdx] = new Image();
      imgLaters[j - startAsyncIdx].onload = loadFullImage;
      imgLaters[j - startAsyncIdx].onerror = errorFullImage;
      imgLaters[j - startAsyncIdx].src = imagesLater[j - startAsyncIdx];
      j++;
      if((j - startAsyncIdx) < imagesLaterLen) {
        setTimeout(arguments.callee);
      }
    }, 20);

  
    return imgs; /* Make chainable */
    
    function nextPrevNavigate(e, dir) {
      var isNext = dir > 0;	
      if (e.type == "mouseover") {
        wrp.find("a.next span, a.prev span").stop().fadeTo(settings.fadeNavInOutTime, 0.2);   /* XXX: some filtering instead below */
        wrp.find("a." + (isNext ? "next" : "prev")).stop().fadeTo(settings.fadeNavInOutTime, 1);
        wrp.find("a." + (isNext ? "prev" : "next")).stop().fadeTo(settings.fadeNavInOutTime, 0.5);
      } else if (e.type == "mouseout") {
        wrp.find("a.prev, a.prev span, a.next, a.next span").stop().fadeTo(settings.fadeNavInOutTime, 0);
      } else {
        var activeThumb = wrpThumbsLinks.filter(".active").parent();
        var elm = isNext ? activeThumb.next() : activeThumb.prev();
        var roundAboutElm = isNext ? "first" : "last";
        if (elm.length) {
          if(elm.find(".loading-image").length) return false;
          elm.find("a").click();
        } else {
          wrp.find("li:" + roundAboutElm + " a").click();
        }
        e.preventDefault();
      }
    }
    
    function calculateImage(image, init) {
      var src = image.attr("src").split("?")[0]; /* Remove parameters when active is sent in to gallery */
      if (settings.fadeInOutTime > 0 && !init) {
        wrpContainer.append("<div class='over'>" + $(wrapperContainerLink).html() + "</div>");
        $(wrapperContainerLink).remove();
        $(".over").fadeTo(settings.fadeInOutTime, settings.fadedOutOpacity, function () {
          $(this).remove();
        });
      } else {
        if (init) {
          cacheGenerateLinkImage(src, image, image.parent());
        } else {
          $(wrapperContainerLink).remove();
        }
      }
      wrpContainer.append(images[src].html);
      scaleAndCalculatePosition(src);

      var thumbs = wrpThumbsLinks;
      for (var thumbsLength = thumbs.length, i = 0; i < thumbsLength; i++) {
        var thumb = $(thumbs[i]);
        if (thumb.hasClass("active")) {
          if (!init) {
            thumb.removeClass("active");
            thumb.find("img").stop().fadeTo(settings.fadeThumbsInOutTime, settings.fadedThumbsOutOpacity);
          }
        } else {
          thumb.find("img").stop().fadeTo(0, settings.fadedThumbsOutOpacity);
        }
      }
    }

    function scaleAndCalculatePosition(src) {
      /* Minimum 150x100px containers */
      var imgWidth =  Math.max(parseInt(images[src].width, 10), 150) + "px";
      var imgHeight = Math.max(parseInt(images[src].height, 10), 100) + "px";

      $(wrapperContainer + "-nav a, " + wrapperContainer + "-nav span, " + wrapperContainerLink).css("height", imgHeight);
      $(wrapperContainer + ", " + wrapperContainer + "-nav").css("width", imgWidth);
      
      var description = $(wrapperContainer + "-description");
      if(!description.length) {
        $("<div class='" + container.substring(1) + "-description' />").insertAfter(wrapperContainer);
        description = $(wrapperContainer + "-description");
      }
      var html = "";
      if (images[src].title) html += "<p class='" + container.substring(1) + "-title'>" + images[src].title + "</p>";
      if (images[src].alt)   html += images[src].alt;
      description.html(html).css("width", imgWidth);
    }

    function centerThumbnailImage(thumb, link) {
      centerDimension(thumb, thumb.width(), link.width(), "marginLeft"); // horizontal
      centerDimension(thumb, thumb.height(), link.height(), "marginTop"); // vertical
    }

    function centerDimension(thumb, thumbDimension, thumbContainerDimension, cssProperty) {
      var adjust = 0;
      if (thumbDimension > thumbContainerDimension) {
        adjust = ((thumbDimension - thumbContainerDimension) / 2) * -1;
      } else if (thumbDimension < thumbContainerDimension) {
        adjust = (thumbContainerDimension - thumbDimension) / 2;
      }
      thumb.css(cssProperty, adjust + "px");
    }

    function cacheGenerateLinkImage(src, image, link) {
      images[src] = {};
      images[src].width = parseInt(link.find("span.hiddenWidth").text(), 10);
      images[src].height = parseInt(link.find("span.hiddenHeight").text(), 10);
      var alt = image.attr("alt");
      var title = image.attr("title");
      // HTML encode quotes in alt and title if not already encoded
      images[src].alt = alt ? alt.replace(/\'/g, "&#39;").replace(/\"/g, "&quot;") : null;
      images[src].title = title ? title.replace(/\'/g, "&#39;").replace(/\"/g, "&quot;") : null;
      // Build HTML
      images[src].html = "<a href='" + link.attr("href") + "'" +
                         " class='" + container.substring(1) + "-link'>" +
                         "<img src='" + src + "' alt='" + images[src].alt + "' style='width: " +
                         images[src].width + "px; height: " + images[src].height + "px;' />" + "</a>";
    }
  };
})(jQuery);

/* ^ Vortex Simple Gallery jQuery plugin */