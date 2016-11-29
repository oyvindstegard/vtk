/*
 *  VrtxAccordion - facade to jQuery UI accordions (by USIT/GPL|GUAN)
 *
 *  API: http://api.jqueryui.com/accordion/
 *
 *  * Requires Dejavu OOP library
 *  * Requires but Lazy-loads jQuery UI library (if not defined) on open
 *
 */

/* Public
 * ----------------------------------------------------------------------------------------
 * initialize(opts)
 * create()
 * destroy()
 * refresh()
 * closeActiveHidden()              - Close active hidden Accordion header
 * updateHeader(elem, isJson, init) - Update Accordion header
 *
 * Private
 * ----------------------------------------------------------------------------------------
 * __getFieldString(field)              - Get either input value or CK data
 * __findMultiContentMatch(elm)         - All need to have content for a match
 * __findSingleContentMatch(elm)        - One need to have content for a match
 * __headerCheckNoContentOrNoTitle(elm) - Returns text for use in header when no title or no content
 */

var VrtxAccordion = dejavu.Class.declare({
  $name: "VrtxAccordion",
  $constants: {
    headerMultipleCheckClass: ".header-empty-check-and",
    headerSingleCheckClass: ".header-empty-check-or",
    headerPopulatorsClass: ".header-populators",
    headerPopulatorsFallbackClass: ".header-fallback-populator",
    headerRegexRemoveMarkup: /(<([^>]+)>|[\t\r]+)/ig,
    headerEllipsisStart: 30
  },
  __opts: {},
  initialize: function (opts) {
    this.__opts = opts;
  },
  create: function() {
    var accordion = this;
    
    // TODO: VrtxComponents should be a super class with these checks
    var rootUrl = "/vrtx/__vrtx/static-resources";
    var jQueryUiVersion = "1.10.4";

    var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;

    if(typeof vrtxComponents === "undefined") {
      vrtxComponents = {};
      vrtxComponents.futureUi = $.Deferred();
      vrtxComponents.futureUiIsLoading = false;
    }
    if (typeof $.ui === "undefined") {
      if(!vrtxComponents.futureUiIsLoading) {
        vrtxComponents.futureUiIsLoading = true;
        getScriptFn(rootUrl + "/jquery/plugins/ui/jquery-ui-" + jQueryUiVersion + ".custom/js/jquery-ui-" + jQueryUiVersion + ".custom.min.js").done(function () {
          vrtxComponents.futureUiIsLoading = false;
          vrtxComponents.futureUi.resolve();
        });
      }
    } else {
      vrtxComponents.futureUi.resolve();
    }

    $.when(vrtxComponents.futureUi).done(function() {
      accordion.destroy(); // Destroy if already exists

      var initOpts = {
        header: accordion.__opts.headerSelector,
        heightStyle: "content",
        collapsible: true,
        active: accordion.__opts.activeElem ? accordion.__opts.activeElem : false,
        activate: function (e, ui) {
          if(accordion.__opts.onActivate) accordion.__opts.onActivate(e, ui, accordion);
        }
      };
      if(accordion.__opts.animationSpeed) {
        initOpts.animate = {
          duration: accordion.__opts.animationSpeed
        };
      }
      accordion.__opts.elem.accordion(initOpts);
    });
  },
  destroy: function() {
    if(this.__opts.elem.hasClass("ui-accordion")) {
      this.__opts.elem.accordion("destroy");
    }
  },
  refresh: function() {
    this.__opts.elem.accordion("refresh");
  },
  closeActiveHidden: function() {
    var active = this.__opts.elem.find(".ui-state-active");
    if (active.length && active.filter(":hidden").length) {
      this.__opts.elem.accordion("option", "active", false);
    }
  },
  __getFieldString: function(field) {
    var fieldId = field.id;
    if (typeof vrtxEditor !== "undefined" && vrtxEditor.richtextEditorFacade.isInstance(fieldId)) { // Check if CK
      var str = vrtxEditor.richtextEditorFacade.getInstanceValue(fieldId); // Get CK content
    } else {
      var str = field.value; // Get input text
    }
    return str;
  },
  __findMultiContentMatch: function(elm) {
    var containers = elm.find(this.$static.headerMultipleCheckClass);
    for(var i = containers.length; i--;) {
      var inputs = $(containers[i]).find("input[type='text'], textarea");
      for(var j = inputs.length; j--;) {
        if("" === this.__getFieldString(inputs[j])) { // All need to have content for match
          return false;
        }
      }
      return true;
    }
  },
  __findSingleContentMatch: function(elm) {
    var inputs = elm.find(this.$static.headerSingleCheckClass + " input[type='text'], " + this.$static.headerSingleCheckClass + " textarea");
    for(var i = inputs.length; i--;) {
      if("" !== this.__getFieldString(inputs[i])) { // One need to have content for match
        return true;
      }
    }
  },
  __headerCheckNoContentOrNoTitle: function(elm) {
    if(!this.__opts.noTitleText) {
      var lang = (vrtxAdmin !== "undefined") ? vrtxAdmin.lang : $("body").attr("lang");
      this.__opts.noTitleText = (lang !== "en") ? "Ingen tittel" : "No title";
      this.__opts.noContentText = (lang !== "en") ? "Intet innhold" : "No content";
    }
    return (this.__findMultiContentMatch(elm) || this.__findSingleContentMatch(elm)) ? this.__opts.noTitleText : this.__opts.noContentText;
  },
  updateHeader: function(elem, isJson, init) {
    if (typeof elem.closest !== "function") elem = $(elem);
    var elm = isJson ? elem.closest(".vrtx-json-element")
                     : elem.closest(".vrtx-grouped"); // XXX: extract
    if (elm.length) { // Header populators
      var str = "";
      var fields = elm.find(this.$static.headerPopulatorsClass);
      if (!fields.length) return;
      for (var i = 0, len = fields.length; i < len; i++) {
        var val = fields[i].value;
        if (!val.length) continue;
        str += (str.length) ? ", " + val : val;
      }
      if (!str.length) { // Fallback header populator
        var field = elm.find(this.$static.headerPopulatorsFallbackClass);
        if (field.length) {
          str = this.__getFieldString(field);
          if (field.is("textarea")) { // Remove markup and tabs
            str = $.trim(str.replace(this.$static.headerRegexRemoveMarkup, ""));
          }
          if (typeof str !== "undefined") {
            if (str.length > this.$static.headerEllipsisStart) {
              str = str.substring(0, this.$static.headerEllipsisStart) + "...";
            } else if (!str.length) {
              str = this.__headerCheckNoContentOrNoTitle(elm);
            }
          }
        } else {
          str = this.__headerCheckNoContentOrNoTitle(elm);
        }
      }
      var header = elm.find("> .header");
      if (!header.length) {
        elm.prepend('<div class="header">' + str + '</div>');
      } else {
        if (!isJson && init) {
          header.data("origText", header.text());
        }
        header.html('<span class="ui-accordion-header-icon ui-icon ui-icon-triangle-1-e"></span>' + (!isJson ? header.data("origText") + " - " : "") + str);
      }
    }
  }
});
