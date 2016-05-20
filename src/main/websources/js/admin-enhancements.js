/*
 *  Vortex Admin enhancements
 *
 *  ToC:
 *
 *  1.  Config
 *  2.  DOM is ready
 *  3.  DOM is fully loaded
 *  4.  General / setup interactions
 *  5.  Permissions
 *  6.  Async functions
 *  7.  Async helper functions and AJAX server façade
 *  8.  Popups and CK browse server integration
 *  9.  Utils
 *  10. Override JavaScript / jQuery
 *
 */

/*-------------------------------------------------------------------*\
    1. Config
\*-------------------------------------------------------------------*/

var startLoadTime = getNowTime();

/**
 * Creates an instance of VrtxAdmin
 * @constructor
 */
function VrtxAdmin() {

  /** Cache jQuery instance internally
   * @type object */
  this._$ = $;

  // Browser info/capabilities: used for e.g. progressive enhancement and performance scaling based on knowledge of current JS-engine
  this.ua = window.navigator.userAgent.toLowerCase();

  /* IE */
  this.isIE = /(msie) ([\w.]+)/.test(this.ua);
  var ieVersion = /(msie) ([\w.]+)/.exec(this.ua);
  this.browserVersion = (ieVersion !== null) ? ieVersion[2] : 99;
  this.isIE9 = this.isIE && this.browserVersion <= 9;
  this.isIE8 = this.isIE && this.browserVersion <= 8;
  this.isIE7 = this.isIE && this.browserVersion <= 7;
  this.isIE6 = this.isIE && this.browserVersion <= 6;
  this.isIETridentInComp = this.isIE7 && /trident/.test(this.ua);

  /* Mobile (iOS and Android) */
  this.isIPhone = /iphone/.test(this.ua);
  this.isIPad = /ipad/.test(this.ua);
  this.isIOS = this.isIPhone || this.isIPad;
  this.isIOS5 = this.isIOS && this.ua.match(/os (2|3|4|5)_/) !== null;
  this.isAndroid = /android/.test(this.ua); // http://www.gtrifonov.com/2011/04/15/google-android-user-agent-strings-2/
  this.isMobileWebkitDevice = (this.isIPhone || this.isIPad || this.isAndroid);
  this.isTouchDevice = window.ontouchstart !== undefined;

  /* Windows */
  this.isWin = ((this.ua.indexOf("win") !== -1) || (this.ua.indexOf("16bit") !== -1));

  this.supportsFileList = window.FileList;
  this.animateTableRows = !this.isIE9;
  this.hasFreeze = typeof Object.freeze !== "undefined"; // ECMAScript 5 check

  this.hasConsole = typeof console !== "undefined";
  this.hasConsoleLog = this.hasConsole && console.log;
  this.hasConsoleError = this.hasConsole && console.error;

  /** Language extracted from i18n in resource-bar.ftl */
  this.lang = "en";

  /** Permissions autocomplete parameters
   * @type object */
  this.permissionsAutocompleteParams = {
    minChars: 4,
    selectFirst: false,
    max: 30,
    delay: 800,
    minWidth: 180,
    adjustForParentWidth: 16,
    adjustLeft: 8
  };
  /** Username autocomplete parameters
   * @type object */
  this.usernameAutocompleteParams = {
    minChars: 2,
    selectFirst: false,
    max: 30,
    delay: 500,
    multiple: false,
    minWidth: 180,
    adjustForParentWidth: 16,
    adjustLeft: 8
  };
  /** Tag autocomplete parameters
   * @type object */
  this.tagAutocompleteParams = {
    minChars: 1,
    minWidth: 180,
    adjustForParentWidth: 16,
    adjustLeft: 8
  };

  /* Transitions */
  this.transitionPropSpeed = this.isMobileWebkitDevice ? 0 : 100;
  this.transitionDropdownSpeed = this.isMobileWebkitDevice ? 0 : 100;
  this.transitionEasingSlideDown = (!this.isIE9 && !this.isMobileWebkitDevice) ? "easeOutQuad" : "linear";
  this.transitionEasingSlideUp = (!this.isIE9 && !this.isMobileWebkitDevice) ? "easeInQuad" : "linear";

  /* Throttle / debounced listeners */
  this.windowResizeScrollDebounceRate = 20;
  this.keyInputDebounceRate = 50;

  /* Save */
  this.editorSaveButtonName = "";
  this.editorSaveButton = null;
  this.editorSaveIsRedirectPreview = false;

  /* Async operations */
  this.asyncEditorSavedDeferred = null;
  this.asyncGetFormsInProgress = 0;
  this.asyncGetStatInProgress = false;
  
  /* Editor keep alive */
  this.editorIsDead = false;
  this.editorLastInteraction = +new Date();
  this.editorKeepAlive = 30*60*1000;
  this.editorDeadMsgGiven = false;

  /* Upload */
  this.uploadCopyMoveSkippedFiles = {};
  this.uploadCompleteTimeoutBeforeProcessingDialog = 2000; // 2s

  /* Create and trashcan */
  this.createResourceReplaceTitle = true;
  this.createDocumentFileName = "";
  this.trashcanCheckedFiles = 0;

  /** Keyboard keys enum
   * @type object */
  this.keys = { TAB: 9, ENTER: 13, ESCAPE: 27, SPACE: 32, LEFT_ARROW: 37, UP_ARROW: 38, RIGHT_ARROW: 39, DOWN_ARROW: 40 };

  this.reloadFromServer = false; // changed by funcProceedCondition and used by funcComplete in completeFormAsync for admin-permissions
  this.ignoreAjaxErrors = false;
  this._$.ajaxSetup({
    timeout: 300000 // 5min
  });
  this.runReadyLoad = true;
  this.domainsIsReady = $.Deferred();
  this.domainsInstantIsReady = $.Deferred();
  this.bodyId = "";

  this.requiredScriptsLoaded = null;

  this.messages = {}; /* Populated with i18n in resource-bar.ftl */

  this.rootUrl = "/vrtx/__vrtx/static-resources";
}

var vrtxAdmin = new VrtxAdmin();

/* Dummy containers for required init components (external scripts)
 *
 * Replaced and applied in the future when script-retrieval is done
 * (you have to be really fast or have a slow connection speed to get this code running,
 *  but the latter could occur on e.g. a 3G-connection)
 */
var VrtxTree = function(opts) {
  var obj = this;
  if(typeof VrtxTreeInterface === "undefined") {
    $.when(vrtxAdmin.requiredScriptsLoaded).done(function() {
      obj = new VrtxTree(opts); // Resolve and replace dummy container
    });
  }
};
var VrtxAnimation = function(opts) {
  var obj = this, objApplied = [];
  if(typeof VrtxAnimationInterface === "undefined") {
    $.when(vrtxAdmin.requiredScriptsLoaded).done(function() {
      obj = new VrtxAnimation(opts); // Resolve and replace dummy container
      for(var i = 0, len = objApplied.length; i < len; i++) { // Apply functions
        obj[objApplied[i].fn](objApplied[i].args);
      }
    });
  }

  var futureAppliedFn = function(methodName, args) {
    if(methodName !== null) { objApplied.push({fn: methodName, args: args}); }
  };

  /* Add applied functions for future running.
   * TODO: general object prop access handling possible? http://blog.calyptus.eu/seb/2010/11/javascript-proxies-leaky-this/?
   * TODO: Firefox can use Proxy and get() for adding future applied functions(?) http://wiki.ecmascript.org/doku.php?id=harmony%3adirect_proxies
   */
  obj.update = function (opts) { futureAppliedFn("update", opts); };
  obj.updateElem = function (elem) { futureAppliedFn("updateElem", elem); };
  obj.rightIn = function () { futureAppliedFn("rightIn", null); };
  obj.leftOut = function () { futureAppliedFn("leftOut", null); };
  obj.topDown = function () { futureAppliedFn("topDown", null); };
  obj.bottomUp = function () { futureAppliedFn("bottomUp", null); };
};


/*-------------------------------------------------------------------*\
    2. DOM is ready
\*-------------------------------------------------------------------*/

var isEmbedded = window.location.href.indexOf("&embed") !== -1;
var isEmbedded2 = false;
var onlySessionId = gup("sessionid", window.location.href);

vrtxAdmin._$(document).ready(function () {
  var startReadyTime = getNowTime(), vrtxAdm = vrtxAdmin, _$ = vrtxAdm._$;

  if(typeof datePickerLang === "string") {
    vrtxAdm.lang = datePickerLang;
  }
  vrtxAdm.cacheDOMNodesForReuse();

  isEmbedded2 = vrtxAdm.cachedBody.hasClass("embedded2");
  if(isEmbedded2) {
    $("html").addClass("embedded2");
  }

  vrtxAdm.bodyId = vrtxAdm.cachedBody.attr("id") || "";
  vrtxAdm.cachedBody.addClass("js");
  if(vrtxAdm.isIE8) {
    vrtxAdm.cachedBody.addClass("ie8");
  }
  vrtxAdm.embeddedView();

  // Show message in IE6=>IE8 and IETrident in compability mode
  if ((vrtxAdm.isIE8 || vrtxAdm.isIETridentInComp) && typeof outdatedBrowserText === "string") {
    var message = vrtxAdm.cachedAppContent.find(" > .message");
    if (message.length) {
      message.html(outdatedBrowserText);
    } else {
      var messageHtml = "<div class='infomessage'>" + outdatedBrowserText + "</div>";
      if(vrtxAdm.bodyId === "vrtx-simple-editor" || isEmbedded) {
        vrtxAdm.cachedBody.prepend(messageHtml);
      } else {
        vrtxAdm.cachedAppContent.prepend(messageHtml);
      }
    }
  }
   // Return if should not run all of ready() code
  if (vrtxAdm.runReadyLoad === false) return;

  // Load required init components (animations and trees)
  vrtxAdm.requiredScriptsLoaded = $.Deferred();
  vrtxAdm.loadScripts(["/js/vrtx-animation.js", "/js/vrtx-tree.js"], vrtxAdm.requiredScriptsLoaded);
  vrtxAdm.clientLastModified = $("#resource-last-modified").text().split(",");

  /* Delay some stuff and only run some stuff if not embedded */
  if(!isEmbedded && !isEmbedded2) {
    vrtxAdm.initDropdowns();
    vrtxAdm.initScrollBreadcrumbs();
  }
  vrtxAdm.domainsInstantIsReady.resolve();

  if(!isEmbedded && !isEmbedded2) {
    vrtxAdm.initMiscAdjustments();
  }

  var waitALittle = setTimeout(function() {
    vrtxAdm.initTooltips();
    if(!isEmbedded && !isEmbedded2) {
      vrtxAdm.initGlobalDialogs();
    }
  }, 15);

  var waitALittleMore = setTimeout(function() {
    if(!isEmbedded && !isEmbedded2) {
      vrtxAdm.initResourceMenus();
    }
    vrtxAdm.initDomains();
    vrtxAdm.domainsIsReady.resolve();
  }, 25);

  vrtxAdm.log({ msg: "Document.ready() in " + (getNowTime() - startReadyTime) + "ms." });
});


/*-------------------------------------------------------------------*\
    3. DOM is fully loaded
\*-------------------------------------------------------------------*/

vrtxAdmin._$(window).load(function () {
  var vrtxAdm = vrtxAdmin;
  if (vrtxAdm.runReadyLoad === false) return; // Return if should not run load() code

  vrtxAdm.log({ msg: "Window.load() in " + (getNowTime() - startLoadTime) + "ms." });
});


/*-------------------------------------------------------------------*\
    4. General / setup interactions
\*-------------------------------------------------------------------*/

/*
 * Cache DOM nodes for reuse
 *
 */

VrtxAdmin.prototype.cacheDOMNodesForReuse = function cacheDOMNodesForReuse() {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  if(vrtxAdm.cachedBody) return;

  vrtxAdm.cachedDoc = _$(document);
  vrtxAdm.cachedBody = vrtxAdm.cachedDoc.find("body");
  vrtxAdm.cachedAppContent = vrtxAdm.cachedBody.find("#app-content");
  vrtxAdm.cachedContent = vrtxAdm.cachedAppContent.find("#contents");
  vrtxAdm.cachedDirectoryListing = _$("#directory-listing");
  vrtxAdm.cachedActiveTab = vrtxAdm.cachedAppContent.find("#active-tab");
};

/*
 * Tooltips init
 *
 */

