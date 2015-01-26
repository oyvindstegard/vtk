/*
 *  VrtxAnimation (by USIT/GPL|GUAN)
 *
 *  Uses CSS in animations if "useCSSAnim" is explicitly set to "true"
 *  (we like fine-grained control over it with JS and after-functions)
 *
 *  * Requires Dejavu OOP library
 *
 *
 *  TODO: Transfer minus-right/left-margin from element to wrapper until animation ends,
 *        and handle case where element initially have margin-top
 *  TODO: PE support CSS tranform2d and transform3d (GPU-accel.)
 *
 *  About performance (see also Paul Irish comments):
 *  http://greensock.com/css-performance
 *
 *  It seems that CSS vs. JS is comparable in speed and each have its pros and cons
 */
 
/* Public
 * ----------------------
 * initialize(opts)
 * rightIn()        - Shows content and animates marginLeft in CSS/JS
 * leftOut()        - Hides content and animates marginLeft in CSS/JS
 * topDown()        - Shows content and animates marginTop in CSS; uses jQuery slideDown in JS
 * bottomUp()       - Hides content and animates marginTop in CSS; uses jQuery slideUp in JS
 * update(opts)     - Update opts
 * updateElem(elem) - Update elem that is being animated
 *
 * Private
 * ----------------------
 * __prepareMove()    - Prepare for animation
 * __afterMove()      - Runs after animation is complete
 * __horizontalMove() - Animate horizontally
 * __verticalMove()   - Animate vertically
 */
 
