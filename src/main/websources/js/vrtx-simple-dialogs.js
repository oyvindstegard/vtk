/*
 *  VrtxSimpleDialog - facade to jQuery UI dialogs (by USIT/GPL|GUAN)
 *
 *  API: http://api.jqueryui.com/dialog/
 *
 *  * Requires Dejavu OOP library
 *  * Requires but Lazy-loads jQuery UI library (if not defined) on open
 *  * Lazy-loads Tree and Datepicker libraries (if not defined) on open if:
 *     - requiresTree: true
 *     - requiresDatepicker: true
 */

/* Public
 * ----------------------
 * initialize(opts)
 * open()
 * close()
 * destroy()
 *
 * Private
 * ----------------------
 * __generateButtons()
 * __addDOM()
 */

var AbstractVrtxSimpleDialog = dejavu.AbstractClass.declare({
  $name: "AbstractVrtxSimpleDialog",        // Meta-attribute useful for debugging
  __opts: {},
  __dialogOpts: {},
  __state: {opening: false, opened: false},
  initialize: function(opts) {              // Constructor
      this.__opts = opts;
      this.__addDOM();
      var dialogOpts =     { modal: true,
                             autoOpen: false,
                             resizable: false,
                             //draggable: false,
                             buttons: this.__generateButtons() };
      if (opts.width)      { dialogOpts.width = opts.width; }
      if (opts.height)     { dialogOpts.height = opts.height; }
      if (opts.unclosable) { dialogOpts.closeOnEscape = false; }
      if (opts.noTitle)    { dialogOpts.dialogClass = "dialog-no-title"; }
      var dialog = this;
      dialogOpts.open = function(e, ui) {
        var ctx = $(this).parent();
        if (dialog.__opts.unclosable) {
          ctx.find(".ui-dialog-titlebar-close").hide();
          ctx.find(".ui-dialog-titlebar").addClass("closable");
        }
        if(dialog.__opts.onOpen) dialog.__opts.onOpen();
        if(dialog.__opts.cancelIsNotAButton) {
          ctx.find(".ui-dialog-buttonpane button:last-child span").unwrap().addClass("cancel-is-not-a-button");
        }
        var inputs = ctx.find("textarea, input[type='text'], select").filter(":visible");
        if(inputs.length) {
          var firstInput = inputs.filter(":first");
          if(firstInput.hasClass("vrtx-date")) {
            $("<a style='outline: none;' tabindex='-1' />").insertBefore(firstInput)[0].focus();
          } else {
            firstInput[0].focus();
          }
        } else {
          input = ctx.find(".ui-dialog-buttonpane, .vrtx-focus-button, .vrtx-button, .vrtx-button-small").filter(":visible").filter(":first");
          if(input.length) {
            $("<a style='outline: none;' tabindex='-1' />").insertBefore(input)[0].focus();
          }
        }
      };
      dialogOpts.close = function(e, ui) {
        if(dialog.__opts.onClose) dialog.__opts.onClose();
      };
      this.__dialogOpts = dialogOpts;
  },
  __generateButtons: function() {
    var buttons = {};
    if (this.__opts.hasOk) {
      var ok = this.__opts.btnTextOk || "Ok";
      var dialog = this;
      buttons[ok] = function() {
        $(this).dialog("close");
        if(dialog.__opts.onOk) dialog.__opts.onOk(dialog.__opts.onOkOpts);
      };
    }
    if(this.__opts.extraBtns) {
      for(var i = 0, len = this.__opts.extraBtns.length; i < len; i++) {
        var extraBtn = this.__opts.extraBtns[i];
        buttons[extraBtn.btnText] = function() {
          $(this).dialog("close");
          if(extraBtn.onOk) extraBtn.onOk(extraBtn.onOkOpts);
        };
      }
    }
    if (this.__opts.hasCancel) {
      var cancel = this.__opts.btnTextCancel || ((typeof cancelI18n !== "undefined") ? cancelI18n : "Cancel");
      if(/^\(/.test(cancel)) {
        this.__opts.cancelIsNotAButton = true;
      }
      var dialog = this;
      buttons[cancel] = function() {
        $(this).dialog("close");
        if(dialog.__opts.onCancel) dialog.__opts.onCancel();
      };
    }
    return buttons;
  },
  __addDOM: function() {
    $(".vrtx-dialog").remove();
    var html = "<div class='vrtx-dialog' id='" + this.__opts.selector.substring(1) + "'";
    if (this.__opts.title) {
      html += " title='" + this.__opts.title + "'";
    }
    $("body").append(html + "><div id='" + this.__opts.selector.substring(1) + "-content' class='vrtx-dialog-content'>"
        + (!this.__opts.hasHtml ? "<p>" + this.__opts.msg + "</p>" : this.__opts.msg) + "</div></div>");
    $(".vrtx-dialog").hide();
  },
  open: function () {
    var dialog = this;
    dialog.__state.opening = true;

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
      if (dialog.__state.opening) {
        dialog.__state.opening = false;
        dialog.__state.opened = true;
        dialog.__opts.elm = $(dialog.__opts.selector);
        if(!dialog.__opts.elm.filter(":visible").length) {
          dialog.__opts.elm.dialog(dialog.__dialogOpts);
          dialog.__opts.elm.dialog("open");
        }
      }
    });
  },
  close: function () {
    this.__state.opening = false;
    if (this.__state.opened) {
      $(".ui-dialog-content").filter(":visible").dialog("close");
      this.__state.opened = false;
    }
  },
  destroy: function () {
    this.__state.opening = false;
    if (this.__state.opened) {
      $(".ui-dialog-content").filter(":visible").dialog("destroy");
      this.__state.opened = false;
      this.__opts.elm.remove();
    }
  }
});