VrtxAdmin.prototype.initTooltips = function initTooltips() {
  var titleContainer = $("#title-container");
  if(titleContainer.length) {
    titleContainer.vortexTips("abbr:not(.delayed)", {
      appendTo: "#title-container",
      containerWidth: 200, xOffset: 20, yOffset: 0
    });
    titleContainer.vortexTips("abbr.delayed", {
      appendTo: "#title-container",
      containerWidth: 200, xOffset: 20, yOffset: 0,
      expandHoverToTipBox: true
    });
  }
  $("#main").vortexTips(".tooltips", {
    appendTo: "#contents",
    containerWidth: 320,
    xOffset: 20, yOffset: -15
  });
  this.cachedBody.vortexTips(".ui-dialog:visible .tree-create li span.folder", {
    appendTo: ".vrtx-create-tree",
    containerWidth: 80,
    xOffset: 10, yOffset: -8,
    expandHoverToTipBox: true,
    enterOpens: true,
    extra: true
  });
  this.cachedBody.vortexTips("td.permissions span.permission-tooltips", {
    appendTo: "#contents",
    containerWidth: 340,
    xOffset: 10, yOffset: -8,
    expandHoverToTipBox: true,
    enterOpens: true
  });
};

/*
 * Resource menus init
 *
 */

VrtxAdmin.prototype.initResourceMenus = function initResourceMenus() {
  var vrtxAdm = this,
      bodyId = vrtxAdm.bodyId;
      _$ = vrtxAdm._$;

  var resourceMenuLeftServices = ["renameService", "deleteResourceService", "manage\\.createArchiveService", "manage\\.expandArchiveService"];
  for (var i = resourceMenuLeftServices.length; i--;) {
    vrtxAdm.getFormAsync({
      selector: "#title-container a#" + resourceMenuLeftServices[i],
      selectorClass: "globalmenu",
      insertAfterOrReplaceClass: "#resource-title > ul:last",
      nodeType: "div",
      simultanSliding: true
    });

    // Ajax post if rename (with redirect)
    var isRename = resourceMenuLeftServices[i] === "renameService";

    vrtxAdm.completeFormAsync({
      selector: "form#" + resourceMenuLeftServices[i] + "-form input[type=submit]",
      errorContainer: "errorContainer",
      errorContainerInsertAfter: "h3",
      errorUpdateSelectors: (isRename ? ["form#renameService-form #submitButtons", "form#renameService-form .vrtx-textfield"] : null),
      post: isRename,
      ignoreDifferentMode: true,
      funcComplete: (isRename ? function(p) {
        var currentPath = location.pathname;
        var isCollection = /\/$/.test(currentPath);
        var newName = $("form#renameService-form .vrtx-textfield").val();
        var newPath = isCollection ? currentPath.replace(/[^\/]+\/$/, "") + newName + "/"
                                   : currentPath.replace(/[^\/]+$/, "") + newName;
        location.pathname = newPath;
      } : null)
    });
  }
  var resourceMenuRightServices = ["vrtx-unpublish-document", "vrtx-publish-document"];
  for (i = resourceMenuRightServices.length; i--;) {
    var publishUnpublishService = resourceMenuRightServices[i];

    // Ajax save before publish if editing
    var isSavingBeforePublish = publishUnpublishService === "vrtx-publish-document" && (bodyId === "vrtx-editor" || bodyId === "vrtx-edit-plaintext");

    vrtxAdm.getFormAsync({
      selector: "#title-container a#" + publishUnpublishService,
      selectorClass: "globalmenu",
      insertAfterOrReplaceClass: "#resource-title > ul:last",
      nodeType: "div",
      simultanSliding: true,
      funcComplete: (isSavingBeforePublish ? function (p) {
        $("#vrtx-publish-document-form h3").text(vrtxAdmin.messages.publish.saveConfirm);
      } : null)
    });
    vrtxAdm.completeFormAsync({
      selector: "form#" + publishUnpublishService + "-form input[type=submit]",
      updateSelectors: ["#resource-title", "#directory-listing", ".prop-lastModified", "#resource-last-modified"],
      funcComplete: (isSavingBeforePublish ? function (link) { // Save async
        $(".vrtx-focus-button.vrtx-save-button").click();
        vrtxAdm.completeFormAsyncPost({ // Publish async
          updateSelectors: ["#resource-title", "#resource-last-modified"],
          link: link,
          form: $("#vrtx-publish-document-form"),
          funcComplete: function () {
            if(typeof updateClientLastModifiedAlreadyRetrieved === "function") {
              updateClientLastModifiedAlreadyRetrieved();
            }
            vrtxAdm.globalAsyncComplete();
          }
        });
        return false;
      } : function(link) {
        if(typeof updateClientLastModifiedAlreadyRetrieved === "function") {
          updateClientLastModifiedAlreadyRetrieved();
        }
        vrtxAdm.globalAsyncComplete();
      }),
      post: (!isSavingBeforePublish && (typeof isImageAudioVideo !== "boolean" || !isImageAudioVideo))
    });
  }

  // Unlock form
  vrtxAdm.getFormAsync({
    selector: "#title-container a#manage\\.unlockFormService",
    selectorClass: "globalmenu",
    insertAfterOrReplaceClass: "#resource-title > ul:last",
    nodeType: "div",
    simultanSliding: true
  });
  // Regular POST unlock lock from other user
  vrtxAdm.completeFormAsync({
    selector: "form#manage\\.unlockFormService-form input[type=submit]"
  });
  // AJAX POST unlock own lock
  vrtxAdm.completeFormAsync({
    selector: "li.manage\\.unlockFormService form[name=unlockForm]",
    updateSelectors: ["#resourceMenuRight", "#contents"],
    funcComplete: function() {
      vrtxAdm.globalAsyncComplete();
    },
    post: (bodyId !== "vrtx-preview" && bodyId !== "vrtx-editor" && bodyId !== "vrtx-edit-plaintext" && bodyId !== "")
  });
};

/*
 * Global dialogs init
 *
 */

VrtxAdmin.prototype.initGlobalDialogs = function initGlobalDialogs() {
  var vrtxAdm = this,
      bodyId = vrtxAdm.bodyId;
      _$ = vrtxAdm._$;

  // Create folder chooser in global menu
  eventListen(vrtxAdm.cachedDoc, "click", "#global-menu-create a, #vrtx-report-view-other", function (link) {
    var id = link.id + "-content";
    vrtxAdm.serverFacade.getHtml(link.href, {
      success: function (results, status, resp) {
        var dialogManageCreate = _$("#" + id);
        if(dialogManageCreate.length) {
          dialogManageCreate.remove();
        }
        vrtxAdm.cachedBody.append("<div id='" + id + "'>" + _$(_$.parseHTML(results)).find("#vrtx-manage-create-content").html() + "</div>");
        dialogManageCreate = _$("#" + id);
        dialogManageCreate.hide();
        var d = new VrtxHtmlDialog({
          name: "global-menu-create",
          html: dialogManageCreate.html(),
          title: link.title,
          width: 600,
          height: 395,
          onOpen: function() {
            var dialog = $(".ui-dialog:visible");

            var treeElem = dialog.find(".tree-create");
            var treeTrav = dialog.find("#vrtx-create-tree-folders").hide().text().split(",");
            var treeType = dialog.find("#vrtx-create-tree-type").hide().text();
            var treeAddParam = dialog.find("#vrtx-create-tree-add-param");

            var service = "service=" + treeType + "-from-drop-down";
            if(treeAddParam.length) {
              treeAddParam = treeAddParam.hide().text();
              service += "&" + treeAddParam;
            }

            treeElem.on("click", "a", function (e) { // Don't want click on links
              e.preventDefault();
            });

            dialog.on("click", ".tip a", function (e) { // Override jQuery UI prevention
              window.location.href = this.href;
            });

            var tree = new VrtxTree({
              service: service,
              elem: treeElem,
              trav: treeTrav,
              afterTrav: function(link) {
                linkTriggeredMouseEnter = link;
                linkTriggeredMouseEnterTipText = linkTriggeredMouseEnter.attr('title');
                link.parent().trigger("mouseenter");
              },
              scrollToContent: ".ui-dialog:visible .ui-dialog-content"
            });
          }
        });
        d.open();
      }
    });
  });

  // Advanced publish settings (need more encapsulation)
  var apsD;
  var datepickerApsD;
  eventListen(vrtxAdm.cachedDoc, "click", "#advanced-publish-settings", function (link) {
    var id = link.id + "-content";
    vrtxAdm.serverFacade.getHtml(link.href + "&" + (+new Date()), {
      success: function (results, status, resp) {
        var dialogAPS = _$("#" + id);
        if(dialogAPS.length) {
          dialogAPS.remove();
        }
        vrtxAdm.cachedBody.append("<div id='" + id + "'>" + _$(_$.parseHTML(results)).find("#vrtx-advanced-publish-settings-dialog").html() + "</div>");
        dialogAPS = _$("#" + id);
        dialogAPS.hide();
        apsD = new VrtxHtmlDialog({
          name: "advanced-publish-settings",
          html: dialogAPS.html(),
          title: dialogAPS.find("h1").text(),
          width: 400,
          requiresDatepicker: true,
          onOpen: function() {
            $(".ui-dialog-buttonpane").hide();
            var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
            var futureDatepicker = (typeof VrtxDatepicker === "undefined") ? getScriptFn(vrtxAdm.rootUrl + "/js/datepicker/vrtx-datepicker.js") : $.Deferred().resolve();
            $.when(futureDatepicker).done(function() {
              datepickerApsD = new VrtxDatepicker({
                language: datePickerLang,
                selector: "#dialog-html-advanced-publish-settings-content"
              });
            });
          }
        });
        apsD.open();
      }
    });
  });

  vrtxAdm.completeFormAsync({
    selector: "#dialog-html-advanced-publish-settings-content #submitButtons input",
    updateSelectors: ["#resource-title", "#directory-listing", ".prop-lastModified", "#resource-last-modified"],
    post: true,
    isUndecoratedService: true,
    isNotAnimated: true,
    funcCancel: function() {
      apsD.close();
    },
    funcProceedCondition: function(options) {
      var dialogId = "#dialog-html-advanced-publish-settings-content";
      var dialog = $(dialogId);

      var publishDate = generateDateObjForValidation(dialog, "publishDate");
      var unpublishDate = generateDateObjForValidation(dialog, "unpublishDate");

      // Check that unpublish date is not set alone
      if(unpublishDate !== null && publishDate === null) {
        vrtxAdm.displayDialogErrorMsg(dialogId + " #submitButtons", vrtxAdmin.messages.publish.unpublishDateNonExisting);
        return;
      }

      // Check that unpublish date is not before or same as publish date
      if(unpublishDate !== null && (unpublishDate <= publishDate)) {
        vrtxAdm.displayDialogErrorMsg(dialogId + " #submitButtons", vrtxAdmin.messages.publish.unpublishDateBefore);
        return;
      }

      datepickerApsD.prepareForSave();
      vrtxAdm.completeFormAsyncPost(options);
    },
    funcComplete: function () {
      apsD.close();
      if(typeof updateClientLastModifiedAlreadyRetrieved === "function") {
        updateClientLastModifiedAlreadyRetrieved();
      }
      vrtxAdm.globalAsyncComplete();
    }
  });

  // Validation of dates
  var generateDateObjForValidation = function(dialog, idInfix) {
    var date = dialog.find("#" + idInfix + "-date").val();
    if(!date.length) {
      return null;
    }
    date = date.split("-");
    if(!date.length === 3) {
      return null;
    }
    var hh = dialog.find("#" + idInfix + "-hours").val();
    if(!hh.length) {
      return new Date(date[0], date[1], date[2], 0, 0, 0, 0);
    }
    var mm = dialog.find("#" + idInfix + "-minutes").val();
    return new Date(date[0], date[1], date[2], hh, mm, 0, 0);
  };
};