var VrtxAnimation = dejavu.Class.declare({
  $name: "VrtxAnimation",
  $constants: {
    animationSpeed: /(iphone|ipad|android)/.test(window.navigator.userAgent.toLowerCase()) ? 0 : 200,
    easeIn: !/msie (8|9.)/.test(window.navigator.userAgent.toLowerCase()) ? "easeInQuad" : "linear",
    easeOut: !/msie (8|9.)/.test(window.navigator.userAgent.toLowerCase()) ? "easeOutQuad" : "linear",
    cssTransform: (function () {
      var propArray = ['transform', 'MozTransform', 'WebkitTransform', 'msTransform', 'OTransform'];
      var root = document.documentElement;
      for (var i = 0, len = propArray.length; i < len; i++) {
        if (propArray[i] in root.style) return propArray[i];
      }
      return null;
    })(),
    cssTransition: (function () {
      var propArray = ['transition', 'MozTransition', 'WebkitTransition', 'msTransition', 'OTransition'];
      var root = document.documentElement;
      for (var i = 0, len = propArray.length; i < len; i++) {
        if (propArray[i] in root.style) return propArray[i];
      }
      return null;
    })()
  },
  __opts: {},
  initialize: function(opts) {
    this.__opts = opts;
    var animation = this;
    if(animation.$static.cssTransform !== null && animation.$static.cssTransition !== null) {
      animation.__opts.cssTransitionEnd = (function () {
        var props = { 'WebkitTransition': 'webkitTransitionEnd', 'MozTransition': 'transitionend', 'OTransition': 'oTransitionEnd otransitionend', 'msTransition': 'MSTransitionEnd', 'transition': 'transitionend' };
        return props.hasOwnProperty(animation.$static.cssTransition) ? props[animation.$static.cssTransition] : null;
      })();
    }
  },
  __prepareMove: function(dir) {
    if(this.__opts.outerWrapperElem && !this.__opts.outerWrapperElem.hasClass("overflow-hidden")) {
      this.__opts.outerWrapperElem.addClass("overflow-hidden");
    }
    this.__opts.afterSp = this.__opts[(dir === "in") ? "afterIn" : "afterOut"];
    return [this.__opts.elem.outerWidth(true), this.__opts.elem.outerHeight(true)];
  },
  __afterMove: function() {
    if(this.__opts.outerWrapperElem) this.__opts.outerWrapperElem.removeClass("overflow-hidden");
    if(this.__opts.after) this.__opts.after(this);
    if(this.__opts.afterSp) this.__opts.afterSp(this);
  },
  __horizontalMove: function(dir) {
    var width = this.__prepareMove(dir)[0];
    var left = (dir === "in") ? 0 : -width;

    if(dir === "in") {
      this.__opts.elem.css("marginLeft", -width);
    }
    
    var animation = this;
    if(animation.$static.cssTransform === null || !animation.__opts.useCSSAnim) { // JS pixel pushing
      var easing = (dir === "in") ? "easeIn" : "easeOut";
      var speed = animation.__opts.animationSpeed || animation.$static.animationSpeed;
      animation.__opts.elem.animate({
        "marginLeft": left + "px"
      }, speed, animation.__opts[easing] || animation.$static[easing], function () {
        animation.__afterMove();
      });
    } else { // CSS pixel pushing
      var easing = (dir === "in") ? "cubic-bezier(0.17, 0.04, 0.03, 0.94)" : "cubic-bezier(0.03, 0.94, 0.96, 0.83)";
      var speed = animation.__opts.animationSpeed || animation.$static.animationSpeed;
      var transition = animation.$static.cssTransition;
      var wait = setTimeout(function() {
        animation.__opts.elem.css({
          transition: "all " + speed + "ms " + easing,
          "marginLeft": left + "px"
        });
        animation.__opts.elem.parent().one(animation.__opts.cssTransitionEnd, function() {
          animation.__afterMove();
        });
      }, 5);
    }
  },
  __verticalMove: function(dir) {
    var height = this.__prepareMove(dir)[1];

    var animation = this;
    if(animation.$static.cssTransform === null || !animation.__opts.useCSSAnim) { // JS pixel pushing
      var easing = (dir === "in") ? "easeIn" : "easeOut";
      var speed = animation.__opts.animationSpeed || animation.$static.animationSpeed;
      animation.__opts.elem[(dir === "in") ? "slideDown" : "slideUp"](
         speed, animation.__opts[easing] || animation.$static[easing], function () {
           animation.__afterMove();
       });
    } else { // CSS pixel pushing
      var elm = animation.__opts.elem.is("tr") ? animation.__opts.elem.find('td > div')
                                               : animation.__opts.elem;                                  
      if(dir === "in") {
        var easing = "cubic-bezier(0.17, 0.04, 0.03, 0.94)"; // http://cubic-bezier.com/#.17,.04,.03,.94
        animation.__opts.elem.show();
        elm.show();
        elm.css("marginTop", -height);
        var top = 0;
      } else {
        var easing = "cubic-bezier(0.03, 0.94, 0.96, 0.83)"; // http://cubic-bezier.com/#.03,.94,.96,.83
        var top = -height;
      }
      var uniqueFn = {};
      var unique = +new Date();
      elm.wrap("<div id='hmw-" + unique + "' class='horizontal-move-wrapper' style='overflow: hidden; clear: both;' />");
      var speed = animation.__opts.animationSpeed || animation.$static.animationSpeed;
      var wait = setTimeout(function() {
        var transition = animation.$static.cssTransition;
        elm = $("#hmw-" + unique + " > *");
        elm.css({
          transition: "all " + speed + "ms " + easing,
          "marginTop": top + "px"
        });
        elm.parent().one(animation.__opts.cssTransitionEnd, function() {
          if(dir === "out") {
            animation.__opts.elem.hide();
            if(!elm.length) {
              elm.remove();
            } else {
              elm.unwrap();
            }
          } else {
            elm.unwrap();
          }
          animation.__afterMove();
        });
      }, 5);
    }
  },
  rightIn: function() {
    this.__horizontalMove("in");
  },
  leftOut: function() {
    this.__horizontalMove("out");
  },
  topDown: function() {
    this.__verticalMove("in");
  },
  bottomUp: function() {
    this.__verticalMove("out");
  },
  update: function(opts) {
    this.__opts = opts;
  },
  updateElem: function(elem) {
    this.__opts.elem = elem;
  }
});