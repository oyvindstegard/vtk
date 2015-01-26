/*
 *  Vortex Admin enhancements
 *
 *  ToC: 
 *
 *  1.  Config
 *  2.  DOM is ready
 *  3.  DOM is fully loaded
 *  4.  General / setup interactions
 *  5.  Create / Upload / Copy / Move
 *  6.  Collectionlisting
 *  7.  Editor
 *  8.  Permissions
 *  9.  Async functions
 *  10. Async helper functions and AJAX server façade
 *  11. Popups and CK browse server integration
 *  12. Utils
 *  13. Override JavaScript / jQuery
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
  /* Mobile - iOS and Android */
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

  var futureAppliedFn = function(calleeStr, args) {
    var fn = calleeStr.match(/function\s+([^\s\(]+)/); if(fn.length > 1) { objApplied.push({fn: fn[1], args: args}); }
  };
  
  /* Add applied functions for future running.
   * TODO: general object prop access handling possible? http://blog.calyptus.eu/seb/2010/11/javascript-proxies-leaky-this/?
   * TODO: Firefox can use Proxy and get() for adding future applied functions(?) http://wiki.ecmascript.org/doku.php?id=harmony%3adirect_proxies
   */
  obj.update = function update(opts)         { futureAppliedFn(arguments.callee.toString(), opts); };
  obj.updateElem = function updateElem(elem) { futureAppliedFn(arguments.callee.toString(), elem); };
  obj.rightIn = function rightIn()           { futureAppliedFn(arguments.callee.toString(), null); };
  obj.leftOut = function leftOut()           { futureAppliedFn(arguments.callee.toString(), null); };
  obj.topDown = function topDown()           { futureAppliedFn(arguments.callee.toString(), null); };
  obj.bottomUp = function bottomUp()         { futureAppliedFn(arguments.callee.toString(), null); };
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
  if(vrtxAdm.isIE8) vrtxAdm.cachedBody.addClass("ie8");
  
  vrtxAdm.embeddedView();
  
  // Show message in IE6, IE7 and IETrident in compability mode
  if ((vrtxAdm.isIE7 || vrtxAdm.isIETridentInComp) && typeof outdatedBrowserText === "string") {
    var message = vrtxAdm.cachedAppContent.find(" > .message");
    if (message.length) {
      message.html(outdatedBrowserText);
    } else {
      if(vrtxAdm.bodyId === "vrtx-simple-editor" || isEmbedded) {
        vrtxAdm.cachedBody.prepend("<div class='infomessage'>" + outdatedBrowserText + "</div>");
      } else {
        vrtxAdm.cachedAppContent.prepend("<div class='infomessage'>" + outdatedBrowserText + "</div>");
      }
    }
  }
   // Return if should not run all of ready() code
  if (vrtxAdm.runReadyLoad === false) return;

  // Load required init components (animations and trees)
  vrtxAdm.requiredScriptsLoaded = $.Deferred();
  vrtxAdm.loadScripts(["/js/vrtx-animation.js", "/js/vrtx-tree.js"], vrtxAdm.requiredScriptsLoaded);
  vrtxAdm.clientLastModified = $("#resource-last-modified").text().split(",");
  
  if(!isEmbedded && !isEmbedded2) {
    vrtxAdm.initDropdowns();
    vrtxAdm.initScrollBreadcrumbs();
  }
  vrtxAdm.initDomainsInstant();
  
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
    containerWidth: 320, xOffset: 20, yOffset: -15
  });
  this.cachedBody.vortexTips(".ui-dialog:visible .tree-create li span.folder", {
    appendTo: ".vrtx-create-tree",
    containerWidth: 80, xOffset: 10, yOffset: -8,
    expandHoverToTipBox: true,
    enterOpens: true,
    extra: true
  });
  this.cachedBody.vortexTips("td.permissions span.permission-tooltips", {
    appendTo: "#contents",
    containerWidth: 340, xOffset: 10, yOffset: -8,
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
    vrtxAdm.completeFormAsync({
      selector: "form#" + resourceMenuLeftServices[i] + "-form input[type=submit]"
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
        var publishSaveResource = vrtxAdm.lang === "en" ? "Are you sure you want to save and publish?"
                                                        : "Er du sikker på at du vil lagre og publisere?";
        $("#vrtx-publish-document-form h3").text(publishSaveResource);
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
            updateClientLastModifiedAlreadyRetrieved();
            vrtxAdm.globalAsyncComplete();
          }
        });
        return false;
      } : function(link) {
        updateClientLastModifiedAlreadyRetrieved();
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
    vrtxAdm.serverFacade.getHtml(link.href + "&4", {
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
      updateClientLastModifiedAlreadyRetrieved();
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
  vrtxAdm.updateCollectionListingInteraction();
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
 * Domains init
 *
 * TODO: Too big and complex (HV: ?, CC: ?)
 *
 * * is based on id of body-tag
 *
 */
 
VrtxAdmin.prototype.initDomains = function initDomains() {
  var vrtxAdm = this,
      bodyId = vrtxAdm.bodyId;
      _$ = vrtxAdm._$;
      
  switch (bodyId) {
    case "vrtx-manage-collectionlisting":
      var tabMenuServices = ["fileUploadService", "createDocumentService", "createCollectionService"];
      var speedCreationServices = vrtxAdm.isIE8 ? 0 : 350;
      for (i = tabMenuServices.length; i--;) {
          if (tabMenuServices[i] !== "fileUploadService") {
            vrtxAdm.getFormAsync({
              selector: "ul#tabMenuRight a#" + tabMenuServices[i],
              selectorClass: "vrtx-admin-form",
              insertAfterOrReplaceClass: "#active-tab ul#tabMenuRight",
              nodeType: "div",
              focusElement: "input[type='text']",
              funcComplete: function (p) {
                createFuncComplete();
              },
              simultanSliding: true,
              transitionSpeed: speedCreationServices
            });
            vrtxAdm.completeFormAsync({
              selector: "form#" + tabMenuServices[i] + "-form input[type=submit]",
              transitionSpeed: speedCreationServices,
              funcBeforeComplete: function () {
                createTitleChange("#vrtx-textfield-collection-title", $("#vrtx-textfield-collection-name"), $("#isIndex"));
                createTitleChange("#vrtx-textfield-file-title", $("#vrtx-textfield-file-name"), $("#isIndex"));
              }
            });
          } else {
            if (vrtxAdm.isIOS5) { // TODO: feature detection
              _$("ul#tabMenuRight li." + tabMenuServices[i]).remove();
            } else {
              vrtxAdm.getFormAsync({
                selector: "ul#tabMenuRight a#" + tabMenuServices[i],
                selectorClass: "vrtx-admin-form",
                insertAfterOrReplaceClass: "#active-tab ul#tabMenuRight",
                nodeType: "div",
                focusElement: "",
                funcComplete: function (p) {
                  vrtxAdm.initFileUpload();
                },
                simultanSliding: true
              });
              vrtxAdm.completeFormAsync({
                selector: "form#" + tabMenuServices[i] + "-form input[type=submit]",
                updateSelectors: ["#contents"],
                errorContainer: "errorContainer",
                errorContainerInsertAfter: "h3",
                post: true,
                funcProceedCondition: ajaxUpload
              });
              vrtxAdm.initFileUpload(); // when error message
            }
          }
      }
      
      tabMenuServices = ["collectionListing\\.action\\.move-resources", "collectionListing\\.action\\.copy-resources"];
      resourceMenuServices = ["moveToSelectedFolderService", "copyToSelectedFolderService"];
      // TODO: This map/lookup-obj is a little hacky..
      tabMenuServicesInjectMap = {
        "collectionListing.action.move-resources": "moveToSelectedFolderService",
        "collectionListing.action.copy-resources": "copyToSelectedFolderService"
      };
      
      // Mark resources for copy / move
      for (i = tabMenuServices.length; i--;) {
        vrtxAdm.completeSimpleFormAsync({
          selector: "input#" + tabMenuServices[i],
          useClickVal: true,
          fnComplete: markResourcesCopyMove
        });
      }
 
      // Copy / move resources
      for (i = resourceMenuServices.length; i--;) {
        vrtxAdm.completeSimpleFormAsync({
          selector: "#resourceMenuRight li." + resourceMenuServices[i] + " button",
          useClickVal: true,
          extraParams: "&overwrite",
          fnBeforePost: function(form, link) {
            if(link.attr("name") !== "clear-action") {
              form.find(".vrtx-button-small").hide();
              form.find(".vrtx-cancel-link").hide();
              _$("<span class='vrtx-show-processing' />").insertBefore(form.find(".vrtx-cancel-link"));
            }
          },
          fnComplete: resourcesCopyMove
        });
      }

      // Publish / unpublish resources
      vrtxAdm.completeSimpleFormAsync({
        selector: "input#collectionListing\\.action\\.unpublish-resources, input#collectionListing\\.action\\.publish-resources",
        updateSelectors: ["#contents"],
        useClickVal: true,
        fnComplete: function(resultElm) {
          vrtxAdm.displayErrorMsg(resultElm.find(".errormessage").html());
          vrtxAdm.updateCollectionListingInteraction();
        }
      });
      
      // Delete resources
      var deletingD = null;
      vrtxAdm.completeSimpleFormAsync({
        selector: "input#collectionListing\\.action\\.delete-resources",
        updateSelectors: ["#contents"],
        useClickVal: true,
        rowCheckedAnimateOut: true,
        minDelay: 800,
        fnBeforePost: function() {
          deletingD = new VrtxLoadingDialog({title: vrtxAdm.messages.deleting.inprogress});
          deletingD.open();
        },
        fnCompleteInstant: function() {
          deletingD.close();
        },
        fnComplete: function(resultElm) {
          vrtxAdm.displayErrorMsg(resultElm.find(".errormessage").html());
          vrtxAdm.updateCollectionListingInteraction();
        },
        fnError: function() {
          deletingD.close();
        }
      });
      break;
    case "vrtx-trash-can":
      var deletingPermanentD = null;
      var deletingPermanentEmptyFolder = false;
      vrtxAdm.completeSimpleFormAsync({
        selector: "input.deleteResourcePermanent",
        updateSelectors: ["#contents"],
        rowCheckedAnimateOut: true,
        minDelay: 800,
        fnBeforePost: function(form, link) {
          if (vrtxAdm.trashcanCheckedFiles >= vrtxAdm.cachedContent.find("tbody tr").length) {
            deletingPermanentEmptyFolder = true;
          }
          vrtxAdm.trashcanCheckedFiles = 0;
          startTime = new Date();
          deletingPermanentD = new VrtxLoadingDialog({title: vrtxAdm.messages.deleting.inprogress});
          deletingPermanentD.open();
        },
        fnCompleteInstant: function() {
          deletingPermanentD.close();
        },
        fnComplete: function(resultElm) {
          vrtxAdm.displayErrorMsg(resultElm.find(".errormessage").html());
          vrtxAdm.updateCollectionListingInteraction();
          deletingPermanentD.close();
          if(deletingPermanentEmptyFolder) { // Redirect on empty trash can
            window.location.href = "./?vrtx=admin";
          }
        },
        fnError: function() {
          deletingPermanentD.close();
        }
      });
      break;
    case "vrtx-editor":
    case "vrtx-edit-plaintext":
    case "vrtx-visual-profile":
      if (_$("form#editor").length) {
        
        // Dropdowns
        if(!isEmbedded) {
          vrtxAdm.dropdownPlain("#editor-help-menu");
          vrtxAdm.dropdown({
            selector: "ul#editor-menu",
            title: vrtxAdm.messages.dropdowns.editorTitle
          });
        }

        // Save shortcut and AJAX
        vrtxAdm.cachedDoc.bind('keydown', 'ctrl+s', $.debounce(150, true, function (e) {
          ctrlSEventHandler(_$, e);
        }));
    
        // Save
        eventListen(vrtxAdm.cachedAppContent, "click", ".vrtx-save-button", function (ref) {
          var link = _$(ref);
          vrtxAdm.editorSaveButtonName = link.attr("name");
          vrtxAdm.editorSaveButton = link;
          // ! Edit single course schedule session
          vrtxAdm.editorSaveIsRedirectPreview = (ref.id === "saveAndViewButton" || ref.id === "saveViewAction")
                                             && (typeof vrtxEditor === "undefined" || !(vrtxEditor.editorForm.hasClass("vrtx-course-schedule") && onlySessionId.length));
          ajaxSave();
          _$.when(vrtxAdm.asyncEditorSavedDeferred).done(function () {
            vrtxAdm.removeMsg("error");
            // Redirect after save
            if(vrtxAdm.editorSaveIsRedirectPreview) {
              if(typeof vrtxEditor !== "undefined") vrtxEditor.needToConfirm = false;
              var isCollection = _$("#resource-title.true").length;
              if(isCollection) {
                window.location.href = "./?vrtx=admin&action=preview";
              } else {
                window.location.href = window.location.pathname + "?vrtx=admin";
              }
            } else {
              if(typeof vrtxEditor !== "undefined") {
                storeInitPropValues($("#app-content > form, #contents"));
                if(typeof CKEDITOR !== "undefined") {
                  vrtxEditor.richtextEditorFacade.resetChanged();
                }
              }
            }
          }).fail(handleAjaxSaveErrors);
        });
      }
      break;
    case "vrtx-preview":
    case "vrtx-revisions":
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
    case "vrtx-permissions":
      var privilegiesPermissions = ["read", "read-write", "all"];
      for (i = privilegiesPermissions.length; i--;) {
        vrtxAdm.getFormAsync({
          selector: "div.permissions-" + privilegiesPermissions[i] + "-wrapper a.full-ajax",
          selectorClass: "expandedForm-" + privilegiesPermissions[i],
          insertAfterOrReplaceClass: "div.permissions-" + privilegiesPermissions[i] + "-wrapper",
          isReplacing: true,
          nodeType: "div",
          funcComplete: initPermissionForm,
          simultanSliding: false
        });
        vrtxAdm.completeFormAsync({
          selector: "div.permissions-" + privilegiesPermissions[i] + "-wrapper .submitButtons input",
          isReplacing: true,
          updateSelectors: [".permissions-" + privilegiesPermissions[i] + "-wrapper",
                            "#resourceMenuRight"],
          errorContainer: "errorContainer",
          errorContainerInsertAfter: ".groups-wrapper",
          funcProceedCondition: checkStillAdmin,
          funcComplete: function () {
            if (vrtxAdm.reloadFromServer) {
              window.location.reload(true);
            } else {
              vrtxAdm.globalAsyncComplete();
            }
          },
          post: true
        });
      }

      var privilegiesPermissionsInTable = ["add-comment", "read-processed", "read-write-unpublished"];
      for (i = privilegiesPermissionsInTable.length; i--;) {
        vrtxAdm.getFormAsync({
          selector: ".privilegeTable tr." + privilegiesPermissionsInTable[i] + " a.full-ajax",
          selectorClass: privilegiesPermissionsInTable[i],
          insertAfterOrReplaceClass: "tr." + privilegiesPermissionsInTable[i],
          isReplacing: true,
          nodeType: "tr",
          funcComplete: initPermissionForm,
          simultanSliding: true
        });
        vrtxAdm.completeFormAsync({
          selector: "tr." + privilegiesPermissionsInTable[i] + " .submitButtons input",
          isReplacing: true,
          updateSelectors: ["tr." + privilegiesPermissionsInTable[i],
                            "#resourceMenuRight"],
          errorContainer: "errorContainer",
          errorContainerInsertAfter: ".groups-wrapper",
          funcComplete: function () {
            vrtxAdm.globalAsyncComplete();
          },
          post: true
        });
      }

      // Remove/add permissions
      vrtxAdm.completeSimpleFormAsync({
        selector: "input.removePermission",
        updateSelectors: [".principalList"],
        fnComplete: initSimplifiedPermissionForm
      });
      vrtxAdm.completeSimpleFormAsync({
        selector: "span.addGroup input[type='submit']",
        updateSelectors: [".principalList"],
        errorContainer: "errorContainer",
        errorContainerInsertAfter: ".groups-wrapper",
        fnComplete: function() {
          $("input#groupNames").val("");
          initSimplifiedPermissionForm();
        }
      });
      vrtxAdm.completeSimpleFormAsync({
        selector: "span.addUser input[type='submit']",
        updateSelectors: [".principalList"],
        errorContainer: "errorContainer",
        errorContainerInsertAfter: ".users-wrapper",
        fnComplete: function() {
          $("input#userNames").val("");
          initSimplifiedPermissionForm();
        }
      });
      
      var SUBMIT_SET_INHERITED_PERMISSIONS = false;
      vrtxAdm.cachedDoc.on("click", "#permissions\\.toggleInheritance\\.submit", function (e) {
        if (!SUBMIT_SET_INHERITED_PERMISSIONS && typeof confirmSetInheritedPermissionsMsg !== "undefined") {
          var d = new VrtxConfirmDialog({
            msg: confirmSetInheritedPermissionsMsg,
            title: confirmSetInheritedPermissionsTitle,
            onOk: function () {
              SUBMIT_SET_INHERITED_PERMISSIONS = true;
              $("#permissions\\.toggleInheritance\\.submit").trigger("click");
            }
          });
          d.open();
          e.stopPropagation();
          e.preventDefault();
        } else {
          e.stopPropagation();
        }
      });

      break;
    case "vrtx-about":
      vrtxAdm.zebraTables(".resourceInfo");

      if (!vrtxAdmin.isIE7) { // Turn of tmp. in IE7
        var propsAbout = ["contentLocale", "commentsEnabled", "userTitle", "keywords", "description",
                        "verifiedDate", "authorName", "authorEmail", "authorURL", "collection-type",
                        "contentType", "userSpecifiedCharacterEncoding", "plaintext-edit", "xhtml10-type",
                        "obsoleted", "editorial-contacts"];
        for (i = propsAbout.length; i--;) {
          vrtxAdm.getFormAsync({
            selector: ".prop-" + propsAbout[i] + " a.vrtx-button-small",
            selectorClass: "expandedForm-prop-" + propsAbout[i],
            insertAfterOrReplaceClass: "tr.prop-" + propsAbout[i],
            isReplacing: true,
            nodeType: "tr",
            simultanSliding: true
          });
          vrtxAdm.completeFormAsync({
            selector: ".prop-" + propsAbout[i] + " form input[type=submit]",
            isReplacing: true
          });
        }
      }

      var takenOwnership = false;
      vrtxAdm.cachedDoc.on("submit", "#vrtx-admin-ownership-form", function (e) {
        if (!takenOwnership) {
          var d = new VrtxConfirmDialog({
            msg: confirmTakeOwnershipMsg,
            title: confirmTakeOwnershipTitle,
            onOk: function () {
              takenOwnership = true;
              _$("#vrtx-admin-ownership-form").submit();
            }
          });
          d.open();
          e.stopPropagation();
          e.preventDefault();
        } else {
          e.stopPropagation();
        }
      });

      // Urchin stats
      vrtxAdm.cachedBody.on("click", "#vrtx-resource-visit-tab-menu a", function (e) {
        if (vrtxAdm.asyncGetStatInProgress) {
          return false;
        }
        vrtxAdm.asyncGetStatInProgress = true;

        var link = _$(this);
        var liElm = link.parent();
        if (liElm.hasClass("first")) {
          liElm.removeClass("first").addClass("active active-first");
          liElm.next().removeClass("active active-last").addClass("last");
        } else {
          liElm.removeClass("last").addClass("active active-last");
          liElm.prev().removeClass("active active-first").addClass("first");
        }

        _$("#vrtx-resource-visit-wrapper").append("<span id='urchin-loading'></span>");
        _$("#vrtx-resource-visit-chart-stats-info").remove();
        vrtxAdm.serverFacade.getHtml(this.href, {
          success: function (results, status, resp) {
            _$("#urchin-loading").remove();
            _$("#vrtx-resource-visit").append("<div id='vrtx-resource-visit-chart-stats-info'>" + _$($.parseHTML(results)).find("#vrtx-resource-visit-chart-stats-info").html() + "</div>");
            vrtxAdm.asyncGetStatInProgress = false;
          }
        });
        e.stopPropagation();
        e.preventDefault();
      });
      
      break;
    case "vrtx-report-broken-links":
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

VrtxAdmin.prototype.initDomainsInstant = function initDomainsInstant() {
  var vrtxAdm = this;
      
  switch (vrtxAdm.bodyId) {
    case "vrtx-trash-can":
    case "vrtx-manage-collectionlisting":
      vrtxAdm.collectionListingInteraction();
    default: // noop
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

function interceptEnterKeyAndReroute(txt, btn, fnOnKeyPress) {
  vrtxAdmin.cachedAppContent.on("keypress", txt, function (e) {
    if (isKey(e, [vrtxAdmin.keys.ENTER])) {
      if ($(this).hasClass("blockSubmit")) { // submit/rerouting can be blocked elsewhere on textfield
        $(this).removeClass("blockSubmit");
      } else {
        $(btn).click(); // click the associated button
      }
      if(typeof fnOnKeyPress === "function") {
        fnOnKeyPress($(this));
      }
      e.preventDefault();
    }
  });
}

VrtxAdmin.prototype.mapShortcut = function mapShortcut(selectors, reroutedSelector) {
  eventListen(this.cachedAppContent, "click", selectors, function(ref) {
    $(reroutedSelector).click();
  });
};

VrtxAdmin.prototype.logoutButtonAsLink = function logoutButtonAsLink() {
  var btn = $('input#logoutAction');
  if (!btn.length) return;
  
  btn.hide();
  btn.after('&nbsp;<a id=\"logoutAction.link\" name=\"logoutAction\" href="javascript:void(0);">' + btn.attr('value') + '</a>');
  eventListen($("#app-head-wrapper"), "click", "#logoutAction\\.link", function(ref) {
    btn.click();
  });
};


/*-------------------------------------------------------------------*\
    5. Create / Upload / Copy / Move
       XXX: optimize more and needs more seperation
\*-------------------------------------------------------------------*/

function createFuncComplete() {
  var vrtxAdm = vrtxAdmin;
  
  // Navigate radio buttons
  vrtxAdm.cachedDoc.on("keydown", "#active-tab .vrtx-admin-form .radio-buttons input[type='radio']", function(e) {
    if(isKey(e, [vrtxAdm.keys.LEFT_ARROW, vrtxAdm.keys.UP_ARROW, vrtxAdm.keys.RIGHT_ARROW, vrtxAdm.keys.DOWN_ARROW])) {
      var checkBox = $(this);
      var waitAndRefocus = setTimeout(function() {
        var checked = checkBox.closest(".radio-buttons").find("input:checked");
        if(checked.length) {
          checked[0].focus();
        }
      }, 15);
    }
  });
  
  eventListen(vrtxAdm.cachedDoc, "keyup", "#vrtx-div-collection-title input[type='text']", function(ref) {
    createTitleChange($(ref), $("#vrtx-div-collection-name input[type='text']"), null);
  }, null, vrtxAdm.keyInputDebounceRate);
  
  eventListen(vrtxAdm.cachedDoc, "keyup", "#vrtx-div-file-title input[type='text']", function(ref) {
    createTitleChange($(ref), $("#vrtx-div-file-name input[type='text']"), $("#isIndex"));
  }, null, vrtxAdm.keyInputDebounceRate);
  
  eventListen(vrtxAdm.cachedDoc, "keyup", "#vrtx-div-file-name input[type='text'], #vrtx-div-collection-name input[type='text']", function(ref) {
    createFileNameChange($(ref));
  }, null, vrtxAdm.keyInputDebounceRate);

  vrtxAdm.createResourceReplaceTitle = true;

  // Fix margin left for radio descriptions because radio width variation on different OS-themes
  var radioDescriptions = $(".radioDescription");
  if (radioDescriptions.length) {
    var leftPos = $(".radio-buttons label").filter(":first").position().left;
    radioDescriptions.css("marginLeft", leftPos + "px");
  }

  $("#initCreateChangeTemplate").trigger("click");
  $(".vrtx-admin-form input[type='text']").attr("autocomplete", "off").attr("autocorrect", "off");
  
  var notRecommendedTemplates = $("#vrtx-create-templates-not-recommended");
  if(notRecommendedTemplates.length) {
    notRecommendedTemplates.hide();
    $("<a id='vrtx-create-templates-not-recommended-toggle' href='javascript:void(0);'>" + createShowMoreTemplates + "</a>").insertBefore(notRecommendedTemplates);
    $("#vrtx-create-templates-not-recommended-toggle").click(function(e) {
      $(this).hide().next().toggle().parent().find(".radio-buttons:first input:first").click();
      e.stopPropagation();
      e.preventDefault();
    });
  }
}

function createChangeTemplate(hasTitle) {
  var checked = $(".radio-buttons input").filter(":checked");
  var fileTypeEnding = "";
  if (checked.length) {
    var templateFile = checked.val();
    if (templateFile.indexOf(".") !== -1) {
      var fileType = $("#vrtx-textfield-file-type");
      if (fileType.length) {
        fileTypeEnding = templateFile.split(".")[1];
        fileType.text("." + fileTypeEnding);
      }
    }
  }
  var indexCheckbox = $("#isIndex");
  var isIndex = false;

  if (indexCheckbox.length) {
    if (fileTypeEnding !== "html") {
      indexCheckbox.parent().hide();
      if (indexCheckbox.is(":checked")) {
        indexCheckbox.removeAttr("checked");
        createCheckUncheckIndexFile($("#vrtx-div-file-name input[type='text']"), indexCheckbox);
      }
    } else {
      indexCheckbox.parent().show();
      isIndex = indexCheckbox.is(":checked");
    }
  }

  var isIndexOrReplaceTitle = false;
  if (hasTitle) {
    $("#vrtx-div-file-title").show();
    isIndexOrReplaceTitle = vrtxAdmin.createResourceReplaceTitle || isIndex;
  } else {
    $("#vrtx-div-file-title").hide();
    isIndexOrReplaceTitle = isIndex;
  }

  var name = $("#name");
  growField(name, name.val(), 5, isIndexOrReplaceTitle ? 35 : 100, 530);
  
  if (vrtxAdmin.createResourceReplaceTitle) {
    $(".vrtx-admin-form").addClass("file-name-from-title");
  }
}

function createCheckUncheckIndexFile(nameField, indexCheckbox) {
  if (indexCheckbox.is(":checked")) {
    vrtxAdmin.createDocumentFileName = nameField.val();
    growField(nameField, 'index', 5, 35, 530);
    nameField.val("index");
    
    nameField[0].disabled = true;
    $("#vrtx-textfield-file-type").addClass("disabled");
  } else {
    nameField[0].disabled = false;
    $("#vrtx-textfield-file-type").removeClass("disabled");

    nameField.val(vrtxAdmin.createDocumentFileName);
    growField(nameField, vrtxAdmin.createDocumentFileName, 5, (vrtxAdmin.createResourceReplaceTitle ? 35 : 100), 530);
  }
}

function createTitleChange(titleField, nameField, indexCheckbox) {
  if (vrtxAdmin.createResourceReplaceTitle) {
    var nameFieldVal = replaceInvalidChar(titleField.val(), fileTitleSubstitutions, true);
    if (!indexCheckbox || !indexCheckbox.length || !indexCheckbox.is(":checked")) {
      if (nameFieldVal.length > 50) {
        nameFieldVal = nameFieldVal.substring(0, 50);
      }
      nameField.val(nameFieldVal);
      growField(nameField, nameFieldVal, 5, 35, 530);
    } else {
      vrtxAdmin.createDocumentFileName = nameFieldVal;
    }
  }
}

function createFileNameChange(nameField) {
  if (vrtxAdmin.createResourceReplaceTitle) {
    vrtxAdmin.createResourceReplaceTitle = false;
  }

  updateField({
    field: nameField,
    substitutions: fileTitleSubstitutions,
    toLowerCase: true,
    afterUpdate: function(after) {
      growField(this.field, after, 5, 100, 530);
    }
  });
  $(".file-name-from-title").removeClass("file-name-from-title");
}

var fileTitleSubstitutions = {
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
};

function updateField(opts) {
  var currentCaretPos = getCaretPos(opts.field[0]);
  var before = opts.field.val();
  var after = replaceInvalidChar(before, opts.substitutions, opts.toLowerCase);
  opts.field.val(after);
  if(opts.afterUpdate) opts.afterUpdate(after);
  setCaretToPos(opts.field[0], currentCaretPos - (before.length - after.length));
  return after;
}

function replaceInvalidChar(val, substitutions, toLowerCase) {
  if(toLowerCase) val = val.toLowerCase();
  for (var key in substitutions) {
    var replaceThisCharGlobally = new RegExp(key, "g");
    val = val.replace(replaceThisCharGlobally, substitutions[key]);
  }
  return val;
}

/* Taken from second comment (and jquery.autocomplete.js): 
 * http://stackoverflow.com/questions/499126/jquery-set-cursor-position-in-text-area
 */
function setCaretToPos(input, pos) {
  setSelectionRange(input, pos, pos);
}

function setSelectionRange(field, start, end) {
  if (field.createTextRange) {
    var selRange = field.createTextRange();
    selRange.collapse(true);
    selRange.moveStart("character", start);
    selRange.moveEnd("character", end);
    selRange.select();
  } else if (field.setSelectionRange) {
    field.setSelectionRange(start, end);
  } else {
    if (field.selectionStart) {
      field.selectionStart = start;
      field.selectionEnd = end;
    }
  }
  field.focus();
}

/* Taken from fourth comment:
 * http://stackoverflow.com/questions/4928586/get-caret-position-in-html-input
 */
function getCaretPos(input) {
  if (input.setSelectionRange) {
    return input.selectionStart;
  } else if (document.selection && document.selection.createRange) {
    var range = document.selection.createRange();
    var bookmark = range.getBookmark();
    return bookmark.charCodeAt(2) - 2;
  }
}

/* 
 * jQuery autoGrowInput plugin 
 * by James Padolsey
 *
 * Modified to simplified function++ for more specific use / event-handling
 * by USIT, 2012
 *
 * See related thread: 
 * http://stackoverflow.com/questions/931207/is-there-a-jquery-autogrow-plugin-for-text-fields
 */
function growField(input, val, comfortZone, minWidth, maxWidth) {
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

  var newWidth = Math.min(Math.max(testSubject.width() + comfortZone, minWidth), maxWidth),
    currentWidth = input.width();
  if (newWidth !== currentWidth) {
    input.width(newWidth);
  }
}


/**
 * Initialize file upload
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.initFileUpload = function initFileUpload(isAutoUpload) {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;
  var form = _$("form[name=fileUploadService]");
  if (!form.length) return;
  var inputFile = form.find("#file");
  
  if(typeof isAutoUpload !== "undefined" && isAutoUpload) {
    var btnI18n = vrtxAdm.lang === "en" ? "Upload" : "Last opp";
  } else {
    var btnI18n = vrtxAdm.lang === "en" ? "Browse" : "Bla igjennom";
  }

  _$("<input class='vrtx-textfield vrtx-file-upload' tabindex='-1' id='fake-file' type='text' /><a tabindex='-1' class='vrtx-button vrtx-file-upload'>" + btnI18n+ "...</a>")
    .insertAfter(inputFile);

  inputFile.change(function (e) {
    var filePath = _$(this).val();
    filePath = filePath.substring(filePath.lastIndexOf("\\") + 1);
    if (vrtxAdm.supportsFileList) {
      var files = this.files;
      if (files.length > 1) {
        var tailMsg = "files selected";
        if (typeof fileUploadMoreFilesTailMessage !== "undefined") {
          tailMsg = fileUploadMoreFilesTailMessage;
        }
        filePath = files.length + " " + tailMsg;
      }
    }
    form.find("#fake-file").val(filePath);
  });

  var fileUploadLink = _$("a.vrtx-file-upload");
  inputFile.hover(function () { fileUploadLink.addClass("hover");
  }, function () {              fileUploadLink.removeClass("hover"); });
  inputFile.focus(function () { fileUploadLink.addClass("hover");    })
  .blur(function() {            fileUploadLink.removeClass("hover"); });
  
  $("<a id='upload-focus' style='display: inline-block; outline: none;' tabindex='-1' />").insertBefore("#file");
  $("#upload-focus")[0].focus();

  if (vrtxAdm.supportsReadOnly(document.getElementById("fake-file"))) {
    form.find("#fake-file").attr("readOnly", "readOnly");
  }
  if (vrtxAdm.supportsMultipleAttribute(document.getElementById("file"))) {
    inputFile.attr("multiple", "multiple");
    if (typeof multipleFilesInfoText !== "undefined") {
      _$("<p id='vrtx-file-upload-info-text'>" + multipleFilesInfoText + "</p>").insertAfter(".vrtx-textfield");
    }
  }
};

function ajaxUpload(options) {
  var vrtxAdm = vrtxAdmin,
  _$ = vrtxAdm._$;
  
  var futureFormAjax = _$.Deferred();
  if (typeof _$.fn.ajaxSubmit !== "function") {
    var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
    getScriptFn(vrtxAdm.rootUrl + "/jquery/plugins/jquery.form.js").done(function() {
      futureFormAjax.resolve();
    }).fail(function(xhr, textStatus, errMsg) {
      var uploadingFailedD = new VrtxMsgDialog({ title: xhr.status + " " + vrtxAdm.serverFacade.errorMessages.uploadingFilesFailedTitle,
                                                 msg: vrtxAdm.serverFacade.errorMessages.uploadingFilesFailed
                                              });
      uploadingFailedD.open();
      if(opts.funcAfterComplete) {
        opts.funcAfterComplete();
      }
    });
  } else {
    futureFormAjax.resolve();
  }
  _$.when(futureFormAjax).done(function() {
    var fileField = _$("#file");
    var filePaths = "";
    var numberOfFiles = 0;
    var size = 0;
    if (vrtxAdm.supportsFileList) {
      var files = fileField[0].files;
      for (var i = 0, numberOfFiles = files.length; i < numberOfFiles; i++) {
        filePaths += files[i].name + ",";
        size += files[i].size;
      }
    } else {
      filePaths = fileField.val().substring(fileField.val().lastIndexOf("\\") + 1);
      numberOfFiles = 1;
    }
    
    var checkForm = _$("#fileUploadCheckService-form");
    var filenamesToCheckField = checkForm.find("input[name='filenamesToBeChecked']");
    if(!filenamesToCheckField.length) {
      checkForm.append("<input type='hidden' name='filenamesToBeChecked' value='" + filePaths + "' />");
    } else {
      filenamesToCheckField.val(filePaths);
    }
    vrtxAdm.removeErrorContainers(options.form, options.errorContainerInsertAfter, options.errorContainer);

    checkForm.ajaxSubmit({
      success: function(results, status, xhr) {
        var result = _$.parseHTML(results);
        var opts = options;
        var existingFilenamesField = _$(result).find("#file-upload-existing-filenames");
        if (!existingFilenamesField.length && vrtxAdm.hasErrorContainers(result, opts.errorContainer)) {
          vrtxAdm.displayErrorContainers(result, opts.form, opts.errorContainerInsertAfter, opts.errorContainer);
        } else {
          if(existingFilenamesField.length) {
            var existingFilenamesFixedField = _$(result).find("#file-upload-existing-filenames-fixed");
            var existingFilenames = existingFilenamesField.text().split("#");
            var existingFilenamesFixed = existingFilenamesFixedField.text().split("#");
            userProcessExistingFiles({
              filenames: existingFilenames,
              filenamesFixed: existingFilenamesFixed,
              numberOfFiles: numberOfFiles, 
              completeFn: function() {
                ajaxUploadPerform(opts, size);
              },
              cancelFn: function() {
                vrtxAdm.uploadCopyMoveSkippedFiles = {};;
                var animation = new VrtxAnimation({
                  elem: opts.form.parent(),
                  animationSpeed: opts.transitionSpeed,
                  easeIn: opts.transitionEasingSlideDown,
                  easeOut: opts.transitionEasingSlideUp,
                  afterOut: function() {
                    if(opts.funcAfterComplete) {
                      opts.funcAfterComplete();
                    }
                  }
                });
                animation.bottomUp();
              },
              isAllSkippedEqualComplete: false
            });
          } else {
            ajaxUploadPerform(opts, size);
          }
        }
      }
    });
  });
  return false;
}

function ajaxUploadPerform(opts, size) {
  var vrtxAdm = vrtxAdmin,
  _$ = vrtxAdm._$;

  var uploadingD = new VrtxLoadingDialog({title: vrtxAdm.messages.upload.inprogress});
  uploadingD.open();
  
  var dialogUploadingD = _$("#dialog-loading-content");
  // Set role and ARIA on dialog
  dialogUploadingD.attr("role", "progressbar");
  dialogUploadingD.attr("aria-valuemin", "0");
  dialogUploadingD.attr("aria-valuemax", "100");
  dialogUploadingD.attr("aria-valuenow", "0");
  dialogUploadingD.append("<div id='dialog-uploading-bar' /><div id='dialog-uploading-percent'>&nbsp;</div><a id='dialog-uploading-focus' style='outline: none;' tabindex='-1' /><a id='dialog-uploading-abort' href='javascript:void(0);'>Avbryt</a>");
  var dialogUploadingBar = dialogUploadingD.find("#dialog-uploading-bar");
  
  // Set focus on element before cancel link
  var focusElm = dialogUploadingD.find("#dialog-uploading-focus");
  if(focusElm.length) focusElm.focus();
  focusElm.keydown(function(e) {
    if (isKey(e, [vrtxAdm.keys.TAB])) { 
      $(this).next().addClass("tab-visible")[0].focus();
      return false;
    }
  });

  var uploadXhr = null;
  var processesD = null;
  var stillProcesses = false;
  
  // Set form to overwrite-mode
  opts.form.append("<input type='hidden' name='overwrite' value='overwrite' />");
  
  opts.form.ajaxSubmit({
    uploadProgress: function(event, position, total, percent) { // Show upload progress
      _$("#dialog-uploading-percent").text(percent + "%");
      dialogUploadingBar.css("width", percent + "%");
      dialogUploadingD.attr("aria-valuenow", percent);
      if(percent >= 100) {
        stillProcesses = true;
        var waitAndProcess = setTimeout(function() {
          if(stillProcesses) {
            uploadingD.close();
            processesD = new VrtxLoadingDialog({title: vrtxAdm.messages.upload.processes});
            processesD.open();
          }
        }, vrtxAdm.uploadCompleteTimeoutBeforeProcessingDialog);
      }
    },
    beforeSend: function(xhr) {
      uploadXhr = xhr;
    },
    success: function(results, status, xhr) {
      dialogUploadingBar.css("width", "100%");
      dialogUploadingD.attr("aria-valuenow", 100);

      var result = _$.parseHTML(results);
      vrtxAdm.uploadCopyMoveSkippedFiles = {};
      if (vrtxAdm.hasErrorContainers(result, opts.errorContainer)) {
        vrtxAdm.displayErrorContainers(result, opts.form, opts.errorContainerInsertAfter, opts.errorContainer);
      } else {
        var animation = new VrtxAnimation({
          elem: opts.form.parent(),
          animationSpeed: opts.transitionSpeed,
          easeIn: opts.transitionEasingSlideDown,
          easeOut: opts.transitionEasingSlideUp,
          afterOut: function(animation) {
            if(opts.updateSelectors) {
              for (var i = opts.updateSelectors.length; i--;) {
                var outer = vrtxAdm.outerHTML(result, opts.updateSelectors[i]);
                vrtxAdm.cachedBody.find(opts.updateSelectors[i]).replaceWith(outer);
              }
            }
            vrtxAdm.updateCollectionListingInteraction();
            if (opts.funcComplete) {
              opts.funcComplete();
            }
          }
        });
        animation.bottomUp();
      }
    },
    error: function (xhr, textStatus, errMsg) {
      if(uploadXhr === null) {
        var uploadingFailedD = new VrtxMsgDialog({ title: xhr.status + " " + vrtxAdm.serverFacade.errorMessages.uploadingFilesFailedTitle,
                                                   msg: vrtxAdm.serverFacade.errorMessages.uploadingFilesFailed
                                                 });
        uploadingFailedD.open();
      }
    },
    complete: function (xhr, textStatus) {
      stillProcesses = false;
      if(processesD !== null) {
        processesD.close();
      } else {
        uploadingD.close();
      }
      if(opts.funcAfterComplete) {
        opts.funcAfterComplete();
      }
    }
  });
    
  var ajaxUploadAbort = function(e) {
    if(uploadXhr !== null) {
      uploadXhr.abort();
    }
    uploadingD.close();
    $(this).prev().removeClass("tab-visible");
    if(opts.funcAfterComplete) {
      opts.funcAfterComplete();
    }
    e.stopPropagation();
    e.preventDefault();
  };
    
  vrtxAdm.cachedDoc.off("click", "#dialog-uploading-abort", ajaxUploadAbort)
                   .on("click", "#dialog-uploading-abort", ajaxUploadAbort);
}

/*
 * Copy / move
 */
function markResourcesCopyMove(resultElm, form, url, link) {
  var vrtxAdm = vrtxAdmin;

  var li = "li." + tabMenuServicesInjectMap[link.attr("id")];
  var resourceMenuRight = $("#resourceMenuRight");
  var copyMoveExists = "";
  for (var key in tabMenuServicesInjectMap) {
    var copyMove = resourceMenuRight.find("li." + tabMenuServicesInjectMap[key]);
    if (copyMove.length) {
      copyMoveExists = copyMove;
      break;
    }
  }
  var copyMoveAfter = function() {
    resourceMenuRight.html(resultElm.find("#resourceMenuRight").html());
    vrtxAdm.displayInfoMsg(resultElm.find(".infomessage").html());
  };
  if (copyMoveExists !== "") {
    var copyMoveAnimation = new VrtxAnimation({
      elem: copyMoveExists,
      outerWrapperElem: resourceMenuRight,
      useCSSAnim: true,
      after: function() {
        copyMoveExists.remove();
        copyMoveAfter();
        copyMoveAnimation.update({
          elem: resourceMenuRight.find(li),
          useCSSAnim: true,
          outerWrapperElem: resourceMenuRight
        });
        copyMoveAnimation.rightIn();
      }
    });
    copyMoveAnimation.leftOut();
  } else {
    copyMoveAfter();
    var copyMoveAnimation = new VrtxAnimation({
      elem: resourceMenuRight.find(li),
      useCSSAnim: true,
      outerWrapperElem: resourceMenuRight
    });
    copyMoveAnimation.rightIn();
  }
}

function resourcesCopyMove(resultElm, form, url, link) {
  var vrtxAdm = vrtxAdmin;

  var cancelFn = function() {
    form.find(".vrtx-button-small").show();
    form.find(".vrtx-cancel-link").show();
    form.find(".vrtx-show-processing").remove();
    vrtxAdm.uploadCopyMoveSkippedFiles = {};
  };
  var li = form.closest("li");
  var existingFilenamesField = resultElm.find("#copy-move-existing-filenames");
  var moveToSameFolder = resultElm.find("#move-to-same-folder");
  if(moveToSameFolder.length) {
    cancelFn();
    vrtxAdm.displayErrorMsg(vrtxAdm.messages.move.existing.sameFolder);
  } else if(existingFilenamesField.length) {
    var existingFilenames = existingFilenamesField.text().split("#");
    var numberOfFiles = parseInt(resultElm.find("#copy-move-number-of-files").text(), 10);
    userProcessExistingFiles({
      filenames: existingFilenames,
      numberOfFiles: numberOfFiles, 
      completeFn: function() {
        var skippedFiles = "";
        for(key in vrtxAdm.uploadCopyMoveSkippedFiles) {
          skippedFiles += key + ",";
        }
        form.find("#existing-skipped-files").remove();
        form.append("<input id='existing-skipped-files' name='existing-skipped-files' type='hidden' value='" + skippedFiles + "' />");
        cancelFn();
        link.click();
      },
      cancelFn: cancelFn,
      isAllSkippedEqualComplete: true
    });
  } else {
    form.find("#existing-skipped-files").remove();
    var copyMoveAnimation = new VrtxAnimation({
      elem: li,
      outerWrapperElem: $("#resourceMenuRight"),
      useCSSAnim: true,
      after: function() {
        vrtxAdm.displayErrorMsg(resultElm.find(".errormessage").html());
        vrtxAdm.cachedContent.html(resultElm.find("#contents").html());
        vrtxAdm.updateCollectionListingInteraction();
        li.remove();
      }
    });
    copyMoveAnimation.leftOut();
  }
}

function userProcessExistingFiles(opts) {
  var vrtxAdm = vrtxAdmin;

  var filenamesLen = opts.filenames.length;
  var userProcessNextFilename = function() {
    if(opts.filenames.length) {
      var filename = opts.filenames.pop();
      var filenameFixed = opts.filenamesFixed ? opts.filenamesFixed.pop() : filename;
      if(filenamesLen === 1 && opts.numberOfFiles === 1) {
        var skipOverwriteDialogOpts = {
          msg: filenameFixed,
          title: vrtxAdm.messages.upload.existing.title,
          onOk: userProcessNextFilename, // Keep/overwrite file
          btnTextOk: vrtxAdm.messages.upload.existing.overwrite
        };
      } else {
        var skipOverwriteDialogOpts = {
          msg: filenameFixed,
          title: vrtxAdm.messages.upload.existing.title,
          onOk: function () {  // Skip file
            vrtxAdm.uploadCopyMoveSkippedFiles[filename] = "skip";
            userProcessNextFilename();
          },
          btnTextOk: vrtxAdm.messages.upload.existing.skip,
          extraBtns: [{
            btnText: vrtxAdm.messages.upload.existing.overwrite,
            onOk: userProcessNextFilename // Keep/overwrite file
          }]
        };
      }
      skipOverwriteDialogOpts.onCancel = opts.cancelFn;
      var userDecideExistingFileDialog = new VrtxConfirmDialog(skipOverwriteDialogOpts);
      userDecideExistingFileDialog.open();
    } else { // User has decided for all existing uris
      var numberOfSkippedFiles = 0;
       for (skippedFile in vrtxAdm.uploadCopyMoveSkippedFiles) {
         if (vrtxAdm.uploadCopyMoveSkippedFiles[skippedFile]) {
           numberOfSkippedFiles++;
         }
       }
       if(opts.numberOfFiles > numberOfSkippedFiles || opts.isAllSkippedEqualComplete) {
         opts.completeFn();
       } else {
         opts.cancelFn();
      }
    }
  };
  userProcessNextFilename();
}

/**
 * Check if browser supports 'multiple' attribute
 * Credits: http://miketaylr.com/code/input-type-attr.html (MIT license)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.supportsMultipleAttribute = function supportsMultipleAttribute(inputfield) {
  return ( !! (inputfield.multiple === false) && !! (inputfield.multiple !== "undefined")) && !vrtxAdmin.isIOS;
};

/**
 * Check if browser supports 'readOnly' attribute
 * Credits: http://miketaylr.com/code/input-type-attr.html (MIT license)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.supportsReadOnly = function supportsReadOnly(inputfield) {
  return ( !! (inputfield.readOnly === false) && !! (inputfield.readOnly !== "undefined"));
};


/*-------------------------------------------------------------------*\
    6. Collectionlisting
       TODO: dynamic event handlers for tab-menu links
\*-------------------------------------------------------------------*/

/**
 * Initialize collection listing interaction
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.collectionListingInteraction = function collectionListingInteraction() {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  if (!vrtxAdm.cachedDirectoryListing.length) return;
  
  vrtxAdmin.cachedAppContent.on("click", "#vrtx-checkbox-is-index #isIndex", function (e) {
    createCheckUncheckIndexFile($("#vrtx-div-file-name input[type='text']"), $(this));
    e.stopPropagation();
  });
  vrtxAdmin.cachedAppContent.on("click", ".radio-buttons input", function (e) {
    var focusedTextField = $(".vrtx-admin-form input[type='text']").filter(":visible:first");
    if (focusedTextField.length && !focusedTextField.val().length) { // Only focus when empty
      focusedTextField.focus();
    }
    e.stopPropagation();
  });

  // TODO: generalize dialog jQuery UI function with AJAX markup/text
  vrtxAdm.cachedDoc.on("click", "a.vrtx-copy-move-to-selected-folder-disclosed", function (e) {
    var dialogTemplate = $("#vrtx-dialog-template-copy-move-content");
    if (!dialogTemplate.length) {
      vrtxAdm.serverFacade.getHtml(this.href, {
        success: function (results, status, resp) {
          vrtxAdm.cachedBody.append("<div id='vrtx-dialog-template-copy-move-content'>" + _$($.parseHTML(results)).find("#vrtx-dialog-template-content").html() + "</div>");
          dialogTemplate = $("#vrtx-dialog-template-copy-move-content");
          dialogTemplate.hide();
          var d = new VrtxConfirmDialog({
            msg: dialogTemplate.find(".vrtx-confirm-copy-move-explanation").text(),
            title: dialogTemplate.find(".vrtx-confirm-copy-move-confirmation").text(),
            onOk: function () {
              dialogTemplate.find(".vrtx-focus-button").trigger("click");
            }
          });
          d.open();
        }
      });
    } else {
      var d = new VrtxConfirmDialog({
        msg: dialogTemplate.find(".vrtx-confirm-copy-move-explanation").text(),
        title: dialogTemplate.find(".vrtx-confirm-copy-move-confirmation").text(),
        onOk: function () {
          dialogTemplate.find(".vrtx-focus-button").trigger("click");
        }
      });
      d.open();
    }
    e.stopPropagation();
    e.preventDefault();
  });

  if (typeof moveUncheckedMessage !== "undefined") {
    vrtxAdm.placeCopyMoveButtonInActiveTab({
      formName: "collectionListingForm",
      btnId: "collectionListing\\.action\\.move-resources",
      service: "moveResourcesService",
      msg: moveUncheckedMessage,
      title: moveTitle
    });
    vrtxAdm.placeCopyMoveButtonInActiveTab({
      formName: "collectionListingForm",
      btnId: "collectionListing\\.action\\.copy-resources",
      service: "copyResourcesService",
      msg: copyUncheckedMessage,
      title: copyTitle
    });
    vrtxAdm.placeDeleteButtonInActiveTab();

    vrtxAdm.placePublishButtonInActiveTab();
    vrtxAdm.placeUnpublishButtonInActiveTab();
    vrtxAdm.dropdownPlain("#collection-more-menu");
  }

  vrtxAdm.placeRecoverButtonInActiveTab();
  vrtxAdm.placeDeletePermanentButtonInActiveTab();
  vrtxAdm.initializeCheckUncheckAll();
};

/**
 * Update collection listing interaction
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.updateCollectionListingInteraction = function updateCollectionListingInteraction() {
  var vrtxAdm = vrtxAdmin;
  vrtxAdm.cachedContent = vrtxAdm.cachedAppContent.find("#contents");
  vrtxAdm.cachedDirectoryListing = vrtxAdm.cachedContent.find("#directory-listing");
  if(vrtxAdm.cachedDirectoryListing.length) {
    var tdCheckbox = vrtxAdm.cachedDirectoryListing.find("td.checkbox");
    if (tdCheckbox.length) {
      vrtxAdm.cachedDirectoryListing.find("th.checkbox").append("<input type='checkbox' name='checkUncheckAll' />");
    }
    vrtxAdm.cachedContent.find("input[type=submit]").hide();
  }
};

/**
 * Check / uncheck all initialization
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.initializeCheckUncheckAll = function initializeCheckUncheckAll() {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var tdCheckbox = vrtxAdm.cachedDirectoryListing.find("td.checkbox");
  if (tdCheckbox.length) {
    var checkUncheckAll = vrtxAdm.cachedDirectoryListing.find("th.checkbox input[type='checkbox']");
    if(!checkUncheckAll.length) {
      vrtxAdm.cachedDirectoryListing.find("th.checkbox").append("<input type='checkbox' name='checkUncheckAll' />");
    }
  }
  
  vrtxAdm.cachedDirectoryListing.on("focusin focusout", "td a, td input", function (e) {
    $(this).closest("tr")[(e.type === "focusin" ? "add" : "remove") + "Class"]("focus");
  });
  
  // Check / uncheck all
  vrtxAdm.cachedAppContent.on("click", "th.checkbox input", function (e) {
    var trigger = this;
    var checkAll = trigger.checked;

    $(trigger).closest("table").find("tbody tr").filter(function (idx) {
      var name = "checked";
      if (checkAll) {
        $(this).filter(":not(." + name + ")").addClass(name)
               .find("td.checkbox input").attr(name, true).change();
      } else {
        $(this).filter("." + name).removeClass(name)
               .find("td.checkbox input").attr(name, false).change();
      }
    });
    e.stopPropagation();
  });
  // Check / uncheck single
  vrtxAdm.cachedAppContent.on("click", "td.checkbox input", function (e) {
    $(this).closest("tr").toggleClass("checked");
    e.stopPropagation();
  });
};

/**
 * Places Copy or Move button in active tab as link and setup dialog
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuration
 * @param {string} options.service The className for service
 * @param {string} options.btnId The id for button
 * @param {string} options.msg The dialog message
 * @param {string} options.title The dialog title
 */
VrtxAdmin.prototype.placeCopyMoveButtonInActiveTab = function placeCopyMoveButtonInActiveTab(options) {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var btn = vrtxAdm.cachedAppContent.find("#" + options.btnId);
  if (!btn.length) return;
  btn.hide();
  var li = vrtxAdm.cachedActiveTab.find("li." + options.service);
  li.html("<a id='" + options.service + "' href='javascript:void(0);'>" + btn.attr('title') + "</a>");
  vrtxAdm.cachedActiveTab.find("#" + options.service).click(function (e) {
    if (!vrtxAdm.cachedDirectoryListing.find("td input[type=checkbox]:checked").length) {
      var d = new VrtxMsgDialog(options);
      d.open();
    } else {
      vrtxAdm.cachedAppContent.find("#" + options.btnId).click();
    }
    e.stopPropagation();
    e.preventDefault();
  });
};

/**
 * Places Delete button in active tab as link and setup dialog
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.placeDeleteButtonInActiveTab = function placeDeleteButtonInActiveTab() {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var btn = vrtxAdm.cachedAppContent.find('#collectionListing\\.action\\.delete-resources');
  if (!btn.length) return;
  btn.hide();
  var li = vrtxAdm.cachedActiveTab.find('li.deleteResourcesService');
  li.html('<a id="deleteResourceService" href="javascript:void(0);">' + btn.attr('title') + '</a>');

  vrtxAdm.cachedActiveTab.find('#deleteResourceService').click(function (e) {
    var boxes = vrtxAdm.cachedDirectoryListing.find('td input[type=checkbox]:checked');
    var boxesSize = boxes.length;
    if (!boxesSize) {
      var d = new VrtxMsgDialog({msg: deleteUncheckedMessage, title: deleteTitle});
      d.open();
    } else {
      var list = vrtxAdm.buildFileList(boxes, boxesSize, false);
      var d = new VrtxConfirmDialog({
        msg: confirmDelete.replace("(1)", boxesSize) + '<br />' + list,
        title: confirmDeleteTitle,
        onOk: function () {
          vrtxAdm.cachedAppContent.find('#collectionListing\\.action\\.delete-resources').click();
        }
      });
      d.open();
    }
    e.stopPropagation();
    e.preventDefault();
  });
};

/**
 * Places Publish button in active tab as link and setup dialog
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.placePublishButtonInActiveTab = function placeDeleteButtonInActiveTab() {
  if (typeof moreTitle === "undefined") return;
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var btn = vrtxAdm.cachedAppContent.find('#collectionListing\\.action\\.publish-resources');
  if (!btn.length) return;
  btn.hide();
  var li = vrtxAdm.cachedActiveTab.find('li.publishResourcesService');
  li.hide();
  var menu = li.closest("#tabMenuRight");
  var html = '<li class="more-menu">' +
    '<div id="collection-more-menu">' +
    '<span id="collection-more-menu-header">' + moreTitle + '</span>' +
    '<ul><li><a id="publishTheResourcesService" href="javascript:void(0);">' + btn.attr('title') + '</a></li></ul>' +
    '</div>' +
    '</li>';

  menu.append(html);
  $('#publishTheResourcesService').click(function (e) {
    var boxes = vrtxAdm.cachedDirectoryListing.find('td input[type=checkbox]:checked');
    var boxesSize = boxes.length;
    if (!boxesSize) {
      var d = new VrtxMsgDialog({msg: publishUncheckedMessage, title: publishTitle});
      d.open();
    } else {
      var list = vrtxAdm.buildFileList(boxes, boxesSize, false);
      var d = new VrtxConfirmDialog({
        msg: confirmPublish.replace("(1)", boxesSize) + '<br />' + list,
        title: confirmPublishTitle,
        onOk: function () {
          vrtxAdm.cachedAppContent.find('#collectionListing\\.action\\.publish-resources').click();
        }
      });
      d.open();
    }
    e.stopPropagation();
    e.preventDefault();
  });
};

/**
 * Places Unpublish button in active tab as link and setup dialog
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.placeUnpublishButtonInActiveTab = function placeDeleteButtonInActiveTab() {
  if (typeof moreTitle === "undefined") return;
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var btn = vrtxAdm.cachedAppContent.find('#collectionListing\\.action\\.unpublish-resources');
  if (!btn.length) return;
  btn.hide();
  var li = vrtxAdm.cachedActiveTab.find('li.unpublishResourcesService');
  li.hide();
  var menu = li.closest("#tabMenuRight");
  menu.find("#collection-more-menu ul").append('<li><a id="unpublishTheResourcesService" href="javascript:void(0);">' + btn.attr('title') + '</a></li>');
  $('#unpublishTheResourcesService').click(function (e) {
    var boxes = vrtxAdm.cachedDirectoryListing.find('td input[type=checkbox]:checked');
    var boxesSize = boxes.length;
    
    if (!boxesSize) {
      var d = new VrtxMsgDialog({msg: unpublishUncheckedMessage, title: unpublishTitle});
      d.open();
    } else {
      var list = vrtxAdm.buildFileList(boxes, boxesSize, false);
      var d = new VrtxConfirmDialog({
        msg: confirmUnpublish.replace("(1)", boxesSize) + '<br />' + list,
        title: confirmUnpublishTitle,
        onOk: function () {
          vrtxAdm.cachedAppContent.find('#collectionListing\\.action\\.unpublish-resources').click();
        }
      });
      d.open();
    }
    e.stopPropagation();
    e.preventDefault();
  });
};

/**
 * Places Recover button in active tab as link and setup dialog
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.placeRecoverButtonInActiveTab = function placeRecoverButtonInActiveTab() {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var btn = vrtxAdm.cachedAppContent.find('.recoverResource');
  if (!btn.length) return;
  btn.hide();
  vrtxAdm.cachedActiveTab.prepend('<ul class="list-menu" id="tabMenuRight"><li class="recoverResourceService">' +
    '<a id="recoverResourceService" href="javascript:void(0);">' + btn.attr('value') + '</a></li></ul>');
  vrtxAdm.cachedActiveTab.find("#recoverResourceService").click(function (e) {
    var boxes = vrtxAdm.cachedDirectoryListing.find('td input[type=checkbox]:checked');
    var boxesSize = boxes.length;
    var d = new VrtxMsgDialog({msg: recoverUncheckedMessage, title: recoverTitle});
    if (!boxesSize) {
      d.open();
    } else {
      vrtxAdm.trashcanCheckedFiles = boxesSize;
      vrtxAdm.cachedAppContent.find('.recoverResource').click();
    }
    e.stopPropagation();
    e.preventDefault();
  });
};

/**
 * Places Delete Permanent button in active tab as link and setup dialog
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.placeDeletePermanentButtonInActiveTab = function placeDeletePermanentButtonInActiveTab() {
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var btn = vrtxAdm.cachedAppContent.find('.deleteResourcePermanent');
  if (!btn.length) return;
  btn.hide();
  vrtxAdm.cachedActiveTab.find("#tabMenuRight")
    .append('<li class="deleteResourcePermanentService"><a id="deleteResourcePermanentService" href="javascript:void(0);">' + btn.attr('value') + '</a></li>');
  vrtxAdm.cachedActiveTab.find('#deleteResourcePermanentService').click(function (e) {
    var boxes = vrtxAdm.cachedDirectoryListing.find('td input[type=checkbox]:checked');
    var boxesSize = boxes.length;
    if (!boxesSize) {
      var d = new VrtxMsgDialog({msg: deletePermanentlyUncheckedMessage, title: deletePermTitle});
      d.open();
    } else {
      vrtxAdm.trashcanCheckedFiles = boxesSize;
      var list = vrtxAdm.buildFileList(boxes, boxesSize, true);
      var d = new VrtxConfirmDialog({
        msg: confirmDeletePermanently.replace("(1)", boxesSize) + '<br />' + list,
        title: confirmDeletePermTitle,
        onOk: function () {
          vrtxAdm.cachedContent.find('.deleteResourcePermanent').click();
        }
      });
      d.open();
    }
    e.stopPropagation();
    e.preventDefault();
  });
};

/**
 * Builds a file list with ten items based on name- or title-attribute
 *
 * @this {VrtxAdmin}
 * @param {array} boxes The items
 * @param {number} boxesSize The size of the boxes
 * @param {boolean} useTitle Use title- instead of name-attribute?
 * @return {string} The builded HTML
 */
VrtxAdmin.prototype.buildFileList = function buildFileList(boxes, boxesSize, useTitle) {
  var boxesSizeExceedsTen = boxesSize > 10;
  var boxesSizeTmp = boxesSizeExceedsTen ? 10 : boxesSize;

  var fileNameAttr = useTitle ? "title" : "name";

  var list = "<ul>";
  for (var i = 0; i < boxesSizeTmp; i++) {
    var name = boxes[i][fileNameAttr].split("/");
    list += "<li>" + name[name.length - 1] + "</li>";
  }
  list += "</ul>";
  if (boxesSizeExceedsTen) {
    list += "... " + confirmAnd + " " + (boxesSize - 10) + " " + confirmMore;
  }
  return list;
};


/*-------------------------------------------------------------------*\
    7. Editor and Save-robustness (also for plaintext and vis. profile)
\*-------------------------------------------------------------------*/

function handleAjaxSaveErrors(xhr, textStatus) {
  var vrtxAdm = vrtxAdmin,
  _$ = vrtxAdm._$;
  
  if (xhr !== null) {
    /* Fail in performSave() for exceeding 1500 chars in intro/add.content is handled in editor.js with popup */
    
    if(xhr === "UPDATED_IN_BACKGROUND") {
      var serverTime = serverTimeFormatToClientTimeFormat(vrtxAdmin.serverLastModified);
      var nowTime = serverTimeFormatToClientTimeFormat(vrtxAdmin.serverNowTime);
      var ago = "";
      var agoSeconds = ((+nowTime) - (+serverTime)) / 1000;
      if(agoSeconds >= 60) {
        agoMinutes = Math.floor(agoSeconds / 60);
        agoSeconds = agoSeconds % 60;
        ago = agoMinutes + " min " + agoSeconds + "s";
      } else {
        ago = agoSeconds + "s";
      }
      var d = new VrtxConfirmDialog({
        msg: vrtxAdm.serverFacade.errorMessages.outOfDate.replace(/XX/, ago).replace(/YY/, vrtxAdm.serverModifiedBy),
        title: vrtxAdm.serverFacade.errorMessages.outOfDateTitle,
        btnTextOk: vrtxAdm.serverFacade.errorMessages.outOfDateOk,
        width: 450,
        onOk: ajaxSaveAsCopy
      });
      d.open();
      return false;
    } else {
      var msg = vrtxAdmin.serverFacade.error(xhr, textStatus, false);
      if(msg === "RE_AUTH") {
        reAuthenticateRetokenizeForms(true);
      } else if(msg === "LOCKED") {
        var d = new VrtxConfirmDialog({
          msg: vrtxAdm.serverFacade.errorMessages.lockStolen.replace(/XX/, vrtxAdm.lockedBy),
          title: vrtxAdm.serverFacade.errorMessages.lockStolenTitle,
          btnTextOk: vrtxAdm.serverFacade.errorMessages.lockStolenOk,
          width: 450,
          onOk: ajaxSaveAsCopy
        });
        d.open();
      } else {
        var customTitle = vrtxAdm.serverFacade.errorMessages.customTitle[xhr.status];
        var d = new VrtxMsgDialog({
          msg: msg,
          title: customTitle ? customTitle : vrtxAdm.serverFacade.errorMessages.title + " " + xhr.status
        });
        d.open();
      }
    }
  }
}

function ajaxSave() {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  vrtxAdm.asyncEditorSavedDeferred = _$.Deferred();

  if(typeof CKEDITOR !== "undefined" && typeof vrtxEditor !== "undefined") {
    vrtxEditor.richtextEditorFacade.updateInstances();
  }
  var startTime = new Date();
  
  var d = new VrtxLoadingDialog({title: ajaxSaveText});
  d.open();

  if (typeof vrtxImageEditor !== "undefined" && vrtxImageEditor.save) {
    vrtxImageEditor.save();
  }
  if (typeof performSave !== "undefined") {
    var ok = performSave();
    if (!ok) {
      d.close();
      vrtxAdm.asyncEditorSavedDeferred.rejectWith(this, [null, null]);
      return false;
    }
  }
  
  if(!isServerLastModifiedOlderThanClientLastModified(d)) return false;
  
  var extraData = {};
  var skipForm = false;
  if(typeof vrtxEditor !== "undefined" && vrtxEditor.editorForm.hasClass("vrtx-course-schedule")) {
    editorCourseSchedule.saveLastSession();
    extraData = { "csrf-prevention-token": vrtxEditor.editorForm.find("input[name='csrf-prevention-token']").val(),
                  "schedule-content": JSON.stringify(editorCourseSchedule.retrievedScheduleData)
                };
    skipForm = true;
  }
  
  var futureFormAjax = $.Deferred();
  if (typeof $.fn.ajaxSubmit !== "function") {
    var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
    getScriptFn(vrtxAdm.rootUrl + "/jquery/plugins/jquery.form.js").done(function() {
      futureFormAjax.resolve();
    }).fail(function(xhr, textStatus, errMsg) {
      d.close();
      vrtxAdm.asyncEditorSavedDeferred.rejectWith(this, [xhr, textStatus]);
    });
  } else {
    futureFormAjax.resolve();
  }
  $.when(futureFormAjax).done(function() {
    _$("#editor").ajaxSubmit({
      data: extraData,
      skipForm: skipForm,
      success: function(results, status, xhr) { 
        vrtxAdmin.clientLastModified = $($.parseHTML(results)).find("#resource-last-modified").text().split(",");
        var endTime = new Date() - startTime;
        var waitMinMs = 800;
        if (endTime >= waitMinMs) { // Wait minimum 0.8s
          d.close();
          vrtxAdmin.asyncEditorSavedDeferred.resolve();
        } else {
          var waitMinTimer = setTimeout(function () {
            d.close();
            vrtxAdmin.asyncEditorSavedDeferred.resolve();
          }, Math.round(waitMinMs - endTime));
        }
        if(typeof vrtxEditor !== "undefined" && vrtxEditor.editorForm.hasClass("vrtx-course-schedule")) {
          editorCourseSchedule.saved(vrtxAdm.editorSaveButtonName === "updateViewAction");
        }
      },
      error: function (xhr, textStatus, errMsg) {
        d.close();
        vrtxAdmin.asyncEditorSavedDeferred.rejectWith(this, [xhr, textStatus]);
      }
    });
  });
}

function updateClientLastModifiedAlreadyRetrieved() {
  vrtxAdmin.clientLastModified = $("#resource-last-modified").text().split(",");
}

function isServerLastModifiedOlderThanClientLastModified(d) {
  var olderThanMs = 1000; // Ignore changes in 1 second to avoid most strange cases

  var isOlder = true;
  vrtxAdmin._$.ajax({
    type: "GET",
    url: window.location.pathname + "?vrtx=admin&mode=about" + (gup("service", window.location.search) === "view" ? "&service=view" : ""),
    async: false,
    cache: false,
    success: function (results, status, resp) {
      var parsedResults = $($.parseHTML(results));
      vrtxAdmin.serverNowTime = parsedResults.find("#server-now-time").text().split(",");
      vrtxAdmin.serverLastModified = parsedResults.find("#resource-last-modified").text().split(",");
      vrtxAdmin.serverModifiedBy = parsedResults.find("#resource-last-modified-by").text();
      if(isServerLastModifiedNewerThanClientLastModified(olderThanMs)) {
        d.close();
        vrtxAdmin.asyncEditorSavedDeferred.rejectWith(this, ["UPDATED_IN_BACKGROUND", ""]);
        isOlder = false;
      }
    },
    error: function (xhr, textStatus, errMsg) {
      d.close();
      vrtxAdmin.asyncEditorSavedDeferred.rejectWith(this, [xhr, textStatus]);
      isOlder = false;
    }
  });
  return isOlder;
}

function isServerLastModifiedNewerThanClientLastModified(olderThanMs) {
  try {            
    var serverTime = serverTimeFormatToClientTimeFormat(vrtxAdmin.serverLastModified);
    var clientTime = serverTimeFormatToClientTimeFormat(vrtxAdmin.clientLastModified);
    // If server last-modified is newer than client last-modified return true
    var diff = +serverTime - +clientTime;
    var isNewer = diff > olderThanMs;
    vrtxAdmin.log({msg: "\n\tServer: " + serverTime + "\n\tClient: " + clientTime + "\n\tisNewer: " + isNewer + " (" + diff + "ms)"});
    return isNewer;
  } catch(ex) { // Parse error, return true (we don't know)
    vrtxAdmin.log({msg: ex});
    return true; 
  }
}

function serverTimeFormatToClientTimeFormat(time) {
  return new Date(parseInt(time[0], 10), (parseInt(time[1], 10) - 1), parseInt(time[2], 10),
                  parseInt(time[3], 10), parseInt(time[4], 10), parseInt(time[5], 10));
}

/* After reject save */

function ajaxSaveAsCopy() {
  var vrtxAdm = vrtxAdmin,
  _$ = vrtxAdm._$;

  if(/\/$/i.test(window.location.pathname)) { // Folder
    var d = new VrtxMsgDialog({
      msg: vrtxAdm.serverFacade.errorMessages.cantBackupFolder,
      title: vrtxAdm.serverFacade.errorMessages.cantBackupFolderTitle,
      width: 400
    });
    d.open();
    return false;
  }
  
  // POST create the copy
  var form = $("#backupForm");
  var url = form.attr("action");
  var dataString = form.serialize();
  _$.ajax({
    type: "POST",
    url: url,
    data: dataString,
    dataType: "html",
    contentType: "application/x-www-form-urlencoded;charset=UTF-8",
    success: function (results, status, resp) {
      var copyUri = resp.getResponseHeader('Location');
      var copyEditUri = copyUri + window.location.search;
      
      // GET editor for the copy to get token etc.
      _$.ajax({
        type: "GET",
        url: copyEditUri,
        dataType: "html",
        success: function (results, status, resp) {

          // Update form with the copy token and set action to copy uri
          var copyEditEditorToken = _$(_$.parseHTML(results)).find("form#editor input[name='csrf-prevention-token']");
          var editor = _$("form#editor");
          editor.find("input[name='csrf-prevention-token']").val(copyEditEditorToken.val());
          editor.attr("action", copyEditUri);
          vrtxAdm.clientLastModified = vrtxAdm.serverLastModified; // Make sure we can proceed
          ajaxSave();
          _$.when(vrtxAdm.asyncEditorSavedDeferred).done(function () {
            if(typeof vrtxEditor !== "undefined") vrtxEditor.needToConfirm = false;
            if(!vrtxAdm.editorSaveIsRedirectPreview) {
              window.location.href = copyEditUri;
            } else {
              window.location.href = copyUri + "/?vrtx=admin";
            }
          }).fail(handleAjaxSaveErrors);
        },
        error: function (xhr, textStatus, errMsg) {
          handleAjaxSaveErrors(xhr, textStatus);
        }
      });
    },
    error: function (xhr, textStatus, errMsg) {
      if(xhr.status === 423) {
        xhr.status = 4233;
        handleAjaxSaveErrors(xhr, textStatus);
      }
      handleAjaxSaveErrors(xhr, textStatus);
    }
  });
}

function reAuthenticateRetokenizeForms(isEditorSave) {  
  // Open reauth dialog
  var d = new VrtxHtmlDialog({
    name: "reauth-open",
    html: isEditorSave ? vrtxAdmin.serverFacade.errorMessages.sessionInvalidSave
                       : vrtxAdmin.serverFacade.errorMessages.sessionInvalid,
    title: isEditorSave ? vrtxAdmin.serverFacade.errorMessages.sessionInvalidTitleSave
                        : vrtxAdmin.serverFacade.errorMessages.sessionInvalidTitle,
    onOk: function() { // Log in      
      // Loading..
      var d2 = new VrtxLoadingDialog({title: vrtxAdmin.serverFacade.errorMessages.sessionWaitReauthenticate});
      d2.open();
    
      // Open window to reauthenticate - the user may log in
      var newW = openRegular("./?vrtx=admin&service=reauthenticate", 1020, 800, "Reauth");
      newW.focus();

      // Wait for reauthentication (250ms interval)
      var timerDelay = 250;
      var timerWaitReauthenticate = setTimeout(function() {
        var self = arguments.callee;
        $.ajax({
          type: "GET",
          url: "./?vrtx=admin&service=reauthenticate",
          cache: false,
          complete: function (xhr, textStatus, errMsg) {
            if(xhr.status === 0) {
              setTimeout(self, timerDelay);
            } else {
              retokenizeFormsOpenSaveDialog(d2, isEditorSave);
            }
          }
        });
      }, timerDelay);
    },
    btnTextOk: vrtxAdmin.serverFacade.errorMessages.sessionInvalidOk,
    btnTextCancel: "(" + vrtxAdmin.serverFacade.errorMessages.sessionInvalidOkInfo + ")"													
  });
  
  d.open();
                           
  var cancelBtnSpan = $(".ui-dialog[aria-labelledby='ui-dialog-title-dialog-html-reauth-open']").find(".ui-button:last-child span");
  cancelBtnSpan.unwrap();
}

function retokenizeFormsOpenSaveDialog(d2, isEditorSave) {
  // Repopulate all tokens
  var current = $("input[name='csrf-prevention-token']");
  var currentLen = current.length;
  
  $.ajax({
    type: "GET",
    url: window.location.href,
    cache: true,
    dataType: "html",
    success: function (results, status, resp) {
      var updated = $($.parseHTML(results)).find("input[name='csrf-prevention-token']");
      for(var i = 0; i < currentLen; i++) {
        current[i].value = updated[i].value;
      }

      // Stop loading
      d2.close();
      
      // Open save dialog
      var d = new VrtxHtmlDialog({
        name: "reauth-save",
        html: isEditorSave ? vrtxAdmin.serverFacade.errorMessages.sessionValidatedSave 
                           : vrtxAdmin.serverFacade.errorMessages.sessionValidated,
        title: vrtxAdmin.serverFacade.errorMessages.sessionValidatedTitle,
        onOk: function() { // Trigger save or close
          if(isEditorSave) {
            vrtxAdmin.editorSaveButton.click();
          }
        },
        btnTextOk: isEditorSave ? vrtxAdmin.serverFacade.errorMessages.sessionValidatedOkSave
                                : vrtxAdmin.serverFacade.errorMessages.sessionValidatedOk
      });
      d.open();
    },
    error: function (xhr, textStatus, errMsg) {
      d2.close();
      if(isEditorSave) {
        handleAjaxSaveErrors(xhr, textStatus);
      } else {
        var msg = vrtxAdmin.serverFacade.error(xhr, textStatus, true);
        if(msg === "RE_AUTH") {
          reAuthenticateRetokenizeForms(false);
        } else {
          vrtxAdmin.displayErrorMsg(msg);
        }
      }
    }
  });
}

function ctrlSEventHandler(_$, e) {
  if (!_$("#dialog-loading:visible").length) {
    _$(".vrtx-focus-button:last").click();
  }
  e.preventDefault();
  return false;
}


/*-------------------------------------------------------------------*\
    8. Permissions
\*-------------------------------------------------------------------*/

function initPermissionForm(selectorClass) {
  if (!$("." + selectorClass + " .aclEdit").length) return;
  toggleConfigCustomPermissions(selectorClass);
  interceptEnterKeyAndReroute("." + selectorClass + " .addUser input[type=text]", "." + selectorClass + " input.addUserButton", function(txt) {
    txt.unautocomplete();
  });
  interceptEnterKeyAndReroute("." + selectorClass + " .addGroup input[type=text]", "." + selectorClass + " input.addGroupButton", function(txt) {
    txt.unautocomplete();
  });
  initSimplifiedPermissionForm();
}

function initSimplifiedPermissionForm() {
  permissionsAutocomplete('userNames', 'userNames', vrtxAdmin.permissionsAutocompleteParams, false);
  splitAutocompleteSuggestion('userNames');
  permissionsAutocomplete('groupNames', 'groupNames', vrtxAdmin.permissionsAutocompleteParams, false);
}

function toggleConfigCustomPermissions(selectorClass) {
  var customInput = $("." + selectorClass + " ul.shortcuts label[for=custom] input");
  if (!customInput.is(":checked") && customInput.length) {
    $("." + selectorClass).find(".principalList").addClass("hidden");
  }
  var customConfigAnimation = new VrtxAnimation({
    afterIn: function(animation) {
      animation.__opts.elem.removeClass("hidden");
    },
    afterOut: function(animation) {
      animation.__opts.elem.addClass("hidden");
    }
  });
  vrtxAdmin.cachedAppContent.on("click", "." + selectorClass + " ul.shortcuts label[for=custom]", function (e) {
    var elem = $(this).closest("form").find(".principalList.hidden");
    customConfigAnimation.updateElem(elem);
    customConfigAnimation.topDown();
    e.stopPropagation();
  });
  vrtxAdmin.cachedAppContent.on("click", "." + selectorClass + " ul.shortcuts label:not([for=custom])", function (e) {
    var elem = $(this).closest("form").find(".principalList:not(.hidden)");
    customConfigAnimation.updateElem(elem);
    customConfigAnimation.bottomUp();
    e.stopPropagation();
  });
}

function checkStillAdmin(options) {
  var stillAdmin = options.form.find(".still-admin").text();
  vrtxAdmin.reloadFromServer = false;
  if (stillAdmin === "false") {
    vrtxAdmin.reloadFromServer = true;
    var d = new VrtxConfirmDialog({
      msg: removeAdminPermissionsMsg,
      title: removeAdminPermissionsTitle,
      onOk: vrtxAdmin.completeFormAsyncPost,
      onOkOpts: options,
      onCancel: function () {
        vrtxAdmin.reloadFromServer = false;
      }
    });
    d.open();
  } else {
    vrtxAdmin.completeFormAsyncPost(options);
  }
}

function autocompleteUsernames(elms, useEnrichment) {
  var _$ = vrtxAdmin._$;
  var autocompleteTextfields = elms.find('.vrtx-textfield');
  var i = autocompleteTextfields.length;
  while (i--) {
    var id = _$(autocompleteTextfields[i]).attr("id");
    permissionsAutocomplete(id, 'userNames', vrtxAdmin.usernameAutocompleteParams, true);
    if(typeof useEnrichment === "boolean" && useEnrichment) {
      enrichedUsersAutocomplete(id, ".vrtx-button.add");
    }
  }
}

function autocompleteUsername(selector, subselector, useEnrichment) {
  var autocompleteTextfield = vrtxAdmin._$(selector).find('input#' + subselector);
  if (autocompleteTextfield.length) {
    permissionsAutocomplete(subselector, 'userNames', vrtxAdmin.usernameAutocompleteParams, true);
    if(typeof useEnrichment === "boolean" && useEnrichment) {
      enrichedUsersAutocomplete(subselector, ".vrtx-button.add");
    }
  }
}

function autocompleteTags(selector) {
  var _$ = vrtxAdmin._$;
  var autocompleteTextfields = _$(selector).find('.vrtx-textfield');
  var i = autocompleteTextfields.length;
  while (i--) {
    setAutoComplete(_$(autocompleteTextfields[i]).attr("id"), 'tags', vrtxAdmin.tagAutocompleteParams);
  }
}


/*-------------------------------------------------------------------*\
    9. Async functions  
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
 * TODO: Combine it with completeSimpleFormAsync() making this an expanded version of it for expanded slidable forms  
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
    
    if (vrtxAdm.hasErrorContainers(_$.parseHTML(results), opts.errorContainer)) {
      vrtxAdm.displayErrorContainers(_$.parseHTML(results), opts.form, opts.errorContainerInsertAfter, opts.errorContainer);
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
        if (opts.isUndecoratedService || (pageIsMode && !sameMode) || (pageIsRevisions !== remoteIsRevisions)) { // When we need the 'mode=' or 'action=revisions' HTML. TODO: should only run when updateSelector is inside content
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

  // Avoid POST if cancel in actions listing
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
                                                                                  return $(this).find("td.checkbox input:checked").length; }
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
    10. Async helper functions and AJAX server façade   
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
    11. Popups and CK browse server integration
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
  var sOptions = "toolbar=no,status=no,resizable=yes,scrollbars=yes"; // http://www.quirksmode.org/js/popup.html
  return openGeneral(url, width, height, winTitle, sOptions); // title must be without spaces in IE
}

function openPopup(url, width, height, winTitle) {
  var sOptions = "toolbar=no,status=no,resizable=yes"; // http://www.quirksmode.org/js/popup.html
  return openGeneral(url, width, height, winTitle, sOptions); // title must be without spaces in IE
}

function openGeneral(url, width, height, winTitle, sOptions) {
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
    12. Utils
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
  this.ariaBool("expanded", idOrElmDropdown, isExpanded);
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
    if(options.args) {
      fn = options.args.callee.toString().match(/function\s+([^\s\(]+)/);
    }
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
    if(options.args) {
      fn = options.args.callee.toString().match(/function\s+([^\s\(]+)/);
    }
    var msgMid = (fn.length > 1) ? " -> " + fn[1] : "";
    console.error("Vortex admin error" + msgMid + ": " + options.msg);
  } else {
    this.log(options);
  }
};

/**
 * Generate zebra rows in table (PE)
 *
 * @this {VrtxAdmin}
 * @param {string} selector The table selector
 */
VrtxAdmin.prototype.zebraTables = function zebraTables(selector) {
  var _$ = this._$;
  var table = _$("table" + selector);
  if (!table.length) return;
  if (this.isIE8) { // http://www.quirksmode.org/css/contents.html
    table.find("tbody tr:odd").addClass("even"); // hmm.. somehow even is odd and odd is even
    table.find("tbody tr:first-child").addClass("first");
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
 * @param {object} listenBase Base element the events bubbles up to (jQElement)
 * @param {string} eventType Type of events
 * @param {object} listenOn Elements should listen on (jQElement's)
 * @param {object} handlerFn Handler function
 * @param {string} handlerFnCheck If should proceed with handler function (event conditions)
 * @param {number} debounceInterval Debounce the events by some milliseconds
 */
function eventListen(listenBase, eventType, listenOn, handlerFn, handlerFnCheck, debounceInterval) {
  // DEBUG: vrtxAdmin.log({ msg: "Listen for events of type " + eventType.toUpperCase() + " on " + listenOn });
  if(typeof debounceInterval === "number") {
    listenBase.on(eventType, listenOn, $.debounce(debounceInterval, true, function (e) {
      if(typeof handlerFnCheck !== "string"
            || (handlerFnCheck === "clickOrEnter" && (e.type === "click" || isKey(e, [vrtxAdmin.keys.ENTER])))) {
        handlerFn(this);
        // DEBUG: vrtxAdmin.log({ msg: (e.type.toUpperCase() + " for " + (this.id || this.className || this.nodeType)) })
        e.preventDefault();
      }
    }));
  } else {
    listenBase.on(eventType, listenOn, function (e) {
      if(typeof handlerFnCheck !== "string"
            || (handlerFnCheck === "clickOrEnter" && (e.type === "click" || isKey(e, [vrtxAdmin.keys.ENTER])))) {
        handlerFn(this);
        // DEBUG: vrtxAdmin.log({ msg: (e.type.toUpperCase() + " for " + (this.id || this.className || this.nodeType)) })
        e.stopPropagation();
        e.preventDefault();
      }
    });
  }
}


/*-------------------------------------------------------------------*\
    13. Override JavaScript / jQuery
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

/* A little faster dynamic click handler 
 */
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
 * 
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