/**
 * On global async completion
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.globalAsyncComplete = function globalAsyncComplete() {
  var vrtxAdm = this;
  if(vrtxAdm.bodyId === "vrtx-preview") {
    var previewIframe = $("#previewIframe");
    if(previewIframe.length) {
      var src = previewIframe[0].src;
      previewIframe[0].src = src;
    }
  }
  $("#advanced-publish-settings-content").remove();

  vrtxAdm.initResourceTitleDropdown();
  vrtxAdm.initPublishingDropdown();
  vrtxAdm.adjustResourceTitle();
  if(typeof vrtxAdm.updateCollectionListingInteraction === "function") {
    vrtxAdm.updateCollectionListingInteraction();
  }
};

/**
 * Embedded view (editor)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.embeddedView = function embeddedView() {
  if(!isEmbedded) return;

  $("html").addClass("embedded embedded-loading");

  if(onlySessionId) { // Also handles when locked or no write permissions (goes to preview)
    var canEdit = $("#resource-can-edit").text() === "true";
    var lockedByOtherElm = $("#resource-locked-by-other");
    var lockedByOther = (lockedByOtherElm.length && lockedByOtherElm.text() === "true") ? $("#resource-locked-by").html() : "";

    // Choose proper fail message (we know these sends you to preview)
    var vrtxAdm = vrtxAdmin;
    if(!canEdit || lockedByOther.length) {
      // Title and fail message
      var csTitle = vrtxAdm.lang === "en" ? "Edit activity" : "Rediger aktivitet";
      if(!canEdit) {
        var csFail = vrtxAdm.lang === "en" ? "You don't have write permissions to edit the course schedule." : "Du har ikke skriverettigheter til å redigere denne timeplanen.";
      } else {
        var csFail = vrtxAdm.lang === "en" ? "The course schedule is locked by other user: " + lockedByOther : "Timeplanen er låst av en annen bruker: " + lockedByOther;
      }

      var failHtml = "<p id='editor-fail'>" + csFail + "</p>";

      this.cachedBody.addClass("embedded-editor-fail");

      // Add embedded editor fail HTML
      var titleHtml = '<div id="vrtx-editor-title-submit-buttons"><div id="vrtx-editor-title-submit-buttons-inner-wrapper"><h2>' + csTitle + '<a href="javascript:void(0)" class="vrtx-close-dialog-editor"></a></h2></div></div>';
      var buttonsHtml = '<div class="submit submitButtons"><input class="vrtx-focus-button vrtx-embedded-button" id="vrtx-embedded-cancel-button" type="submit" value="Ok" /></div>';
      this.cachedContent.html(titleHtml + failHtml + buttonsHtml);

      // Ok / 'X' goes back to view
      eventListen(vrtxAdm.cachedDoc, "click", "#vrtx-embedded-cancel-button, .vrtx-close-dialog-editor", function (ref) {
        location.href = $("#global-menu-leave-admin a").attr("href");
      });
    } else {
      $("#editor").height($(window).height() - $("body").height());
    }
  }
};

/*
 * Domains init (based on id of body-tag) - rest is in domain-specific JS-files in domains/-folder
 *
 */
VrtxAdmin.prototype.initDomains = function initDomains() {
  var vrtxAdm = this,
      bodyId = vrtxAdm.bodyId;
      _$ = vrtxAdm._$;

  switch (bodyId) {
    case "vrtx-preview":
    case "vrtx-revisions":
      // Preview of revision in popup window (from preview or revisions tab)
      eventListen(vrtxAdm.cachedAppContent, "click", "a.vrtx-revision-view, a.vrtx-revision-view-changes", function (ref) {
        var openedRevision = openRegular(ref.href, 1020, 800, "DisplayRevision");
      });

      if (bodyId === "vrtx-revisions") {
        // Delete revisions
        vrtxAdm.completeSimpleFormAsync({
          selector: ".vrtx-revisions-delete-form input[type=submit]",
          updateSelectors: ["#app-tabs", "#contents"],
          rowFromFormAnimateOut: true
        });

        // Restore revisions
        vrtxAdm.completeSimpleFormAsync({
          selector: ".vrtx-revisions-restore-form input[type=submit]",
          updateSelectors: ["#contents"],
          fnBeforePost: function() {
            _$("td.vrtx-revisions-buttons-column input").attr("disabled", "disabled"); // Lock buttons
          },
          fnComplete: function(resultElm, form, url) {
            if (typeof versionsRestoredInfoMsg !== "undefined") {
              var revisionNr = url.substring(url.lastIndexOf("=") + 1, url.length);
              var versionsRestoredInfoMsgTmp = versionsRestoredInfoMsg.replace("X", revisionNr);
              vrtxAdm.displayInfoMsg(versionsRestoredInfoMsgTmp);
            }
            window.scroll(0, 0);
          },
          fnError: function() {
            _$("td.vrtx-revisions-buttons-column input").removeAttr("disabled"); // Unlock buttons
          }
        });

        // Make working copy into current version
        vrtxAdm.completeSimpleFormAsync({
          selector: "#vrtx-revisions-make-current-form input[type=submit]",
          updateSelectors: ["#contents", "#app-tabs"],
          fnComplete: function(resultElm, form, url) {
            if (typeof versionsMadeCurrentInfoMsg !== "undefined") {
              vrtxAdm.displayInfoMsg(versionsMadeCurrentInfoMsg);
            }
          }
        });
      }
      break;
    case "vrtx-report-broken-links":
      // Broken links switch between alternative views
      eventListen(vrtxAdm.cachedDoc, "click", ".vrtx-report-alternative-view-switch input", function (ref) {
        if(!$(ref).is(":checked")) {
          window.location.href = window.location.href.replace("&" + ref.name, "");
        } else {
          window.location.href = window.location.href + "&" + ref.name;
        }
      });
      break;
    default:
      // noop
      break;
  }
};

/**
 * Initialize dropdowns
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.initDropdowns = function initDropdowns() {
  this.dropdownPlain("#locale-selection");
  this.initResourceTitleDropdown();
  this.dropdown({
    selector: "ul.manage-create",
    title: vrtxAdmin.messages.dropdowns.createTitle
  });
  this.initPublishingDropdown();
  var vrtxAdm = this;
  this.cachedBody.on("click", ".dropdown-shortcut-menu li a, .dropdown-shortcut-menu-container li a", function () {
    vrtxAdm.closeDropdowns();
  });
  this.cachedBody.on("click", document, function (e) {
    vrtxAdm.closeDropdowns();
    vrtxAdm.hideTips();
  });
};

/**
 * Initialize resource title dropdown
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.initResourceTitleDropdown = function initResourceTitleDropdown() {
  this.dropdown({
    selector: "#resource-title ul#resourceMenuLeft",
    proceedCondition: function (numOfListElements) {
      return numOfListElements > 1;
    },
    calcTop: true,
    title: vrtxAdmin.messages.dropdowns.resourceTitle
  });
};

/**
 * Initialize publishing dropdown
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.initPublishingDropdown = function initPublishingDropdown() {
  this.dropdown({
    selector: "ul.publishing-document",
    small: true,
    calcTop: true,
    calcLeft: true,
    title: vrtxAdmin.messages.dropdowns.publishingTitle
  });
};

/**
 * Dropdown with links
 *
 * @this {VrtxAdmin}
 * @param {string} selector The selector for container
 */
VrtxAdmin.prototype.dropdownPlain = function dropdownPlain(selector) {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var languageMenu = _$(selector + " ul");
  if (!languageMenu.length) return;

  var idWrp = languageMenu.attr("id");
  if(!idWrp || idWrp === "") {
    idWrp = "dropdown-shortcut-menu-container-" + Math.round(+new Date() * Math.random());
    languageMenu.attr("id", idWrp);
  }
  var idLink = selector.substring(1) + "-header";

  languageMenu.addClass("dropdown-shortcut-menu-container");
  languageMenu.attr("aria-hidden", "true");
  languageMenu.attr("aria-labelledby", idLink);

  var parent = languageMenu.parent();
  var header = parent.find(selector + "-header");
  var headerText = header.text();
  header.replaceWith("<a href='javascript:void(0);' id='" + idLink + "' class='dropdown-shortcut-menu-click-area' aria-haspopup='true' aria-controls='" + idWrp + "' aria-expanded='false'>" + headerText.substring(0, headerText.length - 1) + "</a>");

  eventListen(vrtxAdm.cachedBody, "click", selector + "-header", function (ref) {
    var link = _$(ref);
    var wrp = link.next(".dropdown-shortcut-menu-container");
    vrtxAdm.closeDropdowns();
    vrtxAdm.openDropdown(link, wrp);
  });
};

/**
 * Dropdown with button-row
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuration
 * @param {string} options.selector The selector for the container (list)
 * @param {function} options.proceedCondition Callback function before proceeding that uses number of list elements as parameter
 * @param {number} options.start Specify a starting point otherwise first is used
 * @param {boolean} options.calcTop Wheter or not to calculate absolute top position
 */
VrtxAdmin.prototype.dropdown = function dropdown(options) {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var list = _$(options.selector);
  if (!list.length) return;

  var listElements = list.find("li");
  var numOfListElements = listElements.length;

  if (!options.proceedCondition || (options.proceedCondition && options.proceedCondition(numOfListElements))) {
    list.addClass("dropdown-shortcut-menu");
    if(options.small) {
      list.addClass("dropdown-shortcut-menu-small");
    }

    var idWrp = "dropdown-shortcut-menu-container-" + Math.round(+new Date() * Math.random());
    var idLink = "dropdown-shortcut-menu-click-area-" + Math.round((+new Date() + 1) * Math.random());

    // Move listelements except .first into container
    var listParent = list.parent();
    listParent.append("<div id='" + idWrp + "'class='dropdown-shortcut-menu-container' aria-labelledby='" + idLink + "' aria-hidden='true'><ul>" + list.html() + "</ul></div>");

    var startDropdown = options.start ? ":nth-child(-n+" + options.start + ")" : ".first";
    var dropdownClickArea = options.start ? ":nth-child(3)" : ".first";

    listElements.not(startDropdown).remove();
    listElements.filter("li" + dropdownClickArea).append("<span id='" + idLink + "' title='" + options.title + "' role='link' aria-haspopup='true' aria-controls='" + idWrp + "' aria-expanded='false' tabindex='0' class='dropdown-shortcut-menu-click-area' />");
    var shortcutMenu = listParent.find(".dropdown-shortcut-menu-container");
    shortcutMenu.find("li" + startDropdown).remove();

    var waitSome = setTimeout(function() { // Adjust positioning of dropdown container
      if (options.calcTop) {
        shortcutMenu.css("top", (list.position().top + list.height() - (parseInt(list.css("marginTop"), 10) * -1) + 2) + "px");
      }
      var left = (list.width() - list.find(".dropdown-shortcut-menu-click-area").width() - 2);
      if (options.calcLeft) {
        left += list.position().left;
      }
      shortcutMenu.css("left", left + "px");
    }, 500);

    var togglerWrp = list.find("li" + dropdownClickArea);
    togglerWrp.addClass("dropdown-init");

    eventListen(togglerWrp, "click keypress", ".dropdown-shortcut-menu-click-area", function (ref) {
      var link = $(ref);
      vrtxAdm.closeDropdowns();
      vrtxAdm.openDropdown(link, shortcutMenu);
    }, "clickOrEnter");
  }
};

