/*
 * Vortex Simple Gallery jQuery plugin
 * w/ paging, centered thumbnail navigation and crossfade effect (dimensions from server)
 *
 * Copyright (C) 2010 Øyvind Hatland - University Of Oslo / USIT
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
    var images = []; 
    var imagesWidth = {};
    var imagesHeight = {};
    
    // Init first active image
    var firstImage = wrpThumbsLinks.filter(".active");
    calculateImage(firstImage.find("img.vrtx-thumbnail-image"), firstImage.find("img.vrtx-full-image"), true);
    
    wrp.find("a.prev, a.prev span, a.next, a.next span").fadeTo(0, 0);
    
    // Event-handlers
    $(document).keydown(function (e) {
      if (e.keyCode == 37) {
        wrp.find("a.prev").click();
      } else if (e.keyCode == 39) {
        wrp.find("a.next").click();
      }
    });

    wrp.on("mouseover mouseout click", "li a", function (e) {
      var elm = $(this);      
      if (e.type == "mouseover") {
        elm.filter(":not(.active)").find("img").stop().fadeTo(settings.fadeThumbsInOutTime, 1);
      } else if (e.type == "mouseout") {
        elm.filter(":not(.active)").find("img").stop().fadeTo(settings.fadeThumbsInOutTime, settings.fadedThumbsOutOpacity);
      } else {
        var img = elm.find("img.vrtx-thumbnail-image");
        var fullImage = elm.find("img.vrtx-full-image");
        calculateImage(img, fullImage, false);
        elm.addClass("active");
        img.stop().fadeTo(0, 1);
        e.preventDefault();
      }
    });

    wrp.on("click mouseover mouseout", "a.next, " + container + "-link", function (e) {
      nextPrevNavigate(e, 1);
    });

    wrp.on("click mouseover mouseout", "a.prev", function (e) {
      nextPrevNavigate(e, -1);
    });

    var imgs = this, centerThumbnailImageFunc = centerThumbnailImage, generateLinkImageFunc = generateLinkImage;
    for(var i = 0, len = imgs.length; i < len; i++) {
      var link = $(imgs[i]);
      var img = link.find("img.vrtx-full-image");
      var src = img.attr("src");
      imagesWidth[src] = parseInt(link.find("span.hiddenWidth").text(), 10);
      imagesHeight[src] = parseInt(link.find("span.hiddenHeight").text(), 10);
      images[src] = generateLinkImageFunc(img, link, false);
      
      centerThumbnailImageFunc(link.find("img.vrtx-thumbnail-image"), link);
    }
    return imgs; /* Make chainable */
    
    function nextPrevNavigate(e, dir) {
      var isNext = dir > 0;	
      if (e.type == "mouseover") {
        wrp.find("a.next span, a.prev span").stop().fadeTo(settings.fadeNavInOutTime, 0.2);   /* XXX: some filtering instead below */
        wrp.find("a." + (isNext ? "next" : "prev")).stop().fadeTo(settings.fadeNavInOutTime, 1);
        wrp.find("a." + (isNext ? "prev" : "next")).stop().fadeTo(settings.fadeNavInOutTime, 0.5);
      } else if (e.type == "mouseout") {
        wrp.find("a.next span, a.prev span").stop().fadeTo(settings.fadeNavInOutTime, 0);
      } else {
        var activeThumb = wrpThumbsLinks.filter(".active").parent();
        var elm = isNext ? activeThumb.next() : activeThumb.prev();
        var roundAboutElm = isNext ? "first" : "last";
        if (elm.length) {
          elm.find("a").click();
        } else {
          wrp.find("li:" + roundAboutElm + " a").click();
        }
        e.preventDefault();
      }
    }
    
    function calculateImage(image, fullImage, init) {
      if (settings.fadeInOutTime > 0 && !init) {
        wrpContainer.append("<div class='over'>" + $(wrapperContainerLink).html() + "</div>");
        $(wrapperContainerLink).remove();
        wrpContainer.append(images[fullImage.attr("src")]);
        $(".over").fadeTo(settings.fadeInOutTime, settings.fadedOutOpacity, function () {
          $(this).remove();
        });
      } else {
        if (init) {
          wrpContainer.append(generateLinkImage(fullImage, image.parent(), true));
        } else {
          $(wrapperContainerLink).remove();
          wrpContainer.append(images[fullImage.attr("src")]);
        }
      }
      scaleAndCalculatePosition(fullImage);

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

    function scaleAndCalculatePosition(image) {
      var src = image.attr("src").split("?")[0];
      var imgWidth =  Math.max(parseInt( imagesWidth[src], 10), 150) + "px";
      var imgHeight = Math.max(parseInt(imagesHeight[src], 10), 100) + "px";

      $(wrapperContainer + "-nav a, " + wrapperContainer + "-nav span, " + wrapperContainerLink).css("height", imgHeight);
      $(wrapperContainer + ", " + wrapperContainer + "-nav").css("width", imgWidth);
      $(wrapperContainer + "-description").remove();
      
      var title = image.attr("title");
      var alt = image.attr("alt");
      var hasTitle = title && title.length;
      var hasAlt = alt && alt.length;

      var html = "<div class='" + container.substring(1) + "-description'>";
      if (hasTitle) {
        html += "<p class='" + container.substring(1) + "-title'>" + title + "</p>";
      }
      if (hasAlt) {
        html += alt;
      }
      html += "</div>";
      $(html).insertAfter(wrapperContainer);

      if (hasTitle || hasAlt) {
        $(wrapperContainer + "-description").css("width", imgWidth);
      }
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

    function generateLinkImage(image, link, init) {
      var src = image.attr("src").split("?")[0];
      var alt = image.attr("alt");
      var width = 0;
      var height = 0;
      if (!init) {
        width = imagesWidth[src];
        height = imagesHeight[src];
      } else {
        width = parseInt(link.find("span.hiddenWidth").text(), 10);
        height = parseInt(link.find("span.hiddenHeight").text(), 10);
        imagesWidth[src] = width;
        imagesHeight[src] = height;
      }
      return "<a href='" + link.attr("href") + "'" +
             " class='" + container.substring(1) + "-link'>" +
             "<img src='" + src + "' alt='" + alt + "' style='width: " +
             width + "px; height: " + height + "px;' />" + "</a>";
    }
  };
})(jQuery);

/* ^ Vortex Simple Gallery jQuery plugin */