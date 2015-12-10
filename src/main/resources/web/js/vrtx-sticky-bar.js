/*
 *  VrtxStickyBar (by USIT/GPL|GUAN)
 *  
 *  * Requires Dejavu OOP library
 */

/* Public
 * ----------------------
 * initialize(opts)
 * destroy()
 */
 
var VrtxStickyBar = dejavu.Class.declare({
  $name: "VrtxStickyBar",
  __opts: {},
  initialize: function(opts) {
    this.__opts = opts;
    var wrapperId = opts.wrapperId;
    var stickyClass = opts.stickyClass;
    var contents = $(opts.contentsId);
    var main = opts.outerContentsId ? $(opts.outerContentsId) : contents;
    var extraWidth = opts.extraWidth || 0;
    var stickFn = opts.stick || null;
    var unstickFn = opts.unstick || null;
      
    var wrapper = $(wrapperId);
    var thisWindow = $(window);
    var ua = window.navigator.userAgent.toLowerCase();
    if (wrapper.length && !/iphone/.test(ua)) { // Turn off for iPhone. 
      if (/msie 8/.test(ua)) { // Shadow below in IE8
        wrapper[opts.isBottomSticky ? "prepend" : "append"]("<span class='sticky-bg-ie8-below' />");
        wrapper.addClass("ie8");
      }
      var wrapperPos = wrapper.offset();
      if(opts.isBottomSticky) {
        wrapper.addClass("sticky-bottom");
      }
      var shouldStick = function() {
        if(opts.isBottomSticky) {
          return (thisWindow.scrollTop() + thisWindow.height()) <= (wrapperPos.top - 1 + wrapper.height());
        } else {
          return thisWindow.scrollTop() >= wrapperPos.top + 1;
        }
      };
      
      if(opts.alwaysFixed) {
        contents.css("paddingTop", wrapper.outerHeight(true) + "px");
      }
      
      // Scroll and resize
      this.__opts.scroll = function() {
        if (shouldStick()) {
          if (!wrapper.hasClass(stickyClass)) {
            wrapper.addClass(stickyClass);
            if(!opts.alwaysFixed) contents.css("paddingTop", wrapper.outerHeight(true) + "px");
            if(typeof stickFn === "function") {
              stickFn();
            }
          }
          wrapper.css("width", (main.outerWidth(true) - 2 + extraWidth) + "px");
        } else {
          if (wrapper.hasClass(stickyClass)) {
            wrapper.removeClass(stickyClass);
            wrapper.css("width", "auto");
            if(!opts.alwaysFixed) contents.css("paddingTop", "0px");
            if(typeof unstickFn === "function") {
              unstickFn();
            }
          }
        }
      };
      
      this.__opts.resize = function() {
        if (shouldStick()) {
          wrapper.css("width", (main.outerWidth(true) - 2 + extraWidth) + "px");
        }
      };
      
      thisWindow.on("scroll", this.__opts.scroll);
      thisWindow.on("resize", this.__opts.resize);
    }
  },
  destroy: function () {
    var thisWindow = $(window);
    thisWindow.off("scroll", this.__opts.scroll);
    thisWindow.off("resize", this.__opts.resize);
    var wrapper = $(this.__opts.wrapperId);
    if (wrapper.hasClass(this.__opts.stickyClass)) {
      wrapper.removeClass(this.__opts.stickyClass);
      if (wrapper.hasClass("sticky-bottom")) {
        wrapper.removeClass("sticky-bottom");
      }
      wrapper.css("width", "auto");
      $(this.__opts.contentsId).css("paddingTop", "0px");
    }
  }        
});