/**
 * Open dropdown (slide down)
 *
 * @param {object} link The link that triggered the dropdown
 * @param {object} dropdown The element to be dropped down
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.openDropdown = function openDropdown(link, dropdown) {
  var vrtxAdm = this;
  var animation = new VrtxAnimation({
    elem: dropdown.not(":visible"),
    animationSpeed: vrtxAdmin.transitionDropdownSpeed,
    easeIn: "swing",
    after: function(animation) {
      var firstInteractiveElem = animation.__opts.elem.find("a, input[type='button'], input[type='submit']").filter(":first");
      if(firstInteractiveElem.length) firstInteractiveElem.focus();
      vrtxAdm.ariaDropdownState(link, animation.__opts.elem, true);
    }
  });
  animation.topDown();
};

/**
 * Close all dropdowns (slide up)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.closeDropdowns = function closeDropdowns() {
  var vrtxAdm = this;
  var animation = new VrtxAnimation({
    elem: vrtxAdm._$(".dropdown-shortcut-menu-container:visible"),
    animationSpeed: vrtxAdmin.transitionDropdownSpeed,
    easeIn: "swing",
    after: function(animation) {
      vrtxAdm.ariaDropdownState(vrtxAdm._$(".dropdown-shortcut-menu-click-area:visible"), animation.__opts.elem, false);
    }
  });
  animation.bottomUp();
};

/**
 * Hide tips (fade out)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.hideTips = function hideTips() {
  this._$(".tip:visible").fadeOut(this.transitionDropdownSpeed, "swing");
};


/**
 * Scroll breadcrumbs
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.initScrollBreadcrumbs = function initScrollBreadcrumbs() {
  var vrtxAdm = this;

  vrtxAdm.crumbsWrp = this._$("#vrtx-breadcrumb");
  vrtxAdm.crumbs = vrtxAdm.crumbsWrp.find(".vrtx-breadcrumb-level, .vrtx-breadcrumb-level-no-url");
  vrtxAdm.crumbs.wrapAll("<div id='vrtx-breadcrumb-outer'><div id='vrtx-breadcrumb-inner'></div></div>");
  vrtxAdm.crumbsInner = vrtxAdm.crumbsWrp.find("#vrtx-breadcrumb-inner");

  if(vrtxAdm.crumbsInner.width() < 900) { // Don't initialize breadcrumb menu if it is narrower than min-width
    vrtxAdm.crumbsActive = false;
    return;
  }
  vrtxAdm.crumbsActive = true;

  var navHtml = "<span id='navigate-crumbs-left-coverup' />" +
                "<a tabindex='0' id='navigate-crumbs-left' class='navigate-crumbs'><span class='navigate-crumbs-icon'></span><span class='navigate-crumbs-dividor'></span></a>" +
                "<a tabindex='0' id='navigate-crumbs-right' class='navigate-crumbs'><span class='navigate-crumbs-icon'></span><span class='navigate-crumbs-dividor'></span></a>";
  vrtxAdm.crumbsWrp.append(navHtml);

  vrtxAdm.crumbsLeft = vrtxAdm.crumbsWrp.find("#navigate-crumbs-left");
  vrtxAdm.crumbsLeftCoverUp = vrtxAdm.crumbsWrp.find("#navigate-crumbs-left-coverup");
  vrtxAdm.crumbsRight = vrtxAdm.crumbsWrp.find("#navigate-crumbs-right");

  var waitAWhile = setTimeout(function() {
    var i = vrtxAdm.crumbs.length, crumbsWidth = 0;
    while(i--) {
      var crumb = $(vrtxAdm.crumbs[i]);
      crumbsWidth += crumb.outerWidth(true) + 2;
      crumb.filter(":not(.vrtx-breadcrumb-active)").attr("tabindex", "0");
      crumb.find("a").attr("tabindex", "-1");
    }
    vrtxAdm.crumbsWidth = crumbsWidth;
    vrtxAdm.crumbsInner.css("width", crumbsWidth + "px");
    vrtxAdm.scrollBreadcrumbsRight();
    vrtxAdm.crumbsInner.addClass("animate");
  }, 120);

  eventListen(vrtxAdm.cachedDoc, "keypress", ".vrtx-breadcrumb-level", function (ref) {
    window.location.href = $(ref).find("a").attr("href");
  }, "clickOrEnter", 10);

  eventListen(vrtxAdm.cachedDoc, "click keypress", "#navigate-crumbs-left", function (ref) {
    vrtxAdmin.scrollBreadcrumbsLeft();
  }, "clickOrEnter");

  eventListen(vrtxAdm.cachedDoc, "click keypress", "#navigate-crumbs-right", function (ref) {
    vrtxAdmin.scrollBreadcrumbsRight();
  }, "clickOrEnter");

  /* TODO: replace with stacking of blue/hovered element above nav(?) */
  eventListen(vrtxAdm.cachedDoc, "mouseover mouseout", ".vrtx-breadcrumb-level", function(ref) {
    var hoveredBreadcrumb = $(ref);
    if(!hoveredBreadcrumb.hasClass("vrtx-breadcrumb-active")) {
      if(vrtxAdm.crumbsState === "left") {
        var gradientRight = vrtxAdm.crumbsRight;
        var gradientLeftEdge = gradientRight.offset().left;
        var crumbRightEdge = hoveredBreadcrumb.offset().left + hoveredBreadcrumb.width();
        if(crumbRightEdge > gradientLeftEdge) {
          gradientRight.find(".navigate-crumbs-dividor").toggle();
        }
      } else if(vrtxAdm.crumbsState === "right") {
        var gradientLeft = vrtxAdm.crumbsLeft;
        var gradientRightEdge = gradientLeft.offset().left + gradientLeft.width();
        var crumbLeftEdge = hoveredBreadcrumb.offset().left;
        if(crumbLeftEdge < gradientRightEdge) {
          gradientLeft.find(".navigate-crumbs-dividor").toggle();
        }
      }
    }
  });
};

VrtxAdmin.prototype.scrollBreadcrumbsLeft = function scrollBreadcrumbsLeft() {
  this.scrollBreadcrumbsHorizontal(false);
};

VrtxAdmin.prototype.scrollBreadcrumbsRight = function scrollBreadcrumbsRight() {
  this.scrollBreadcrumbsHorizontal(true);
};

VrtxAdmin.prototype.scrollBreadcrumbsHorizontal = function scrollBreadcrumbsHorizontal(isRight) {
  var vrtxAdm = this;
  if(!vrtxAdm.crumbsWidth) return;

  var width = $("#vrtx-breadcrumb").width();
  var diff = vrtxAdm.crumbsWidth - width;
  if(diff > 0) {
    if(isRight) {
      vrtxAdm.crumbsState = "right";
      vrtxAdm.crumbsInner.css("left", -diff + "px");
      vrtxAdm.crumbsRight.filter(":visible").hide();
      vrtxAdm.crumbsLeftCoverUp.filter(":hidden").show();
      vrtxAdm.crumbsLeft.filter(":hidden").show();
    } else {
      vrtxAdm.crumbsState = "left";
      vrtxAdm.crumbsInner.css("left", "0px");
      vrtxAdm.crumbsRight.filter(":hidden").show();
      vrtxAdm.crumbsLeftCoverUp.filter(":visible").hide();
      vrtxAdm.crumbsLeft.filter(":visible").hide();
    }
  } else {
    vrtxAdm.crumbsState = "off";
    if(isRight) vrtxAdm.crumbsInner.css("left", "0px");
    vrtxAdm.crumbsRight.filter(":visible").hide();
    vrtxAdm.crumbsLeftCoverUp.filter(":visible").hide();
    vrtxAdm.crumbsLeft.filter(":visible").hide();
  }
};

/*
 * Misc.
 *
 */

VrtxAdmin.prototype.initMiscAdjustments = function initMiscAdjustments() {
  var vrtxAdm = this;

  // Remove active tab if it has no children
  var activeTabChildren = vrtxAdm.cachedActiveTab.find("> *");
  if (!activeTabChildren.length) {
    vrtxAdm.cachedActiveTab.remove();
  } else {
    // Remove active tab-message if it is empty
    var activeTabMsg = activeTabChildren.filter(".tabMessage");
    if (activeTabMsg.length && !activeTabMsg.text().length) {
      activeTabMsg.remove();
    }
  }

  // Stop enter key on input on editor and collectionlisting + trashcan
  eventListen(vrtxAdmin.cachedAppContent, "keypress", "form#editor input, form[name='collectionListingForm'] input, form.trashcan input", function(ref) {}, "clickOrEnter");

  vrtxAdm.logoutButtonAsLink();
  vrtxAdm.adjustResourceTitle();

  // Ignore all AJAX errors when user navigate away (abort)
  if(typeof unsavedChangesInEditorMessage !== "function" && typeof unsavedChangesInPlaintextEditorMessage !== "function") {
    var ignoreAjaxErrorOnBeforeUnload = function() {
      vrtxAdm.ignoreAjaxErrors = true;
    };
    window.onbeforeunload = ignoreAjaxErrorOnBeforeUnload;
  }
};

/**
 * Adjust resource title across multiple lines
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.adjustResourceTitle = function adjustResourceTitle() {
  var resourceTitle = this._$("#resource-title");
  var resourceMenuLeft = resourceTitle.find("#resourceMenuLeft");
  if (resourceMenuLeft.length) {
    var title = resourceTitle.find("h1");
    var resourceMenuRight = resourceTitle.find("#resourceMenuRight");

    // Top adjust
    var titleHeight = title.outerHeight(true) - 34;
    var resourceMenuLeftHeight = resourceMenuLeft.outerHeight(true);
    resourceMenuRight.css("marginTop", -(resourceMenuLeftHeight + titleHeight) + "px");

    // Left adjust
    var w1 = title.outerWidth(true);
    var w2 = resourceMenuLeft.outerWidth(true);
    if(w1 > w2) {
      resourceMenuRight.css("marginLeft", ((w1 - w2) + 25) + "px");
    }
  }
};

// Map shortcut from a link to a button
VrtxAdmin.prototype.mapShortcut = function mapShortcut(selectors, reroutedSelector) {
  eventListen(this.cachedAppContent, "click", selectors, function(ref) {
    $(reroutedSelector).click();
  });
};

// Make logout button into a link
VrtxAdmin.prototype.logoutButtonAsLink = function logoutButtonAsLink() {
  var btn = $('input#logoutAction');
  if (!btn.length) return;

  btn.hide();
  btn.after('&nbsp;<a id=\"logoutAction.link\" name=\"logoutAction\" href="javascript:void(0);">' + btn.attr('value') + '</a>');
  eventListen($("#app-head-wrapper"), "click", "#logoutAction\\.link", function(ref) {
    btn.click();
  });
};


/**
 * Input update engine
 * @namespace
 */
VrtxAdmin.prototype.inputUpdateEngine = {
  /**
   * Update
   *
   * @this {inputSelectionEngine}
   */
  update: function(opts) {
    var currentCaretPos = this.getCaretPos(opts.input[0]);
    var before = opts.input.val();
    var after = this.substitute(before, opts.toLowerCase, opts.substitutions);
    opts.input.val(after);
    if(opts.afterUpdate) {
      opts.afterUpdate(after);
    }
    this.setCaretPos(opts.input[0], currentCaretPos - (before.length - after.length));
    return after;
  },
  /**
   * Set caret position
   *
   * Credits: http://stackoverflow.com/questions/499126/jquery-set-cursor-position-in-text-area (first)
   *
   * @this {inputSelectionEngine}
   */
  setCaretPos: function(input, pos) {
    var start = pos;
    var end = pos;
    if (input.createTextRange) {
      var selRange = input.createTextRange();
      selRange.collapse(true);
      selRange.moveStart("character", start);
      selRange.moveEnd("character", end);
      selRange.select();
    } else if (input.setSelectionRange) {
      input.setSelectionRange(start, end);
    } else {
      if (input.selectionStart) {
        input.selectionStart = start;
        input.selectionEnd = end;
      }
    }
    input.focus();
  },
  /**
   * Get caret position
   *
   * Credits: http://stackoverflow.com/questions/4928586/get-caret-position-in-html-input (fourth)
   *
   * @this {inputSelectionEngine}
   */
  getCaretPos: function(input) {
    if (input.setSelectionRange) {
      return input.selectionStart;
    } else if (document.selection && document.selection.createRange) {
      var range = document.selection.createRange();
      var bookmark = range.getBookmark();
      return bookmark.charCodeAt(2) - 2;
    }
  },
  /**
   * Substitute characters
   *
   * @this {inputSelectionEngine}
   */
  substitute: function(val, toLowerCase, substitutions) {
    if(toLowerCase) {
      val = val.toLowerCase();
    }
    if(typeof substitutions !== "object") {
      substitutions = this.substitutionsDefault;
    }
    for (var key in substitutions) {
      var replaceRegex = new RegExp(key, "g");
      val = val.replace(replaceRegex, substitutions[key]);
    }
    return val;
  },
  /**
   * Grow dynamically
   *
   * Based on: jQuery autoGrowInput plugin by James Padolsey:
   * http://stackoverflow.com/questions/931207/is-there-a-jquery-autogrow-plugin-for-text-fields
   *
   * @this {inputSelectionEngine}
   */
  grow: function(input, val, comfortZone, minWidth, maxWidth) {
    var testSubject = $('<tester/>').css({
      position: 'absolute',
      top: -9999,
      left: -9999,
      width: 'auto',
      fontSize: input.css('fontSize'),
      fontFamily: input.css('fontFamily'),
      fontWeight: input.css('fontWeight'),
      letterSpacing: input.css('letterSpacing'),
      whiteSpace: 'nowrap'
    });
    input.parent().find("tester").remove(); // Remove test-subjects
    testSubject.insertAfter(input);
    testSubject.html(val);
    var newWidth = Math.min(Math.max(testSubject.width() + comfortZone, minWidth), maxWidth);
    var currentWidth = input.width();
    if (newWidth !== currentWidth) {
      input.width(newWidth);
    }
  },
  /* Default substitutions */
  substitutionsDefault: {
    " ": "-",
    "&": "-",
    "'": "-",
    "\"": "-",
    "\\/": "-",
    "\\\\": "-",
    "æ": "e",
    "ø": "o",
    "å": "a",
    ",": "",
    "%": "",
    "#": "",
    "\\?": ""
  }
};


/*-------------------------------------------------------------------*\
    6. Async functions
\*-------------------------------------------------------------------*/

/**
 * Retrieve a form async
 *
 * TODO: this is ripe for some cleanup
 *
 * @this {VrtxAdmin}
 * @param {object} opts Configuration
 * @param {string} opts.selector Selector for links that should retrieve a form async
 * @param {string} opts.selectorClass Selector for form
 * @param {string} opts.insertAfterOrReplaceClass Where to put the form
 * @param {boolean} opts.isReplacing Whether to replace instead of insert after
 * @param {string} opts.nodeType Node type that should be replaced or inserted
 * @param {function} opts.funcBeforeComplete Callback function to run on before complete
 * @param {function} opts.funcComplete Callback function to run on success
 * @param {boolean} opts.simultanSliding Whether to slideUp existing form at the same time slideDown new form (only when there is an existing form)
 * @param {number} opts.transitionSpeed Transition speed in ms
 * @param {string} opts.transitionEasingSlideDown Transition easing algorithm for slideDown()
 * @param {string} opts.transitionEasingSlideUp Transition easing algorithm for slideUp()
 * @return {boolean} Proceed with regular link operation?
 */