var VrtxLoadingDialog = dejavu.Class.declare({
  $name: "VrtxLoadingDialog",
  $extends: AbstractVrtxSimpleDialog,
  initialize: function (opts) {
    this.$super({
      selector: "#dialog-loading",
      msg: "<img src='/vrtx/__vrtx/static-resources/themes/default/images/loadingAnimation.gif' alt='Loading icon' />",
      title: opts.title,
      hasHtml: true,
      unclosable: true,
      width: opts.width || 208,
      onOpen: function() {
        $("body").attr("aria-busy", "true");
      },
      onClose: function() {
        $("body").attr("aria-busy", "false");
      }
    });
  }
});

var VrtxHtmlDialog = dejavu.Class.declare({
  $name: "VrtxHtmlDialog",
  $extends: AbstractVrtxSimpleDialog,
  initialize: function (opts) {
    this.$super({
      selector: "#dialog-html-" + opts.name,
      msg: opts.html,
      title: opts.title,
      hasHtml: true,
      width: opts.width,
      height: opts.height,
      onOk: opts.onOk,
      onOkOpts: opts.onOkOpts,
      onCancel: opts.onCancel,
      hasOk: opts.btnTextOk,
      hasCancel: opts.btnTextCancel,
      btnTextOk: opts.btnTextOk,
      btnTextCancel: opts.btnTextCancel,
      requiresDatepicker: opts.requiresDatepicker,
      onOpen: opts.onOpen,
      onClose: opts.onClose
    });
  }
});

var VrtxMsgDialog = dejavu.Class.declare({
  $name: "VrtxMsgDialog",
  $extends: AbstractVrtxSimpleDialog,
  initialize: function (opts) {
    this.$super({
      selector: "#dialog-message",
      msg: opts.msg,
      title: opts.title,
      width: opts.width,
      height: opts.height,
      noTitle: opts.noTitle,
      hasOk: true,
      unclosable: true
    });
  }
});

var VrtxConfirmDialog = dejavu.Class.declare({
  $name: "VrtxConfirmDialog",
  $extends: AbstractVrtxSimpleDialog,
  initialize: function (opts) {
    this.$super({
      selector: "#dialog-confirm",
      msg: opts.msg,
      title: opts.title,
      width: opts.width,
      height: opts.height,
      hasOk: true,
      hasCancel: true,
      btnTextOk: opts.btnTextOk,
      btnTextCancel: opts.btnTextCancel,
      onOk: opts.onOk,
      onOkOpts: opts.onOkOpts,
      onCancel: opts.onCancel,
      extraBtns: opts.extraBtns
    });
  }
});

var VrtxIFrameDialog = dejavu.Class.declare({
  $name: "VrtxIFrameDialog",
  $extends: AbstractVrtxSimpleDialog,
  initialize: function (opts) {
    var html = '<iframe width="100%" height="100%" style="min-width: 95%;height:100%;"'
        + ' frameborder="0" src="' + opts.url + '"></iframe>';
    this.$super({
      selector: "#dialog-iframe-" + opts.name,
      msg: html,
      noTitle: opts.noTitle,
      hasHtml: true,
      width: opts.width,
      height: opts.height,
      onOpen: opts.onOpen,
      onClose: opts.onClose
    });
  }
});
