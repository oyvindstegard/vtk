/*
 * View Toggle * 
 *
 * - Stores cached refs and i18n at init in configs-obj (on toggle link id)
 * 
 */

if (typeof toggler !== "function") {
  function Toggler() {
    this.configs = {
      /* name 
       * showLinkText
       * hideLinkText (optional)
       * combinator (optional)
       * isAnimated (optional)
       */
    };
  }
  var toggler = new Toggler(); /* Global accessible object */

  $(document).ready(function () {
    toggler.init();
  });

  Toggler.prototype.add = function (config) {
    this.configs["vrtx-" + config.name + "-toggle"] = config;
  };

  Toggler.prototype.init = function () {
    var self = this;

    for (var key in self.configs) {
      var config = self.configs[key];
      var container = null;
      if(config.combinator) {
        var selector = config.combinator + "." + config.name;
      } else {
        var selector = "#vrtx-" + config.name;
      }
      container = $(selector);
      var link = $("#" + key);
      if (container.length && link.length) {
        container.hide();
        link.addClass("togglable");
        link.parent().show();
        config.container = container;
        config.link = link;
        
        // ARIA
        container.attr("aria-hidden", "true");
        if(!config.combinator) {
          link.attr({
            "aria-expanded": "false",
            "aria-controls": selector.substring(1)
          });
        }
      }
    }

    $(document).on("click", "a.togglable", function (e) {
      self.toggle(this);
      e.stopPropagation();
      e.preventDefault();
    });
  };

  Toggler.prototype.toggle = function (link) {
    var self = this;

    var config = self.configs[link.id];
    if (config.isAnimated) {
      config.container.slideToggle("fast", function () { /* XXX: proper easing requires jQuery UI */
        self.toggleLinkText(config);
      });
    } else {
      config.container.toggle();
      self.toggleLinkText(config);
    }
  };

  Toggler.prototype.toggleLinkText = function (config) {
    if (config.container.filter(":visible").length) {
      if(!config.hideLinkText) {
        config.link.parent().hide();
      } else {
        config.link.text(config.hideLinkText);
      }
      // ARIA
      config.container.attr("aria-hidden", "false");
      if(!config.combinator) {
        config.link.attr("aria-expanded", "true");
      }
    } else {
      config.link.text(config.showLinkText);
      // ARIA
      config.container.attr("aria-hidden", "true");
      if(!config.combinator) {
        config.link.attr("aria-expanded", "false");
      }
    }
  };
}