VrtxAdmin.prototype.getFormAsync = function getFormAsync(opts) {
  var vrtxAdm = this, // use prototypal hierarchy
    _$ = vrtxAdm._$;

  vrtxAdm.cachedBody.dynClick(opts.selector, function (e) {
    if (vrtxAdm.asyncGetFormsInProgress) { // If there are any getFormAsync() in progress
      return false;
    }
    vrtxAdm.asyncGetFormsInProgress++;

    var link = _$(this),
        url = link.attr("href") || link.closest("form").attr("action"),
        modeUrl = window.location.href,
        isActionsListing = modeUrl.indexOf("&mode=actions-listing") !== -1,
        fromModeToNotMode = false,
        existExpandedFormIsReplaced = false,
        expandedForm = $(".expandedForm"),
        existExpandedForm = expandedForm.length;

    // Make sure we get the mode markup (current page) if service is not mode
    // -- only if a expandedForm exists and is of the replaced kind..
    //
    if (existExpandedForm && expandedForm.hasClass("expandedFormIsReplaced")) {
      if ((url.indexOf("&mode=") === -1 && modeUrl.indexOf("&mode=") !== -1) || isActionsListing) {
        fromModeToNotMode = true;
      }
      existExpandedFormIsReplaced = true;
    }

    if(opts.funcBeforeComplete) {
      opts.funcBeforeComplete(link);
    }

    vrtxAdmin.serverFacade.getHtml(url, {
      success: function (results, status, resp) {
        opts.formElm = _$(_$.parseHTML(results)).find("." + opts.selectorClass);
        var form = opts.formElm.html();

        // If something went wrong
        if (!form) {
          vrtxAdm.error({
            args: arguments,
            msg: "retrieved form from " + url + " is null"
          });
          if (vrtxAdm.asyncGetFormsInProgress) {
            vrtxAdm.asyncGetFormsInProgress--;
          }
          return;
        }
        // Another form is already open
        if (existExpandedForm) {
          var resultSelectorClasses = expandedForm.attr("class").split(" ");
          var resultSelectorClass = "";
          if(isActionsListing) {
            for (var i = resultSelectorClasses.length; i--;) {
              var resultSelectorClass = resultSelectorClasses[i];
              if(/^vrtx-/.test(resultSelectorClass)) {
                resultSelectorClass = "." + resultSelectorClass;
                break;
              }
            }
          } else {
            var ignoreClasses = { "even": "", "odd": "", "first": "", "last": "" };
            for (var i = resultSelectorClasses.length; i--;) {
              var resultSelectorClass = resultSelectorClasses[i];
              if (resultSelectorClass && resultSelectorClass !== "" && !(resultSelectorClass in ignoreClasses)) {
                resultSelectorClass = "." + resultSelectorClass;
                break;
              }
            }
          }
          var succeededAddedOriginalMarkup = false;
          var animation = new VrtxAnimation({
            elem: expandedForm,
            animationSpeed: opts.transitionSpeed,
            easeIn: opts.transitionEasingSlideDown,
            easeOut: opts.transitionEasingSlideUp,
            afterOut: function(animation) {
              if (existExpandedFormIsReplaced) {
                if (fromModeToNotMode) { // When we need the 'mode=' HTML when requesting a 'not mode=' service
                  vrtxAdmin.serverFacade.getHtml(modeUrl, {
                    success: function (results, status, resp) {
                      succeededAddedOriginalMarkup = vrtxAdm.addOriginalMarkup(modeUrl, _$.parseHTML(results), resultSelectorClass, expandedForm);
                      if (succeededAddedOriginalMarkup) {
                        vrtxAdmin.addNewMarkup(opts, form, link);
                      } else {
                        if (vrtxAdm.asyncGetFormsInProgress) {
                          vrtxAdmin.asyncGetFormsInProgress--;
                        }
                      }
                    },
                    error: function (xhr, textStatus) {
                      if (vrtxAdm.asyncGetFormsInProgress) {
                        vrtxAdm.asyncGetFormsInProgress--;
                      }
                    }
                  });
                } else {
                  succeededAddedOriginalMarkup = vrtxAdm.addOriginalMarkup(url, _$.parseHTML(results), resultSelectorClass, expandedForm);
                }
              } else {
                var node = animation.__opts.elem.parent().parent();
                if (node.is("tr") && vrtxAdm.animateTableRows) { // Because 'this' can be tr > td > div
                  node.remove();
                } else {
                  animation.__opts.elem.remove();
                }
              }
              if (!opts.simultanSliding && !fromModeToNotMode) {
                if (!succeededAddedOriginalMarkup) {
                  if (vrtxAdmin.asyncGetFormsInProgress) {
                    vrtxAdmin.asyncGetFormsInProgress--;
                  }
                } else {
                  vrtxAdmin.addNewMarkup(opts, form, link);
                }
              }
            }
          });
          animation.bottomUp();
        }
        if ((!existExpandedForm || opts.simultanSliding) && !fromModeToNotMode) {
          vrtxAdm.addNewMarkup(opts, form, link);
        }
      },
      error: function (xhr, textStatus) {
        if (vrtxAdm.asyncGetFormsInProgress) {
          vrtxAdm.asyncGetFormsInProgress--;
        }
      }
    });
    e.stopPropagation();
    e.preventDefault();
  });
};

/**
 * Add original form markup after async retrieve
 *
 * @this {VrtxAdmin}
 * @param {string} url The URL for original markup
 * @param {object} results The results
 * @param {string} resultSelectorClass Selector for original form markup
 * @param {object} expandedForm The expanded form
 * @return {boolean} Has it succeeded?
 */
VrtxAdmin.prototype.addOriginalMarkup = function addOriginalMarkup(url, results, resultSelectorClass, expandedForm) {
  var vrtxAdm = this;

  var resultHtml = vrtxAdm.outerHTML(results, resultSelectorClass);
  if (!resultHtml) { // If all went wrong
    vrtxAdm.error({
      args: arguments,
      msg: "trying to retrieve existing expandedForm from " + url + " returned null"
    });
    return false;
  }
  var node = expandedForm.parent().parent();
  if (node.is("tr") && vrtxAdm.animateTableRows) { // Because 'this' can be tr > td > div
    node.replaceWith(resultHtml).show(0);
  } else {
    expandedForm.replaceWith(resultHtml).show(0);
  }
  return true;
};

/**
 * Add new form markup after async retrieve
 *
 * @this {VrtxAdmin}
 * @param {object} opts Configuration
 * @param {string} selectorClass The selector for form
 * @param {string} transitionSpeed Transition speed in ms
 * @param {string} transitionEasingSlideDown Transition easing algorithm for slideDown()
 * @param {string} transitionEasingSlideUp Transition easing algorithm for slideUp()
 * @param {object} form The form
 */
VrtxAdmin.prototype.addNewMarkup = function addNewMarkup(opts, form, link) {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var inject = opts.findClosest ? link.closest(opts.insertAfterOrReplaceClass) : _$(opts.insertAfterOrReplaceClass);
  if (!inject.length) {
    inject = _$(opts.secondaryInsertAfterOrReplaceClass);
  }

  if (opts.isReplacing) {
    var classes = inject.attr("class");
    var isBadMarkup = opts.nodeType === "tr" && !opts.formElm.find("td").length && !opts.formElm.filter("td").length;
    inject.replaceWith(vrtxAdm.wrap(opts.nodeType, "expandedForm expandedFormIsReplaced nodeType" + opts.nodeType + " " + opts.selectorClass + " " + classes,
                                    (isBadMarkup ? "<td colspan='2'>" : "") + opts.formElm.html() + (isBadMarkup ? "</td>" : "")));
  } else {
    _$(vrtxAdm.wrap(opts.nodeType, "expandedForm nodeType" + opts.nodeType + " " + opts.selectorClass, form))
      .insertAfter(inject);
  }
  if (opts.funcComplete) {
    opts.funcComplete(opts.selectorClass);
  }
  if (vrtxAdm.asyncGetFormsInProgress) {
    vrtxAdm.asyncGetFormsInProgress--;
  }
  if (opts.nodeType === "tr" && vrtxAdm.animateTableRows) {
    _$(opts.nodeType + "." + opts.selectorClass).prepareTableRowForSliding();
  }

  var animation = new VrtxAnimation({
    elem: $(opts.nodeType + "." + opts.selectorClass).hide(),
    animationSpeed: opts.transitionSpeed,
    easeIn: opts.transitionEasingSlideDown,
    easeOut: opts.transitionEasingSlideUp,
    afterIn: function(animation) {
      if(opts.focusElement) {
        if(opts.focusElement !== "") {
          animation.__opts.elem.find(opts.focusElement).filter(":visible").filter(":first").focus();
        }
      } else {
        var inputs = animation.__opts.elem.find("textarea, input[type='text'], select").filter(":visible");
        if(inputs.length) {
          inputs.filter(":first")[0].focus();
        } else {
          input = animation.__opts.elem.find(".vrtx-focus-button, .vrtx-button, .vrtx-button-small").filter(":visible").filter(":first");
          if(input.length) {
            $("<a style='outline: none;' tabindex='-1' />").insertBefore(input)[0].focus();
          }
        }
      }
      if (opts.funcAfterComplete) {
        opts.funcAfterComplete();
      }
    }
  });
  animation.topDown();
};

/**
 * Complete a form async
 *
 * TODO: Maybe combine this function with completeSimpleFormAsync() (this as an expanded version of it for expanded slidable forms)
 *
 * @this {VrtxAdmin}
 * @param {object} opts Configuration
 * @param {string} opts.selector Selector for links that should complete a form async
 * @param {boolean} opts.isReplacing Whether to replace instead of insert after
 * @param {string} opts.updateSelectors One or more containers that should update after POST
 * @param {string} opts.errorContainerInsertAfter Selector where to place the new error container
 * @param {string} opts.errorContainer The className of the error container
 * @param {function} opts.funcProceedCondition Callback function that proceeds with completeFormAsyncPost(opts)
 * @param {function} opts.funcComplete Callback function to run on success
 * @param {function} opts.funcCancel Callback function to run on cancel
 * @param {number} opts.isNotAnimated Not animated form
 * @param {number} opts.transitionSpeed Transition speed in ms
 * @param {string} opts.transitionEasingSlideDown Transition easing algorithm for slideDown()
 * @param {string} opts.transitionEasingSlideUp Transition easing algorithm for slideUp()
 * @param {boolean} opts.post POST or only cancel
 * @return {boolean} Proceed with regular link operation?
 */
VrtxAdmin.prototype.completeFormAsync = function completeFormAsync(opts) {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  vrtxAdm.cachedBody.dynClick(opts.selector, function (e) {

    var link = _$(this),
        isCancelAction = link.attr("name").toLowerCase().indexOf("cancel") !== -1;

    if (isCancelAction && !opts.isReplacing) {
      var elem = $(".expandedForm");
      if(!opts.isNotAnimated) {
        var animation = new VrtxAnimation({
          elem: elem,
          animationSpeed: opts.transitionSpeed,
          easeIn: opts.transitionEasingSlideDown,
          easeOut: opts.transitionEasingSlideUp,
          afterOut: function(animation) {
            animation.__opts.elem.remove();
            if(opts.funcAfterComplete) {
              opts.funcAfterComplete()
            }
          }
        });
        animation.bottomUp();
      } else {
        elem.remove();
        if(opts.funcAfterComplete) {
          opts.funcAfterComplete()
        }
      }
      if(opts.funcCancel) opts.funcCancel();
      e.preventDefault();
    } else {
      if (!opts.post) {
        e.stopPropagation();
        if(!isCancelAction && opts.funcBeforeComplete) {
          opts.funcBeforeComplete();
        }
        if (opts.funcComplete && !isCancelAction) {
          return opts.funcComplete(link);
        } else {
          return;
        }
      } else {
        opts.form = link.closest("form");
        opts.link = link;
        opts.isCancelAction = isCancelAction;
        if (!isCancelAction && opts.funcProceedCondition) {
          opts.funcProceedCondition(opts);
        } else {
          vrtxAdm.completeFormAsyncPost(opts);
        }
        e.stopPropagation();
        e.preventDefault();
      }
    }
  });
};

/**
 * Complete a form async POST
 *
 * @this {VrtxAdmin}
 * @param {object} opts Configuration
 * @param {object} opts.form The form
 * @param {object} opts.link The action link
 * @param {object} opts.isCancelAction Is it a cancel?
 */
VrtxAdmin.prototype.completeFormAsyncPost = function completeFormAsyncPost(opts) {
  if(opts.funcBeforeComplete) opts.funcBeforeComplete();

  var vrtxAdm = vrtxAdmin,
       _$ = vrtxAdm._$,
      url = opts.form.attr("action"),
      modeUrl = window.location.href,
      isActionsListing = modeUrl.indexOf("&mode=actions-listing") !== -1,
      dataString = opts.form.serialize() + "&" + opts.link.attr("name");

  var postIt = function(results, status, resp) {
    var internalComplete = function(res) {
      if(opts.updateSelectors && res != null) {
        for (var i = opts.updateSelectors.length; i--;) {
          var outer = vrtxAdm.outerHTML(_$.parseHTML(res), opts.updateSelectors[i]);
          vrtxAdm.cachedBody.find(opts.updateSelectors[i]).replaceWith(outer);
        }
      }
      if (opts.funcComplete) opts.funcComplete();
    };
    var internalAnimation = function(elem, afterOut) {
      if(opts.isNotAnimated) return;
      var animation = new VrtxAnimation({
        elem: elem,
        animationSpeed: opts.transitionSpeed,
        easeIn: opts.transitionEasingSlideDown,
        easeOut: opts.transitionEasingSlideUp,
        afterOut: afterOut
      });
      animation.bottomUp();
    };

    if (opts.errorContainer && vrtxAdm.hasErrorContainers(_$.parseHTML(results), opts.errorContainer)) {
      var parsedHtml = _$.parseHTML(results);
      vrtxAdm.displayErrorContainers(parsedHtml, opts.form, opts.errorContainerInsertAfter, opts.errorContainer);
      if(opts.errorUpdateSelectors) {
        for (var i = opts.errorUpdateSelectors.length; i--;) {
          var outer = vrtxAdm.outerHTML(parsedHtml, opts.errorUpdateSelectors[i]);
          vrtxAdm.cachedBody.find(opts.errorUpdateSelectors[i]).replaceWith(outer);
        }
      }
    } else {
      if (opts.isReplacing) {
        internalAnimation(opts.form.parent(), function(animation) {
          internalComplete(results);
          if(opts.funcAfterComplete) {
            opts.funcAfterComplete();
          }
        });
      } else {
        var sameMode = false;
        var remoteIsMode = url.indexOf("&mode=") !== -1;
        var pageIsMode = modeUrl.indexOf("&mode=") !== -1;
        var remoteIsRevisions = url.indexOf("&action=revisions") !== -1;
        var pageIsRevisions = modeUrl.indexOf("&action=revisions") !== -1;
        if (remoteIsMode) {
          if (gup("mode", url) === gup("mode", modeUrl)) {
            sameMode = true;
          }
        }
        if (!opts.ignoreDifferentMode && (opts.isUndecoratedService || (pageIsMode && !sameMode) || (pageIsRevisions !== remoteIsRevisions))) { // When we need the 'mode=' or 'action=revisions' HTML. TODO: should only run when updateSelector is inside content
          vrtxAdm.serverFacade.getHtml(modeUrl, {
            success: function (results, status, resp) {
              internalComplete(results);
              internalAnimation(opts.form.parent(), function(animation) {
                animation.__opts.elem.remove();
                if(opts.funcAfterComplete) {
                  opts.funcAfterComplete();
                }
              });
            }
          });
        } else {
          internalComplete(results);
          internalAnimation(opts.form.parent(), function(animation) {
            animation.__opts.elem.remove();
            if(opts.funcAfterComplete) {
              opts.funcAfterComplete();
            }
          });
        }
      }
    }
  }

  // Avoid POST if cancel in actionslisting
  if(opts.isCancelAction && isActionsListing) {
    postIt(null, null, null);
  } else {
    vrtxAdmin.serverFacade.postHtml(url, dataString, {
      success: postIt
    });
  }
};

/**
 * Complete a simple form async
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuration
 * @param {string} options.selector Selector for links that should complete a form async
 * @param {boolean} options.useClickVal Clicked element value is included in the POST
 * @param {boolean} options.extraParams Extra parameters that should be included in the POST
 * @param {string} options.updateSelectors One or more containers that should update after POST
 * @param {string} options.errorContainer The className of the error container
 * @param {string} options.errorContainerInsertAfter Selector where to place the new error container
 * @param {function} options.fnBeforePost Callback function to run before POST
 * @param {function} options.fnComplete Callback function to run on success (after animations)
 * @param {function} options.fnCompleteInstant Callback function to run on success (instantly)
 */
VrtxAdmin.prototype.completeSimpleFormAsync = function completeSimpleFormAsync(opts) {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  vrtxAdm.cachedAppContent.on("click", opts.selector, function (e) {
    var link = _$(this);
    var form = link.closest("form");
    var url = form.attr("action");
    var startTime = new Date();
    var dataString = form.serialize() + "&" + encodeURIComponent(link.attr("name"));

    if(opts.useClickVal)  dataString += "=" + encodeURIComponent(link.val());
    if(opts.extraParams)  dataString += opts.extraParams;
    if(opts.fnBeforePost) {
      var retVal = opts.fnBeforePost(form, link);
      if(retVal === false) return;
    }
    vrtxAdm.serverFacade.postHtml(url, dataString, {
      success: function (results, status, resp) {
        var resultElm = _$($.parseHTML(results));
        if (opts.errorContainer && vrtxAdm.hasErrorContainers(resultElm, opts.errorContainer)) {
          if(opts.fnCompleteInstant) {
            opts.fnCompleteInstant(resultElm, form, url, link);
          }
          vrtxAdm.displayErrorContainers(resultElm, form, opts.errorContainerInsertAfter, opts.errorContainer);
        } else {
          var completion = function() {
            if(opts.fnCompleteInstant) {
              opts.fnCompleteInstant(resultElm, form, url, link);
            }
            var fnInternalComplete = function() {
              if(opts.updateSelectors) {
                for(var i = 0, len = opts.updateSelectors.length; i < len; i++) {
                  vrtxAdm.cachedAppContent.find(opts.updateSelectors[i]).html(resultElm.find(opts.updateSelectors[i]).html());
                }
              }
              if(opts.fnComplete) {
                opts.fnComplete(resultElm, form, url, link);
              }
            };
            if(vrtxAdm.animateTableRows && (opts.rowFromFormAnimateOut || opts.rowCheckedAnimateOut)) {
              var trs = opts.rowFromFormAnimateOut ? [form.closest("tr")] : form.find("tr")
                                                                                .filter(function(i) {
                                                                                  return $(this).find("td.checkbox input:checked").length; }
                                                                                );
              var futureAnims = [];
              for(var i = 0, len = trs.length; i < len; i++) {
                var tr = $(trs[i]);
                tr.prepareTableRowForSliding().hide(0).finish().slideDown(0, "linear", function() {
                  var animA = tr.find("td").finish().animate({
                      paddingTop: '0px',
                      paddingBottom: '0px'
                    },
                    vrtxAdm.transitionDropdownSpeed,
                    vrtxAdm.transitionEasingSlideUp
                  );
                  var animB = tr.slideUp(vrtxAdm.transitionDropdownSpeed, vrtxAdm.transitionEasingSlideUp);
                  futureAnims.push(animA);
                  futureAnims.push(animB);
                });
              }
              _$.when.apply(_$, futureAnims).done(fnInternalComplete);
            } else {
              fnInternalComplete();
            }
          };

          var endTime = new Date() - startTime;
          if (endTime >= (opts.minDelay || 0)) { // Wait minimum
            completion();
          } else {
            setTimeout(completion, Math.round(opts.minDelay - endTime));
          }
        }
      },
      error: function (xhr, textStatus) {
        if(opts.fnError) {
          opts.fnError(xhr, textStatus, form, url, link);
        }
      }
    });
    e.preventDefault();
  });
};

/**
 * Template Engine facade
 *
 * Uses Mustache
 *
 * @namespace
 */
VrtxAdmin.prototype.templateEngineFacade = {
  /**
   * Retrieve templates (splitted on ### => templateNames)
   *
   * @this {templateEngineFacade}
   * @param {string} fileName The filename for the Mustache file
   * @param {array} templateNames Templatenames
   * @param {object} templatesIsRetrieved Deferred / Future
   * @return {array} Templates with templateNames as hash
   */
  get: function (fileName, templateNames, templatesIsRetrieved) {
    var templatesHashArray = [];
    vrtxAdmin.serverFacade.getText(vrtxAdmin.rootUrl + "/js/templates/" + fileName + ".mustache", {
      success: function (results, status, resp) {
        var templates = results.split("###");
        for (var i = 0, len = templates.length; i < len; i++) {
          templatesHashArray[templateNames[i]] = $.trim(templates[i]);
        }
        templatesIsRetrieved.resolve();
      }
    });
    return templatesHashArray;
  },
  /**
   * Render template
   *
   * @this {templateEngineFacade}
   * @param {string} template The template
   * @param {object} args Arguments to render in template
   * @return {string} HTML
   */
  render: function(template, args) {
    return $.mustache(template, args);
  }
};


/*-------------------------------------------------------------------*\
    7. Async helper functions and AJAX server façade
\*-------------------------------------------------------------------*/

/**
 * Check if results has error container
 *
 * @this {VrtxAdmin}
 * @param {object} results The results
 * @param {string} errorContainer The className of the error container
 * @return {boolean} Whether it exists or not
 */
VrtxAdmin.prototype.hasErrorContainers = function hasErrorContainers(results, errorContainer) {
  return this._$(results).find("div." + errorContainer).length > 0;
};

/* TODO: support for multiple errorContainers
  (place the correct one in correct place (e.g. users and groups)) */
/**
 * Display error containers
 *
 * @this {VrtxAdmin}
 * @param {object} results The results
 * @param {string} form The open form
 * @param {string} errorContainerInsertAfter Selector where to place the new error container
 * @param {string} errorContainer The className of the error container
 */
VrtxAdmin.prototype.displayErrorContainers = function displayErrorContainers(results, form, errorContainerInsertAfter, errorContainer) {
  var wrapper = form.find(errorContainerInsertAfter).parent(),
      _$ = this._$;
  if (wrapper.find("div." + errorContainer).length) {
    wrapper.find("div." + errorContainer).html(_$(results).find("div." + errorContainer).html());
  } else {
    var outer = vrtxAdmin.outerHTML(results, "div." + errorContainer);
    _$(outer).attr("role", "alert").insertAfter(wrapper.find(errorContainerInsertAfter));
  }
};

VrtxAdmin.prototype.removeErrorContainers = function removeErrorContainers(form, errorContainerInsertAfter, errorContainer) {
  var wrapper = form.find(errorContainerInsertAfter).parent();
  if (wrapper.find("div." + errorContainer).length) {
    wrapper.find("div." + errorContainer).remove();
  }
};

/**
 * Display error message
 *
 * @this {VrtxAdmin}
 * @param {string} msg The message
 */
VrtxAdmin.prototype.displayErrorMsg = function displayErrorMsg(msg) {
  if (!this.ignoreAjaxErrors) {
    this.displayMsg(msg, "error");
  }
};

/**
 * Display info message
 *
 * @this {VrtxAdmin}
 * @param {string} msg The message
 */
VrtxAdmin.prototype.displayInfoMsg = function displayInfoMsg(msg) {
  this.displayMsg(msg, "info");
};

/**
 * Display message
 *
 * XXX: scrollTo top?
 *
 * @this {VrtxAdmin}
 * @param {string} msg The message
 * @param {string} type "info" or "error" message
 */
VrtxAdmin.prototype.displayMsg = function displayMsg(msg, type) {
  var current = (type === "info") ? "infomessage" : "errormessage";
  var other = (type === "info") ? "errormessage" : "infomessage";
  var role = (type === "info") ? "status" : "alert";

  var currentMsg = this.cachedAppContent.find("> ." + current);
  var otherMsg = this.cachedAppContent.find("> ." + other);
  if (typeof msg !== "undefined" && msg !== "") {
    if (currentMsg.length) {
      currentMsg.html(msg).fadeTo(100, 0.25).fadeTo(100, 1);
    } else if (otherMsg.length) {
      otherMsg.html(msg).removeClass(other).addClass(current).fadeTo(100, 0.25).fadeTo(100, 1);
      otherMsg.attr("role", role);
    } else {
      this.cachedAppContent.prepend("<div class='" + current + " message' role='" + role +"'>" + msg + "</div>");
    }
  } else {
    if (currentMsg.length) {
      currentMsg.remove();
    }
    if (otherMsg.length) {
      otherMsg.remove();
    }
  }
};

/**
 * Remove message
 *
 * @this {VrtxAdmin}
 * @param {string} type "info" or "error" message
 */
VrtxAdmin.prototype.removeMsg = function removeMsg(type) {
  var current = (type === "info") ? "infomessage" : "errormessage";
  var currentMsg = this.cachedAppContent.find("> ." + current);
  if (currentMsg.length) {
    currentMsg.remove();
  }
};

/**
 * Display error message in dialog
 *
 * @this {VrtxAdmin}
 * @param {object} selector The element where msg is inserted before
 * @param {string} msg The message
 */
VrtxAdmin.prototype.displayDialogErrorMsg = function displayDialogErrorMsg(selector, msg) {
  var msgWrp = $(".dialog-error-msg");
  if(!msgWrp.length) {
    $("<p class='dialog-error-msg' role='alert'>" + msg + "</p>").insertBefore(selector);
  } else {
    msgWrp.text(msg);
  }
};

/**
 * Server facade (Async=>Ajax)
 * @namespace
 */
VrtxAdmin.prototype.serverFacade = {
  /**
   * GET text
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {object} callbacks The callbacks
   */
  getText: function (url, callbacks) {
    this.get(url, callbacks, "text");
  },
  /**
   * GET HTML
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {object} callbacks The callback functions
   * @param {boolean} useCache Use cache in browser
   */
  getHtml: function (url, callbacks, useCache) {
    this.get(url, callbacks, "html", (typeof useCache !== "boolean" ? true : useCache));
  },
  /**
   * GET JSON
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {object} callbacks The callback functions
   * @param {boolean} useCache Use cache in browser
   */
  getJSON: function (url, callbacks, useCache) {
    this.get(url, callbacks, "json", (typeof useCache !== "boolean" ? true : useCache));
  },
  /**
   * POST HTML
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {string} params The data
   * @param {object} callbacks The callback functions
   */
  postHtml: function (url, params, callbacks) {
    this.post(url, params, callbacks, "html", "application/x-www-form-urlencoded;charset=UTF-8");
  },
  /**
   * POST JSON (text/plain)
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {string} params The data
   * @param {object} callbacks The callback functions
   */
  postJSON: function (url, params, callbacks) {
    this.post(url, params, callbacks, "json", "text/plain;charset=utf-8");
  },
  /**
   * HEAD Ajax
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {object} callbacks The callback functions
   */
  head: function(url, callbacks) {
    vrtxAdmin._$.ajax({
      type: "HEAD",
      async: true,
      url: url,
      cache: false,
      success: callbacks.success,
      error: function (xhr, textStatus) {
        if (callbacks.error) {
          callbacks.error(xhr, textStatus);
        }
      },
      complete: function (xhr, textStatus) {
        if (callbacks.complete) {
          callbacks.complete(xhr, textStatus);
        }
      }
    });
  },
  /**
   * GET Ajax <data type>
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {object} callbacks The callback functions
   * @param {string} type The data type
   * @param {boolean} useCache Use cache in browser
   */
  get: function (url, callbacks, type, useCache) {
    vrtxAdmin._$.ajax({
      type: "GET",
      url: url,
      dataType: type,
      cache: useCache,
      success: callbacks.success,
      error: function (xhr, textStatus) {
        var msg = vrtxAdmin.serverFacade.error(xhr, textStatus, true);
        if(msg === "RE_AUTH" && !vrtxAdmin.ignoreAjaxErrors) {
          reAuthenticateRetokenizeForms(false);
        } else {
          vrtxAdmin.displayErrorMsg(msg);
        }
        if (callbacks.error) {
          callbacks.error(xhr, textStatus);
        }
      },
      complete: function (xhr, textStatus) {
        if (callbacks.complete) {
          callbacks.complete(xhr, textStatus);
        }
      }
    });
  },
  /**
   * POST Ajax <data type>
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {string} params The data
   * @param {object} callbacks The callback functions
   * @param {string} type The data type
   * @param {string} contentType The content type
   */
  post: function (url, params, callbacks, type, contentType) {
    var opts = {
      type: "POST",
      url: url,
      data: params,
      dataType: type,
      contentType: contentType,
      success: callbacks.success,
      error: function (xhr, textStatus) {
        var msg = vrtxAdmin.serverFacade.error(xhr, textStatus, true);
        if(msg === "RE_AUTH" && !vrtxAdmin.ignoreAjaxErrors) {
          reAuthenticateRetokenizeForms(false);
        } else {
          vrtxAdmin.displayErrorMsg(msg);
        }
        if (callbacks.error) {
          callbacks.error(xhr, textStatus);
        }
      },
      complete: function (xhr, textStatus) {
        if (callbacks.complete) {
          callbacks.complete(xhr, textStatus);
        }
      }
    };
    vrtxAdmin._$.ajax(opts);
  },
  /**
   * Error Ajax handler
   *
   * TODO: More specific error-messages on what action that failed with function-origin
   *
   * @this {serverFacade}
   * @param {object} xhr The XMLHttpRequest object
   * @param {string} textStatus The text status
   * @param {boolean} useStatusCodeInMsg Use status code in error message?
   * @return {string} The messsage
   */
  error: function (xhr, textStatus, useStatusCodeInMsg) { // TODO: detect function origin
    var status = xhr.status;
    var msg = "";
    if (textStatus === "timeout") {
      msg = this.errorMessages.timeout;
    } else if (textStatus === "abort") {
      msg = this.errorMessages.abort;
    } else if (textStatus === "parsererror") {
      msg = this.errorMessages.parsererror;
    } else if (status === 0) {
      msg = this.errorCheckNeedReauthenticationOrOffline();
    } else if (status === 503 || (xhr.readyState === 4 && status === 200)) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.down;
    } else if (status === 500) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s500;
    } else if (status === 400) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s400;
    } else if (status === 401) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s401;
    } else if (status === 403) { // Handle server down => up
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s403;
    } else if (status === 404) {
      msg = this.errorCheckLockedOr404(status, useStatusCodeInMsg);
    } else if (status === 4233) { // Parent locked
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s4233;
    } else {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.general + " " + textStatus;
    }
    return msg;
  },
  errorCheckNeedReauthenticationOrOffline: function () {
    var serverFacade = this, msg = "";
    vrtxAdmin._$.ajax({
      type: "GET",
      url: vrtxAdmin.rootUrl + "/themes/default/images/globe.png?" + (+new Date()),
      async: false,
      success: function (results, status, resp) { // Re-authentication needed - Online
        msg = "RE_AUTH";
      },
      error: function (xhr, textStatus) {         // Offline
        msg = serverFacade.errorMessages.offline;
      }
    });
    return msg;
  },
  errorCheckLockedOr404: function (originalStatus, useStatusCodeInMsg) {
    var serverFacade = this, msg = "";
    vrtxAdmin._$.ajax({
      type: "GET",
      url: window.location.href,
      async: false,
      success: function (results, status, resp) { // Exists - Locked
        msg = useStatusCodeInMsg ? status + " - " + serverFacade.errorMessages.s423 : "LOCKED";
        results = $($.parseHTML(results));
        vrtxAdmin.lockedBy = results.find("#resource-locked-by").html();
        $("#resourceMenuRight").html(results.find("#resourceMenuRight").html());
        vrtxAdmin.globalAsyncComplete();
      },
      error: function (xhr, textStatus) { // 404 - Remove/moved/renamed
        msg = (useStatusCodeInMsg ? originalStatus + " - " : "") + serverFacade.errorMessages.s404;
      }
    });
    return msg;
  },
  errorMessages: {} /* Populated with i18n in resource-bar.ftl */
};


/*-------------------------------------------------------------------*\
    8. Popups and CK browse server integration
\*-------------------------------------------------------------------*/

// XXX: don't pollute global namespace
var urlobj;
var typestr;

function browseServer(obj, editorBase, baseFolder, editorBrowseUrl, type) {
  urlobj = obj; // NB: store to global vars
  if (!type) type = 'Image';
  typestr = type;

  // Use 70% of screen dimension
  var serverBrowserWindow = openServerBrowser(editorBase + '/plugins/filemanager/browser/default/browser.html?BaseFolder=' + baseFolder + '&Type=' + type + '&Connector=' + editorBrowseUrl,
                                              screen.width * 0.7, screen.height * 0.7);

  serverBrowserWindow.focus();
  /* TODO: Refocus when user closes window with [x] and tries to open it again via browse..
   *       Maybe with a timer: http://en.allexperts.com/q/Javascript-1520/set-window-top-working.htm
   */
}

function openServerBrowser(url, width, height) {
  return openPopup(url, width, height, "BrowseServer");
}

function openRegular(url, width, height, winTitle) {
  var sOptions = "toolbar=yes,status=yes,resizable=yes";
  sOptions += ",location=yes,menubar=yes,scrollbars=yes";
  sOptions += ",directories=yes";
  var now = +new Date();
  return openGeneral(url, width, height, winTitle + now, sOptions);
}

function openPopupScrollable(url, width, height, winTitle) {
  var sOptions = "toolbar=no,status=no,resizable=yes,scrollbars=yes";
  return openGeneral(url, width, height, winTitle, sOptions);
}

function openPopup(url, width, height, winTitle) {
  var sOptions = "toolbar=no,status=no,resizable=yes";
  return openGeneral(url, width, height, winTitle, sOptions);
}

// sOptions: http://www.quirksmode.org/js/popup.html
function openGeneral(url, width, height, winTitle, sOptions) { // title must be without spaces in IE
  var iLeft = (screen.width - width) / 2;
  var iTop = (screen.height - height) / 2;
  sOptions += ",width=" + width;
  sOptions += ",height=" + height;
  sOptions += ",left=" + iLeft;
  sOptions += ",top=" + iTop;
  var oWindow = window.open(url, winTitle, sOptions);
  return oWindow;
}

// Show message on admin popup close in admin
$(window).on("message", function(e) {
  window.focus();
  var data = e.originalEvent.data;
  if(typeof data === "string" && data === "displaymsg") {
    var d = new VrtxMsgDialog({
      msg: vrtxAdmin.messages.courseSchedule.updated,
      title: vrtxAdmin.messages.courseSchedule.updatedTitle,
      width: 400
    });
    d.open();
  }
});
function refreshParent() {
  window.opener.postMessage("displaymsg", "*");
}
if(gup("displaymsg", window.location.href) === "yes") {
  window.onunload = refreshParent;
}

// Callback from the image browser:
function SetUrl(url) {
  url = decodeURIComponent(url);
  if (urlobj) {
    var elem = document.getElementById(urlobj);
    elem.value = url;
    elem.focus();
  }
  oWindow = null;
  if (typestr === "Image" && typeof previewImage !== "undefined") {
    previewImage(urlobj);
  }
  urlobj = ""; // NB: reset global vars
  typestr = "";
}


/*-------------------------------------------------------------------*\
    9. Utils
\*-------------------------------------------------------------------*/

/**
 * ARIA-boolean
 *
 * @this {VrtxAdmin}
 * @param {string} ariaAttr The ARIA postfix
 * @param {string} idOrElm The id or jQElement
 * @param {boolean} isTrue True or false
 */
VrtxAdmin.prototype.ariaBool = function ariaBool(ariaAttr, idOrElm, isTrue) {
  var elm = (typeof idOrElm === "string") ? $(idOrElm) : idOrElm;
  elm.attr("aria-" + ariaAttr, isTrue ? "true" : "false");
};

/**
 * ARIA-value
 *
 * @this {VrtxAdmin}
 * @param {string} ariaAttr The ARIA postfix
 * @param {string} idOrElm The id or jQElement
 * @param {string} value Value to set
 */
VrtxAdmin.prototype.ariaVal = function ariaVal(ariaAttr, idOrElm, value) {
  var elm = (typeof idOrElm === "string") ? $(idOrElm) : idOrElm;
  elm.attr("aria-" + ariaAttr, value);
};

/**
 * ARIA-busy (for loading regions)
 *
 * @this {VrtxAdmin}
 * @param {string} idOrElm The id or jQElement
 * @param {boolean} isBusy If the id or element is busy
 */
VrtxAdmin.prototype.ariaBusy = function ariaBusy(idOrElm, isBusy) {
  this.ariaBool("busy", idOrElm, isBusy);
};

/**
 * ARIA-dropdown
 *
 * @this {VrtxAdmin}
 * @param {string} idOrElmLink The id or jQElement
 * @param {string} idOrElmDropdown The id or jQElement
 * @param {boolean} isExpanded If the id or element is expanded
 */
VrtxAdmin.prototype.ariaDropdownState = function ariaDropdownState(idOrElmLink, idOrElmDropdown, isExpanded) {
  this.ariaBool("expanded", idOrElmLink, isExpanded);
  this.ariaHidden(idOrElmDropdown, !isExpanded);
};

/**
 * ARIA-hidden (for hidden regions)
 *
 * @this {VrtxAdmin}
 * @param {string} idOrElm The id or jQElement
 * @param {boolean} isHidden If the id or element is hidden
 */
VrtxAdmin.prototype.ariaHidden = function ariaHidden(idOrElm, isHidden) {
  this.ariaBool("hidden", idOrElm, isHidden);
};

/**
 * Wrap HTML in node
 *
 * @this {VrtxAdmin}
 * @param {string} node The node type
 * @param {string} cls The className(s)
 * @param {string} html The node HTML
 * @return {string} HTML node
 */
VrtxAdmin.prototype.wrap = function wrap(node, cls, html) {
  return this._$.parseHTML("<" + node + " class='" + cls + "'>" + html + "</" + node + ">");
};

/**
 * jQuery outerHTML (because FF <11 don't support regular outerHTML)
 *
 * @this {VrtxAdmin}
 * @param {string} selector Context selector
 * @param {string} subselector The node to get outer HTML from
 * @return {string} outer HTML
 */
VrtxAdmin.prototype.outerHTML = function outerHTML(selector, subselector) {
  var _$ = this._$;

  var wrp = _$(selector);
  var elm = wrp.find(subselector);
  if (elm.length) {
    if (typeof elm[0].outerHTML !== "undefined") {
      return _$.parseHTML(elm[0].outerHTML);
    } else {
      return _$.parseHTML(_$('<div>').append(elm.clone()).html());
    }
  }
};

/**
 * Load multiple scripts Async / lazy-loading
 *
 * @this {VrtxAdmin}
 * @param {string} urls The urls to the script
 * @param {function} deferred Future that will be resolved when all scripts are loaded
 */
VrtxAdmin.prototype.loadScripts = function loadScripts(urls, deferred) {
  var futureScripts = [];
  for(var i = 0, len = urls.length; i < len; i++) {
    futureScripts.push(this.loadScript(this.rootUrl + urls[i]));
  }
  $.when.apply($, futureScripts).done(function () {
    deferred.resolve();
  });
};

/**
 * Load script Async / lazy-loading
 *
 * @this {VrtxAdmin}
 * @param {string} url The url to the script
 * @param {function} callback Callback function to run on success
 */
VrtxAdmin.prototype.loadScript = function loadScript(url, callback) {
  return $.cachedScript(url).done(callback).fail(function (jqxhr, settings, exception) {
    vrtxAdmin.log({
      msg: exception
    });
  });
};

/**
 * Log to console with function name if exists
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuraton
 * @param {string} options.msg The message
 * @param {array} options.args Arguments
 */
VrtxAdmin.prototype.log = function log(options) {
  if (vrtxAdmin.hasConsoleLog) {
    var fn = [];
    /*
    if(options.args) {
      fn = options.args.callee.toString().match(/function\s+([^\s\(]+)/);
    }
    */
    var msgMid = (fn.length > 1) ? " -> " + fn[1] : "";
    console.log("Vortex admin log" + msgMid + ": " + options.msg);
  }
};

/**
 * Error to console with function name if exists with fallback to regular log
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuraton
 * @param {string} options.msg The message
 * @param {array} options.args Arguments
 */
VrtxAdmin.prototype.error = function error(options) {
  if (vrtxAdmin.hasConsoleError) {
    var fn = [];
    /*
    if(options.args) {
      fn = options.args.callee.toString().match(/function\s+([^\s\(]+)/);
    }
    */
    var msgMid = (fn.length > 1) ? " -> " + fn[1] : "";
    console.error("Vortex admin error" + msgMid + ": " + options.msg);
  } else {
    this.log(options);
  }
};

/**
 * Get UNIX-time (more accurate if available)
 *
 * @param {boolean} useNsRes Use nanoseconds resolution if available (or just add three zeroes if not)
 * @return {number} Unix-time
 */
function getNowTime(useNsRes) {
  if(window.performance && typeof window.performance.now === "function") {
    var now = window.performance.now();
  } else {
    var now = +new Date();
  }
  if(typeof useNsRes === "boolean" && useNsRes) {
    now *= 1000;
  }
  return Math.round(now); // performance.now() provides even higher resolution than ns
}

/* Get URL parameter
 *
 * Credits: http://www.netlobo.com/url_query_string_javascript.html
 *
 * Modified slightly
 */
function gup(name, url) {
  name = name.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");
  var regexS = "[\\?&]" + name + "=([^&#]*)";
  var regex = new RegExp(regexS);
  var results = regex.exec(url);
  return (results === null) ? "" : results[1];
}

/* Remove duplicates from an array
 *
 * Credits: http://www.shamasis.net/2009/09/fast-algorithm-to-find-unique-items-in-javascript-array/
 */
function unique(array) {
  var o = {}, i, l = array.length, r = [];
  for (i = 0; i < l; i += 1) o[array[i]] = array[i];
  for (i in o) r.push(o[i]);
  return r;
}

/*
 * Check keyboard input
 */
function isKey(e, keyCodes) {
  for(var i = keyCodes.length; i--;) {
    if((e.which && e.which === keyCodes[i]) || (e.keyCode && e.keyCode === keyCodes[i])) return true;
  }
}

/**
 * Setup listener for events with handler a function (duplicate outer handler function for perf.)
 *
 * @param {object}  listenBase Base element the events bubbles up to (jQElement)
 * @param {string}  eventType Type of events
 * @param {object}  listenOn Elements should listen on (jQElement's)
 * @param {object}  handlerFn Handler function
 * @param {string}  handlerFnCheck If should proceed with handler function (event conditions)
 * @param {number}  debounceInterval Debounce the events by some milliseconds
 * @param {boolean} dontPreventDefault Don't prevent default event action
 */
function eventListen(listenBase, eventType, listenOn, handlerFn, handlerFnCheck, debounceInterval, dontPreventDefault) {
  // DEBUG: vrtxAdmin.log({ msg: "Listen for events of type " + eventType.toUpperCase() + " on " + listenOn });
  if(typeof debounceInterval === "number") {
    listenBase.on(eventType, listenOn, $.debounce(debounceInterval, true, function (e) {
      if(typeof handlerFnCheck !== "string"
            || (handlerFnCheck === "clickOrEnter" && (e.type === "click" || isKey(e, [vrtxAdmin.keys.ENTER])))) {
        handlerFn(this);
        // DEBUG: vrtxAdmin.log({ msg: (e.type.toUpperCase() + " for " + (this.id || this.className || this.nodeType)) })
        if(typeof dontPreventDefault !== "boolean" || !dontPreventDefault) {
          e.preventDefault();
        }
      }
    }));
  } else {
    listenBase.on(eventType, listenOn, function (e) {
      if(typeof handlerFnCheck !== "string"
            || (handlerFnCheck === "clickOrEnter" && (e.type === "click" || isKey(e, [vrtxAdmin.keys.ENTER])))) {
        handlerFn(this);
        // DEBUG: vrtxAdmin.log({ msg: (e.type.toUpperCase() + " for " + (this.id || this.className || this.nodeType)) })
        e.stopPropagation();
        if(typeof dontPreventDefault !== "boolean" || !dontPreventDefault) {
          e.preventDefault();
        }
      }
    });
  }
}


/*-------------------------------------------------------------------*\
    10. Override JavaScript / jQuery
\*-------------------------------------------------------------------*/

/*  Override slideUp() / slideDown() to animate rows in a table
 *
 *  Credits:
 *  o http://stackoverflow.com/questions/467336/jquery-how-to-use-slidedown-or-show-function-on-a-table-row/920480#920480
 *  o http://www.bennadel.com/blog/1624-Ask-Ben-Overriding-Core-jQuery-Methods.htm
 */

if (vrtxAdmin.animateTableRows) {
  jQuery.fn.prepareTableRowForSliding = function () {
    var tr = this;
    tr.children('td').wrapInner('<div style="display: none;" />');
    return tr;
  };

  var originalSlideUp = jQuery.fn.slideUp;
  jQuery.fn.slideUp = function (speed, easing, callback) {
    var trOrOtherElm = this;
    if (trOrOtherElm.is("tr")) {
      trOrOtherElm.find('td > div').animate({
        height: 'toggle'
      }, speed, easing, callback);
    } else {
      originalSlideUp.apply(trOrOtherElm, arguments);
    }
  };

  var originalSlideDown = jQuery.fn.slideDown;
  jQuery.fn.slideDown = function (speed, easing, callback) {
    var trOrOtherElm = this;
    if (trOrOtherElm.is("tr") && trOrOtherElm.css("display") === "none") {
      trOrOtherElm.show().find('td > div').animate({
        height: 'toggle'
      }, speed, easing, callback);
    } else {
      originalSlideDown.apply(trOrOtherElm, arguments);
    }
  };
}

jQuery.cachedScript = function (url, options) {
  options = $.extend(options || {}, {
    dataType: "script",
    cache: true,
    url: url
  });
  return jQuery.ajax(options);
};

jQuery.loadCSS = function(url) {
  var ss = document.styleSheets;
  for (var i = 0, len = ss.length; i < len; i++) {
    if (ss[i].href === url) return;
  }
  if (document.createStyleSheet) {
    document.createStyleSheet(url);
  } else {
    $('<link rel="stylesheet" type="text/css" href="' + url + '" />').appendTo('head');
  }
};

/* A little faster dynamic click handler */
jQuery.fn.extend({
  dynClick: function (selector, fn) {
    var nodes = $(this);
    for (var i = nodes.length; i--;) {
      jQuery.event.add(nodes[i], "click", fn, undefined, selector);
    }
  }
});

/*
 * jQuery throttle / debounce - v1.1 - 3/7/2010
 * http://benalman.com/projects/jquery-throttle-debounce-plugin/
 *
 * Copyright (c) 2010 "Cowboy" Ben Alman
 * Dual licensed under the MIT and GPL licenses.
 * http://benalman.com/about/license/
 */
(function(b,c){var $=b.jQuery||b.Cowboy||(b.Cowboy={}),a;$.throttle=a=function(e,f,j,i){var h,d=0;if(typeof f!=="boolean"){i=j;j=f;f=c}function g(){var o=this,m=+new Date()-d,n=arguments;function l(){d=+new Date();j.apply(o,n)}function k(){h=c}if(i&&!h){l()}h&&clearTimeout(h);if(i===c&&m>e){l()}else{if(f!==true){h=setTimeout(i?k:l,i===c?e-m:e)}}}if($.guid){g.guid=j.guid=j.guid||$.guid++}return g};$.debounce=function(d,e,f){return f===c?a(d,e,false):a(d,f,e!==false)}})(this);

var countResize = 0;
vrtxAdmin._$(window).on("resize", vrtxAdmin._$.debounce(vrtxAdmin.windowResizeScrollDebounceRate, function () {
  if (vrtxAdmin.runReadyLoad && countResize < 3) { // Only use this extra fix for iOS 5 and IE8?
    countResize++;
    resizeOrientationChangeWindowHandler();
  } else { // Let it rest a second..
    var waitResize = setTimeout(function() {
      countResize = 0;
    }, 1000);
  }
}));

/* Dialogs - usability/accessability */

if(vrtxAdmin.isTouchDevice) {
  vrtxAdmin._$(window).on("scroll orientationchange",
    vrtxAdmin._$.debounce(vrtxAdmin.windowResizeScrollDebounceRate, repositionDialogsTouchDevices)
  );
}

function resizeOrientationChangeWindowHandler() {
  if(!vrtxAdmin.isTouchDevice) {
    var jqDialog = $(".ui-dialog-content").filter(":visible");
    if(jqDialog.length === 1) { // If more than one box: ignore (should not happen)
      jqDialog.dialog("option", "position", "center");
    }
  } else {
    repositionDialogsTouchDevices();
  }
  if(vrtxAdmin.crumbsActive) {
    vrtxAdmin.scrollBreadcrumbsRight();
  }
  vrtxAdmin.adjustResourceTitle();
}

function repositionDialogsTouchDevices() {
  var jqCkDialog = $(".ui-dialog, table.cke_dialog").filter(":visible");
  if(jqCkDialog.length === 1) {
    var winInnerWidth = window.innerWidth;
    var winInnerHeight = window.innerHeight;
    var boxWidth = jqCkDialog.outerWidth();
    var boxHeight = jqCkDialog.outerHeight();
    if(boxWidth < winInnerWidth && boxHeight < winInnerHeight) { // Otherwise scroll is in effect
      var left = ((winInnerWidth / 2) + window.pageXOffset) - (boxWidth / 2);
      var top = ((winInnerHeight / 2) + window.pageYOffset) - (boxHeight / 2);
      jqCkDialog.css({ "position": "absolute", "top": top + "px", "left": left + "px" });
    }
  }
}

/* Easing
 *
 * TODO: Move to VrtxAnimation when slide and rotate animations (forms and preview) becomes part of it
 * TODO: Check if jQuery UI is available
 */
(function() {
 // based on easing equations from Robert Penner (http://www.robertpenner.com/easing)
 var baseEasings = {};
 $.each( [ "Quad", "Cubic", "Quart", "Quint", "Expo" ], function( i, name ) {
   baseEasings[ name ] = function( p ) {
     return Math.pow( p, i + 2 );
   };
 });
 $.each( baseEasings, function( name, easeIn ) {
   $.easing[ "easeIn" + name ] = easeIn;
   $.easing[ "easeOut" + name ] = function( p ) {
     return 1 - easeIn( 1 - p );
   };
   $.easing[ "easeInOut" + name ] = function( p ) {
     return p < 0.5 ?
       easeIn( p * 2 ) / 2 :
       1 - easeIn( p * -2 + 2 ) / 2;
   };
 });
})();

/* ^ Vortex Admin enhancements */
