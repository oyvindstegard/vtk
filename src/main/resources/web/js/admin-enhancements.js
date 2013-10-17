/*
 *  Vortex Admin enhancements
 *
 *  Note(s):
 *
 *  Iterating-method for jQuery elements based on: http://jsperf.com/loop-through-jquery-elements/2 (among multiple tests)
 *   -> Backwards for-loop is in most cases even faster but not always desirable
 *
 *  click/onclick/binding: http://stackoverflow.com/questions/12824549/should-all-jquery-events-be-bound-to-document (No)
 *
 *  TODO: Better/revisit architecture for Async code regarding Deferred/Promise 
 *        (http://net.tutsplus.com/tutorials/javascript-ajax/wrangle-async-tasks-with-jquery-promises/)
 *
 *  ToC: 
 *
 *  1.  Config
 *  2.  DOM is ready
 *  3.  DOM is fully loaded
 *  4.  General / setup interactions
 *  5.  Create / File upload
 *  6.  Collectionlisting
 *  7.  Editor
 *  8.  Permissions
 *  9.  Versioning
 *  10. Async functions
 *  11. Async helper functions and AJAX server façade
 *  11. CK browse server integration
 *  13. Utils
 *  14. Override JavaScript / jQuery
 *
 */

/*-------------------------------------------------------------------*\
    1. Config
\*-------------------------------------------------------------------*/

var startLoadTime = +new Date();

/**
 * Creates an instance of VrtxAdmin
 * @constructor
 */
function VrtxAdmin() {

  /** Cache jQuery instance internally
   * @type object */
  this._$ = $;

  // Browser info/capabilities: used for e.g. progressive enhancement and performance scaling based on knowledge of current JS-engine
  this.ua = navigator.userAgent.toLowerCase();
  this.isIE = this._$.browser.msie;
  this.browserVersion = this._$.browser.version;
  this.isIE9 = this.isIE && this.browserVersion <= 9;
  this.isIE8 = this.isIE && this.browserVersion <= 8;
  this.isIE7 = this.isIE && this.browserVersion <= 7;
  this.isIE6 = this.isIE && this.browserVersion <= 6;
  this.isIETridentInComp = this.isIE7 && /trident/.test(this.ua);
  this.isOpera = this._$.browser.opera;
  this.isSafari = this._$.browser.safari;
  this.isIPhone = /iphone/.test(this.ua);
  this.isIPad = /ipad/.test(this.ua);
  this.isAndroid = /android/.test(this.ua); // http://www.gtrifonov.com/2011/04/15/google-android-user-agent-strings-2/
  this.androidVersion = (function() { // Determine if iframes should be made into l$
    var androidVersion = navigator.userAgent.match(/Android ([^\s]+)/);
    if (androidVersion && androidVersion.length === 2) {
      return parseFloat(androidVersion[1]);
    } else {
      return 0;
    }
  }());
  this.isMobileWebkitDevice = (this.isIPhone || this.isIPad || this.isAndroid);
  this.isWin = ((this.ua.indexOf("win") != -1) || (this.ua.indexOf("16bit") != -1));
  this.supportsFileList = window.FileList;
  this.animateTableRows = !this.isIE;
  this.hasFreeze = typeof Object.freeze !== "undefined"; // ECMAScript 5 check
  this.hasConsole = typeof console !== "undefined";
  this.hasConsoleLog = this.hasConsole && console.log;
  this.hasConsoleError = this.hasConsole && console.error;

  /** Language extracted from cookie */
  this.lang = readCookie("vrtx.manage.language", "no");

  // Autocomplete parameters
  this.permissionsAutocompleteParams = {
    minChars: 4,
    selectFirst: false,
    max: 30,
    delay: 800,
    minWidth: 180,
    adjustForParentWidth: 15
  };
  this.usernameAutocompleteParams = {
    minChars: 2,
    selectFirst: false,
    max: 30,
    delay: 500,
    multiple: false,
    minWidth: 180,
    adjustForParentWidth: 15
  };
  this.tagAutocompleteParams = {
    minChars: 1,
    minWidth: 180,
    adjustForParentWidth: 15
  };

  // Transitions
  this.transitionPropSpeed = this.isMobileWebkitDevice ? 0 : 100;
  this.transitionDropdownSpeed = this.isMobileWebkitDevice ? 0 : 100;
  this.transitionEasingSlideDown = (!(this.isIE && this.browserVersion < 10) && !this.isMobileWebkitDevice) ? "easeOutQuad" : "linear";
  this.transitionEasingSlideUp = (!(this.isIE && this.browserVersion < 10) && !this.isMobileWebkitDevice) ? "easeInQuad" : "linear";

  // Application logic
  this.editorSaveButtonName = "";
  this.asyncEditorSavedDeferred = null;
  this.asyncGetFormsInProgress = 0;
  this.asyncGetStatInProgress = false;
  this.createResourceReplaceTitle = true;
  this.createDocumentFileName = "";
  this.trashcanCheckedFiles = 0;

  this.reloadFromServer = false; // changed by funcProceedCondition and used by funcComplete in completeFormAsync for admin-permissions
  this.ignoreAjaxErrors = false;
  this._$.ajaxSetup({
    timeout: 300000 // 5min
  });
  this.runReadyLoad = true;
  this.bodyId = "";
}

var vrtxAdmin = new VrtxAdmin();


/*-------------------------------------------------------------------*\
    2. DOM is ready
       readyState === "complete" || "DOMContentLoaded"-event (++)
\*-------------------------------------------------------------------*/

vrtxAdmin._$(document).ready(function () {
  var startReadyTime = +new Date(),
    vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  vrtxAdm.cacheDOMNodesForReuse();
  
  var bodyId = vrtxAdm.cachedBody.attr("id");
  bodyId = (typeof bodyId !== "undefined") ? bodyId : "";
  vrtxAdm.bodyId = bodyId;
  vrtxAdm.cachedBody.addClass("js");
  if (vrtxAdm.runReadyLoad === false) return; // XXX: return if should not run all of ready() code

  vrtxAdm.miscAdjustments();
  vrtxAdm.initDropdowns();
  vrtxAdm.initTooltips();
  vrtxAdm.initResourceMenus();
  vrtxAdm.initGlobalDialogs();
  vrtxAdm.initDomains();
  vrtxAdm.initScrollBreadcrumbs();

  vrtxAdm.log({
    msg: "Document.ready() in " + (+new Date() - startReadyTime) + "ms."
  });
});


/*-------------------------------------------------------------------*\
    3. DOM is fully loaded ("load"-event) 
\*-------------------------------------------------------------------*/

vrtxAdmin._$(window).load(function () {
  var vrtxAdm = vrtxAdmin;
  if (vrtxAdm.runReadyLoad === false) return; // XXX: return if should not run load() code

  vrtxAdm.log({
    msg: "Window.load() in " + (+new Date() - startLoadTime) + "ms."
  });
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

  if(vrtxAdm.cachedBody != null) return;

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
   $("#title-container").vortexTips("abbr:not(.delayed)", {
    appendTo: "#title-container",
    containerWidth: 200,
    xOffset: 20,
    yOffset: 0
  });
  $("#title-container").vortexTips("abbr.delayed", {
    appendTo: "#title-container",
    containerWidth: 200,
    animOutPreDelay: 4000,
    xOffset: 20,
    yOffset: 0
  });
  $("#main").vortexTips(".tooltips", {
    appendTo: "#contents",
    containerWidth: 320,
    xOffset: 20,
    yOffset: -30
  });
  this.cachedBody.vortexTips(".ui-dialog:visible .tree-create li span.folder", {
    appendTo: ".vrtx-create-tree",
    containerWidth: 80,
    animOutPreDelay: 4000,
    xOffset: 10,
    yOffset: -8,
    extra: true
  });
}

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
      insertAfterOrReplaceClass: "ul#resourceMenuLeft",
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
      insertAfterOrReplaceClass: "ul#resourceMenuLeft",
      secondaryInsertAfterOrReplaceClass: "ul#resourceMenuRight",
      nodeType: "div",
      simultanSliding: true,
      funcComplete: (isSavingBeforePublish ? function (p) {
        if (vrtxAdm.lang === "en") {
          $("#vrtx-publish-document-form h3").text("Are you sure you want to save and publish?");
        } else {
          $("#vrtx-publish-document-form h3").text("Er du sikker på at du vil lagre og publisere?");
        }
      } : null)
    });
    vrtxAdm.completeFormAsync({
      selector: "form#" + publishUnpublishService + "-form input[type=submit]",
      updateSelectors: ["#resource-title", "#directory-listing", ".prop-lastModified"],
      funcComplete: (isSavingBeforePublish ? function (link) { // Save async
        $(".vrtx-focus-button.vrtx-save-button input").click();
        vrtxAdm.completeFormAsyncPost({ // Publish async
          updateSelectors: ["#resource-title"],
          link: link,
          form: $("#vrtx-publish-document-form"),
          funcComplete: function () {
            vrtxAdm.globalAsyncComplete();
          }
        });
        return false;
      } : function(link) {
        vrtxAdm.globalAsyncComplete();
      }),
      post: (!isSavingBeforePublish && (typeof isImageAudioVideo !== "boolean" || !isImageAudioVideo))
    });
  }
  // Unlock
  vrtxAdm.getFormAsync({
    selector: "#title-container a#manage\\.unlockFormService",
    selectorClass: "globalmenu",
    insertAfterOrReplaceClass: "#resource-title > ul:last",
    nodeType: "div",
    simultanSliding: true
  });
  vrtxAdm.completeFormAsync({
    selector: "form#manage\\.unlockFormService-form input[type=submit]"
  });
  vrtxAdm.completeFormAsync({
    selector: "li.manage\\.unlockFormService form[name=unlockForm]",
    updateSelectors: ["#resourceMenuRight", "#contents"],
    funcComplete: function() {
      vrtxAdm.globalAsyncComplete();
    },
    post: (bodyId !== "vrtx-editor" && bodyId !== "vrtx-edit-plaintext" && bodyId !== "")
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
  vrtxAdm.cachedDoc.on("click", "#global-menu-create a, #vrtx-report-view-other", function (e) {
    var link = this;
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
              location.href = this.href;
            });
            
            var t = new VrtxTree({
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
    e.stopPropagation();
    e.preventDefault();
  });
  
  // Advanced publish settings
  var apsD;
  var datepickerApsD;
  vrtxAdm.cachedDoc.on("click", "#advanced-publish-settings", function (e) {
    var link = this;
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
            // TODO: rootUrl and jQueryUiVersion should be retrieved from Vortex config/properties somehow
            var rootUrl = "/vrtx/__vrtx/static-resources";
            var futureDatepicker = (typeof VrtxDatepicker === "undefined") ? $.getScript(rootUrl + "/js/datepicker/vrtx-datepicker.js") : $.Deferred().resolve();
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
    e.stopPropagation();
    e.preventDefault();
  });
  
  vrtxAdm.completeFormAsync({
    selector: "#dialog-html-advanced-publish-settings-content #submitButtons input",
    updateSelectors: ["#resource-title", "#directory-listing", ".prop-lastModified"],
    post: true,
    isUndecoratedService: true,
    funcProceedCondition: function(options) {
      var dialogId = "#dialog-html-advanced-publish-settings-content";
      var dialog = $(dialogId);

      var publishDate = generateDateObjForValidation(dialog, "publishDate");
      var unpublishDate = generateDateObjForValidation(dialog, "unpublishDate");
      
      // Check that unpublish date is not set alone
      if(unpublishDate != null && publishDate == null) {
        vrtxAdm.displayDialogErrorMsg(dialogId + " #submitButtons", publishing.msg.error.unpublishDateNonExisting);
        return; 
      }
      
      // Check that unpublish date is not before or same as publish date
      if(unpublishDate != null && (unpublishDate <= publishDate)) {
        vrtxAdm.displayDialogErrorMsg(dialogId + " #submitButtons", publishing.msg.error.unpublishDateBefore);
        return;
      }
      
      datepickerApsD.prepareForSave();
      
      vrtxAdm.completeFormAsyncPost(options);
    },
    funcComplete: function () {
      apsD.close();
      vrtxAdm.globalAsyncComplete();
    }
  });
  
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
      previewIframe[0].src = previewIframe[0].src;
    }
  }
  $("#advanced-publish-settings-content").remove();
  vrtxAdm.adjustResourceTitle();
  vrtxAdm.initResourceTitleDropdown();
  vrtxAdm.initPublishingDropdown();
  vrtxAdm.updateCollectionListingInteraction();
};

/*
 * Domains init
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
          if (tabMenuServices[i] != "fileUploadService") {
            vrtxAdm.getFormAsync({
              selector: "ul#tabMenuRight a#" + tabMenuServices[i],
              selectorClass: "vrtx-admin-form",
              insertAfterOrReplaceClass: "#active-tab ul#tabMenuRight",
              nodeType: "div",
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
                createTitleChange("#vrtx-textfield-collection-title input", $("#vrtx-textfield-collection-name input"), $("#isIndex"));
                createTitleChange("#vrtx-textfield-file-title input", $("#vrtx-textfield-file-name input"), $("#isIndex"));
              }
            });
          } else {
            if (vrtxAdm.isIPhone || vrtxAdm.isIPad) { // TODO: feature detection
              $("ul#tabMenuRight li." + tabMenuServices[i]).remove();
            } else {
              vrtxAdm.getFormAsync({
                selector: "ul#tabMenuRight a#" + tabMenuServices[i],
                selectorClass: "vrtx-admin-form",
                insertAfterOrReplaceClass: "#active-tab ul#tabMenuRight",
                nodeType: "div",
                funcComplete: function (p) {
                  vrtxAdm.initFileUpload();
                },
                simultanSliding: true
              });
              vrtxAdm.completeFormAsync({
                selector: "form#" + tabMenuServices[i] + "-form input[type=submit]"
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
      for (i = tabMenuServices.length; i--;) {
        vrtxAdm.cachedContent.on("click", "input#" + tabMenuServices[i], function (e) {
          var input = _$(this);
          var form = input.closest("form");
          var url = form.attr("action");
          var li = "li." + tabMenuServicesInjectMap[input.attr("id")];
          var dataString = form.serialize() + "&" + input.attr("name") + "=" + input.val();
          vrtxAdm.serverFacade.postHtml(url, dataString, {
            success: function (results, status, resp) {
              var resourceMenuRight = $("#resourceMenuRight");
              var copyMoveExists = "";
              for (var key in tabMenuServicesInjectMap) {
                var copyMove = resourceMenuRight.find("li." + tabMenuServicesInjectMap[key]);
                if (copyMove.length) {
                  copyMoveExists = copyMove;
                  break;
                }
              }
              results = _$($.parseHTML(results));
              
              var copyMoveAfter = function() {
                resourceMenuRight.html(results.find("#resourceMenuRight").html());
                vrtxAdm.displayInfoMsg(results.find(".infomessage").html());
                var resourceTitle = resourceMenuRight.closest("#resource-title");
                if (resourceTitle.hasClass("compact")) { // Instant compact => expanded
                  resourceTitle.removeClass("compact");
                }
              };
              
              if (copyMoveExists !== "") {
                var copyMoveAnimation = new VrtxAnimation({
                  elem: copyMoveExists,
                  outerWrapperElem: resourceMenuRight,
                  after: function() {
                    copyMoveExists.remove();
                    copyMoveAfter();
                    copyMoveAnimation.update({
                      elem: resourceMenuRight.find(li),
                      outerWrapperElem: resourceMenuRight
                    })
                    copyMoveAnimation.rightIn();
                  }
                });
                copyMoveAnimation.leftOut();
              } else {
                copyMoveAfter();
                var copyMoveAnimation = new VrtxAnimation({
                  elem: resourceMenuRight.find(li),
                  outerWrapperElem: resourceMenuRight
                });
                copyMoveAnimation.rightIn();
              }
            }
          });
          e.stopPropagation();
          e.preventDefault();
        });
      }

      for (i = resourceMenuServices.length; i--;) {
        vrtxAdm.cachedAppContent.on("click", "#resourceMenuRight li." + resourceMenuServices[i] + " button", function (e) {
          var button = _$(this);
          var form = button.closest("form");
          var url = form.attr("action");
          var li = form.closest("li");
          var dataString = form.serialize() + "&" + button.attr("name") + "=" + button.val();
          vrtxAdm.serverFacade.postHtml(url, dataString, {
            success: function (results, status, resp) {
              var copyMoveAnimation = new VrtxAnimation({
                elem: li,
                outerWrapperElem: $("#resourceMenuRight"),
                after: function() {
                  var result = _$($.parseHTML(results));
                  vrtxAdm.displayErrorMsg(result.find(".errormessage").html());
                  vrtxAdm.cachedContent.html(_$($.parseHTML(results)).find("#contents").html());
                  vrtxAdm.updateCollectionListingInteraction();
                  li.remove();
                  var resourceTitle = _$($.parseHTML(results)).find("#resource-title");
                  if (resourceTitle.hasClass("compact")) { // Instant compact => expanded
                    $("#resource-title").addClass("compact");
                  }
                }
              });
              copyMoveAnimation.leftOut();
            }
          });
          e.stopPropagation();
          e.preventDefault();
        });
      }
      vrtxAdm.cachedContent.on("click", "input#collectionListing\\.action\\.unpublish-resources, input#collectionListing\\.action\\.publish-resources, input#collectionListing\\.action\\.delete-resources", function (e) {
        var input = _$(this);
        var form = input.closest("form");
        var url = form.attr("action");
        var dataString = form.serialize() + "&" + input.attr("name") + "=" + input.val();
        vrtxAdm.serverFacade.postHtml(url, dataString, {
          success: function (results, status, resp) {
            var result = _$($.parseHTML(results));
            vrtxAdm.displayErrorMsg(result.find(".errormessage").html());
            vrtxAdm.cachedContent.html(result.find("#contents").html());
            vrtxAdm.updateCollectionListingInteraction();
          }
        });
        e.stopPropagation();
        e.preventDefault();
      });
      vrtxAdm.collectionListingInteraction();
      break;
    case "vrtx-trash-can":
      vrtxAdm.cachedContent.on("click", "input.deleteResourcePermanent", function (e) {
        if (vrtxAdm.trashcanCheckedFiles >= (vrtxAdm.cachedContent.find("tbody tr").length - 1)) return; // Redirect if empty trash can
        vrtxAdm.trashcanCheckedFiles = 0;
        var input = _$(this);
        var form = input.closest("form");
        var url = form.attr("action");
        var dataString = form.serialize() + "&" + input.attr("name");
        vrtxAdm.serverFacade.postHtml(url, dataString, {
          success: function (results, status, resp) {
            var result = _$($.parseHTML(results));
            vrtxAdm.displayErrorMsg(result.find(".errormessage").html());
            vrtxAdm.cachedContent.html(result.find("#contents").html());
            vrtxAdm.updateCollectionListingInteraction();
          }
        });
        e.stopPropagation();
        e.preventDefault();
      });
      vrtxAdm.collectionListingInteraction();
      break;
    case "vrtx-editor":
    case "vrtx-edit-plaintext":
    case "vrtx-visual-profile":
      editorInteraction(bodyId, vrtxAdm, _$);
      break;
    case "vrtx-preview":
    case "vrtx-revisions":
      versioningInteraction(bodyId, vrtxAdm, _$);
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
          simultanSliding: false,
          transitionSpeed: 0,
          transitionEasingSlideDown: "linear",
          transitionEasingSlideUp: "linear"
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
              location.reload(true);
            }
          },
          post: true,
          transitionSpeed: 0,
          transitionEasingSlideDown: "linear",
          transitionEasingSlideUp: "linear"
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
          post: true
        });
      }

      // Remove/add permissions
      vrtxAdm.removePermissionAsync("input.removePermission", ".principalList");
      vrtxAdm.addPermissionAsync("span.addGroup", ".principalList", ".groups-wrapper", "errorContainer");
      vrtxAdm.addPermissionAsync("span.addUser", ".principalList", ".users-wrapper", "errorContainer");

      var SUBMIT_SET_INHERITED_PERMISSIONS = false;
      vrtxAdm.cachedDoc.on("click", "#permissions\\.toggleInheritance\\.submit", function (e) {
        if (!SUBMIT_SET_INHERITED_PERMISSIONS) {
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

      var SUBMIT_TAKE_OWNERSHIP = false;
      vrtxAdm.cachedDoc.on("submit", "#vrtx-admin-ownership-form", function (e) {
        if (!SUBMIT_TAKE_OWNERSHIP) {
          var d = new VrtxConfirmDialog({
            msg: confirmTakeOwnershipMsg,
            title: confirmTakeOwnershipTitle,
            onOk: function () {
              SUBMIT_TAKE_OWNERSHIP = true;
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
          liElm.removeClass("first").addClass("active").addClass("active-first");
          liElm.next().removeClass("active").removeClass("active-last").addClass("last");
        } else {
          liElm.removeClass("last").addClass("active").addClass("active-last");
          liElm.prev().removeClass("active").removeClass("active-first").addClass("first");
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
    default:
      // noop
      break;
  }
};

/* 
 * VrtxAnimation
 *
 */
 
var VrtxAnimationInterface = dejavu.Interface.declare({
  $name: "VrtxAnimationInterface",
  __opts: {},
  __prepareHorizontalMove: function() {},
  __horizontalMove: function() {},
  update: function(opts) {},
  updateElem: function(elem) {},
  rightIn: function() {},
  leftOut: function() {},
  topDown: function() {},
  bottomUp: function() {}
});
 
var VrtxAnimation = dejavu.Class.declare({
  $name: "VrtxAnimation",
  $implements: [VrtxAnimationInterface],
  $constants: {
    // TODO: remove vrtxAdmin dependency
    animationSpeed: typeof vrtxAdmin !== "undefined" && vrtxAdmin.isMobileWebkitDevice ? 0 : 200,
    easeIn: (typeof vrtxAdmin !== "undefined" && !(vrtxAdmin.isIE && vrtxAdmin.browserVersion < 10) && !vrtxAdmin.isMobileWebkitDevice) ? "easeInQuad" : "linear",
    easeOut: (typeof vrtxAdmin !== "undefined" && !(vrtxAdmin.isIE && vrtxAdmin.browserVersion < 10) && !vrtxAdmin.isMobileWebkitDevice) ? "easeOutQuad" : "linear"
  },
  __opts: {},
  initialize: function(opts) {
    this.__opts = opts;
  },
  __prepareHorizontalMove: function() {
    if(this.__opts.outerWrapperElem && !this.__opts.outerWrapperElem.hasClass("overflow-hidden")) {
      this.__opts.outerWrapperElem.addClass("overflow-hidden");
    }
    return this.__opts.elem.outerWidth(true);
  },
  __horizontalMove: function(left, easing) {
    var animation = this;
    animation.__opts.elem.animate({
      "marginLeft": left + "px"
    }, animation.__opts.animationSpeed || animation.$static.animationSpeed, easing, function() {
      if(animation.__opts.outerWrapperElem) animation.__opts.outerWrapperElem.removeClass("overflow-hidden");
      if(animation.__opts.after) animation.__opts.after(animation);
      // TODO: closures pr. direction if needed also for horizontal animation
      if(animation.__opts.afterIn) animation.__opts.afterIn(animation);
      if(animation.__opts.afterOut) animation.__opts.afterOut(animation);
    });
  },
  update: function(opts) {
    this.__opts = opts;
  },
  updateElem: function(elem) {
    this.__opts.elem = elem;
  },
  rightIn: function() {
    var width = this.__prepareHorizontalMove();
    this.__opts.elem.css("marginLeft", -width);
    this.__horizontalMove(0, this.__opts.easeIn || this.$static.easeIn);
  },
  leftOut: function() {
    var width = this.__prepareHorizontalMove();
    this.__horizontalMove(-width, this.__opts.easeOut || this.$static.easeOut);
  },
  topDown: function() {
    var animation = this;
    animation.__opts.elem.slideDown(
        animation.__opts.animationSpeed || animation.$static.animationSpeed,
        animation.__opts.easeIn || animation.$static.easeIn, function() {
      if(animation.__opts.after) animation.__opts.after(animation);
      if(animation.__opts.afterIn) animation.__opts.afterIn(animation);
    });
  },
  bottomUp: function() {
    var animation = this;
    animation.__opts.elem.slideUp(
        animation.__opts.animationSpeed || animation.$static.animationSpeed, 
        animation.__opts.easeOut || animation.$static.easeOut, function() {
      if(animation.__opts.after) animation.__opts.after(animation);
      if(animation.__opts.afterOut) animation.__opts.afterOut(animation);
    });
  }
});
 

/*
 * VrtxTree - facade to TreeView async
 *  
 *  * Requires Dejavu OOP library
 *  * Requires but Lazy-loads TreeView and ScrollTo libraries (if not defined) on open
 */

var VrtxTreeInterface = dejavu.Interface.declare({
  $name: "VrtxTreeInterface",
  __opts: {},
  __openLeaf: function() {}
});

var VrtxTree = dejavu.Class.declare({
  $name: "VrtxTree",
  $implements: [VrtxTreeInterface],
  $constants: {
    leafLoadingClass: "loading-tree-node",
    leafSelector: "> .hitarea" // From closest li
  },
  __opts: {},
  initialize: function(opts) {
    var tree = this;
    tree.__opts = opts;
    tree.__opts.pathNum = 0;
    
    // TODO: rootUrl and jQueryUiVersion should be retrieved from Vortex config/properties somehow
    var rootUrl = "/vrtx/__vrtx/static-resources";
    var jQueryUiVersion = "1.10.3";
    
    var futureTree = $.Deferred();
    if (typeof $.fn.treeview !== "function") {
      $.getScript(location.protocol + "//" + location.host + rootUrl + "/jquery/plugins/jquery.treeview.js", function () {
        $.getScript(location.protocol + "//" + location.host + rootUrl + "/jquery/plugins/jquery.treeview.async.js", function () {
          $.getScript(location.protocol + "//" + location.host + rootUrl + "/jquery/plugins/jquery.scrollTo.min.js", function () {
            futureTree.resolve();
          });
        });
      });
    } else {
      futureTree.resolve();
    }
    $.when(futureTree).done(function() {
      opts.elem.treeview({
        animated: "fast",
        url: location.protocol + '//' + location.host + location.pathname + "?vrtx=admin&uri=&" + opts.service + "&ts=" + (+new Date()),
        service: opts.service,
        dataLoaded: function () {
          tree.__openLeaf();
        }
      });
    });
  },
  __openLeaf: function() {
    var tree = this;
    var checkLeafAvailable = setInterval(function () {
      $("." + tree.$static.leafLoadingClass).remove();
      var link = tree.__opts.elem.find("a[href$='" + tree.__opts.trav[tree.__opts.pathNum] + "']");
      if (link.length) {
        clearInterval(checkLeafAvailable);
        var hit = link.closest("li").find(tree.$static.leafSelector);
        hit.click();
        if (tree.__opts.scrollToContent && (tree.__opts.pathNum == (tree.__opts.trav.length - 1))) {
          tree.__opts.elem.css("background", "none").fadeIn(200, function () {  // Scroll to node
            $(tree.__opts.scrollToContent).scrollTo(Math.max(0, (link.position().top - 145)), 250, {
              easing: "swing",
              queue: true,
              axis: 'y',
              complete: tree.__opts.afterTrav(link)
            });
          });
        } else {
          $("<span class='" + tree.$static.leafLoadingClass + "'>" + loadingSubfolders + "</span>").insertAfter(hit.next());
        }
        tree.__opts.pathNum++;
      }
    }, 20);
  }
});

/**
 * Initialize dropdowns
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.initDropdowns = function initDropdowns() {
  this.dropdownPlain("#locale-selection");
  this.initResourceTitleDropdown();
  this.dropdown({
    selector: "ul.manage-create"
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
    calcTop: true
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
    calcLeft: true
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

  var parent = languageMenu.parent();
  parent.addClass("js-on");

  // Remove ':' and replace <span> with <a>
  var header = parent.find(selector + "-header");
  var headerText = header.text();
  // outerHtml
  header.replaceWith("<a href='javascript:void(0);' id='" + selector.substring(1) + "-header'>" + headerText.substring(0, headerText.length - 1) + "</a>");

  languageMenu.addClass("dropdown-shortcut-menu-container");

  vrtxAdm.cachedBody.on("click", selector + "-header", function (e) {
    vrtxAdm.closeDropdowns();
    vrtxAdm.openDropdown(_$(this).next(".dropdown-shortcut-menu-container"));

    e.stopPropagation();
    e.preventDefault();
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

  var numOfListElements = list.find("li").length;

  if (!options.proceedCondition || (options.proceedCondition && options.proceedCondition(numOfListElements))) {
    list.addClass("dropdown-shortcut-menu");
    if(options.small) {
      list.addClass("dropdown-shortcut-menu-small");
    }

    // Move listelements except .first into container
    var listParent = list.parent();
    listParent.append("<div class='dropdown-shortcut-menu-container'><ul>" + list.html() + "</ul></div>");

    var startDropdown = options.start ? ":nth-child(-n+" + options.start + ")" : ".first";
    var dropdownClickArea = options.start ? ":nth-child(3)" : ".first";

    list.find("li").not(startDropdown).remove();
    list.find("li" + dropdownClickArea).append("<span id='dropdown-shortcut-menu-click-area'></span>");

    var shortcutMenu = listParent.find(".dropdown-shortcut-menu-container");
    shortcutMenu.find("li" + startDropdown).remove();
    if (options.calcTop) {
      shortcutMenu.css("top", (list.position().top + list.height() - (parseInt(list.css("marginTop"), 10) * -1) + 1) + "px");
    }
    var left = (list.width() + 5)
    if (options.calcLeft) {
      left += list.position().left;
    }
    shortcutMenu.css("left", left + "px");

    list.find("li" + dropdownClickArea).addClass("dropdown-init");

    list.find("li.dropdown-init #dropdown-shortcut-menu-click-area").click(function (e) {
      vrtxAdm.closeDropdowns();
      vrtxAdm.openDropdown(shortcutMenu);
      e.stopPropagation();
      e.preventDefault();
    });

    list.find("li.dropdown-init #dropdown-shortcut-menu-click-area").hover(function () {
      var area = _$(this);
      area.parent().toggleClass('unhover');
      area.prev().toggleClass('hover');
    });
  }
};

/**
 * Open dropdown (slide down)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.openDropdown = function openDropdown(elm) {
  var animation = new VrtxAnimation({
    elem: elm.not(":visible"),
    animationSpeed: vrtxAdmin.transitionDropdownSpeed,
    easeIn: "swing"
  });
  animation.topDown();
};

/**
 * Close all dropdowns (slide up)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.closeDropdowns = function closeDropdowns() {
  var animation = new VrtxAnimation({
    elem: this._$(".dropdown-shortcut-menu-container:visible"),
    animationSpeed: vrtxAdmin.transitionDropdownSpeed,
    easeIn: "swing"
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
  
  var crumbs = $(".vrtx-breadcrumb-level, .vrtx-breadcrumb-level-no-url"), i = crumbs.length, crumbsWidth = 0;
  while(i--) {
    crumbsWidth += $(crumbs[i]).outerWidth(true) + 2;
  }
  crumbs.wrapAll("<div id='vrtx-breadcrumb-inner' style='width: " + crumbsWidth + "px' />");
  vrtxAdm.crumbsWidth = crumbsWidth;
  vrtxAdm.crumbsInner = $("#vrtx-breadcrumb-inner");
  vrtxAdm.crumbsInner.wrap("<div id='vrtx-breadcrumb-outer' />");
      
  var navHtml = "<span id='navigate-crumbs-left-coverup' />" +
                "<a id='navigate-crumbs-left' class='navigate-crumbs'><span class='navigate-crumbs-icon'></span><span class='navigate-crumbs-dividor'></span></a>" +
                "<a id='navigate-crumbs-right' class='navigate-crumbs'><span class='navigate-crumbs-icon'></span><span class='navigate-crumbs-dividor'></span></a>";                                      
      
  $("#vrtx-breadcrumb").append(navHtml);
      
  vrtxAdm.crumbsLeft = $("#navigate-crumbs-left");
  vrtxAdm.crumbsLeftCoverUp = $("#navigate-crumbs-left-coverup");
  vrtxAdm.crumbsRight = $("#navigate-crumbs-right"); 
  vrtxAdm.cachedDoc.on("click", "#navigate-crumbs-left", function(e) {
    vrtxAdmin.scrollBreadcrumbsLeft();
    e.stopPropagation();
    e.preventDefault();
  });
  vrtxAdm.cachedDoc.on("click", "#navigate-crumbs-right", function(e) {
    vrtxAdmin.scrollBreadcrumbsRight();
    e.stopPropagation();
    e.preventDefault();
  }); 
  /* TODO: replace with stacking of blue/hovered element above nav(?) */
  vrtxAdm.cachedDoc.on("mouseover mouseout", ".vrtx-breadcrumb-level", function(e) {
    var hoveredBreadcrumb = $(this);
    if(!hoveredBreadcrumb.hasClass("vrtx-breadcrumb-active")) {
      if(vrtxAdm.crumbsState == "left") {            
        var gradientRight = vrtxAdm.crumbsRight;
        var gradientLeftEdge = gradientRight.offset().left;
        var crumbRightEdge = hoveredBreadcrumb.offset().left + hoveredBreadcrumb.width();
        if(crumbRightEdge > gradientLeftEdge) {
          gradientRight.find(".navigate-crumbs-dividor").toggle();
        }
      } else if(vrtxAdm.crumbsState == "right") {
        var gradientLeft = vrtxAdm.crumbsLeft;
        var gradientRightEdge = gradientLeft.offset().left + gradientLeft.width();
        var crumbLeftEdge = hoveredBreadcrumb.offset().left;
        if(crumbLeftEdge < gradientRightEdge) {
          gradientLeft.find(".navigate-crumbs-dividor").toggle();
        }
      }
    }
    e.stopPropagation();
    e.preventDefault();
  });     
  vrtxAdm.scrollBreadcrumbsRight();
  vrtxAdm.crumbsInner.addClass("animate");  
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
 
VrtxAdmin.prototype.miscAdjustments = function miscAdjustments() {
  var vrtxAdm = this;

   // Remove active tab if it has no children
  if (!vrtxAdm.cachedActiveTab.find(" > *").length) {
    vrtxAdm.cachedActiveTab.remove();
  }

  // Remove active tab-message if it is empty
  var activeTabMsg = vrtxAdm.cachedActiveTab.find(" > .tabMessage");
  if (!activeTabMsg.text().length) {
    activeTabMsg.remove();
  }
  
  interceptEnterKey();

  vrtxAdm.logoutButtonAsLink();
  
  vrtxAdm.adjustResourceTitle();
  
  // Ignore all AJAX errors when user navigate away (abort)
  if(typeof unsavedChangesInEditorMessage !== "function") {
    var ignoreAjaxErrorOnBeforeUnload = function() {
      vrtxAdm.ignoreAjaxErrors = true;
    };
    window.onbeforeunload = ignoreAjaxErrorOnBeforeUnload;    
  } 

  // Show message in IE6, IE7 and IETrident in compability mode
  if (vrtxAdm.isIE7 || vrtxAdm.isIETridentInComp) {
    var message = vrtxAdm.cachedAppContent.find(" > .message");
    if (message.length) {
      message.html(outdatedBrowserText);
    } else {
      vrtxAdm.cachedAppContent.prepend("<div class='infomessage'>" + outdatedBrowserText + "</div>");
    }
  }
};

/**
 * Adjust resource title across multiple lines
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.adjustResourceTitle = function adjustResourceTitle() {
  var resourceMenuLeft = this._$("#resourceMenuLeft");
  if (resourceMenuLeft.length) {
    var title = this._$("h1");
    var resourceMenuRightHeight = this._$("#resourceMenuRight").outerHeight(true);
    var resourceMenuLeftTopAdjustments = Math.min(0, title.outerHeight(true) - resourceMenuRightHeight);
    resourceMenuLeft.css("marginTop", resourceMenuLeftTopAdjustments + "px");
  }
};

function interceptEnterKey() {
  vrtxAdmin.cachedAppContent.delegate("form#editor input", "keypress", function (e) {
    if ((e.which && e.which == 13) || (e.keyCode && e.keyCode == 13)) {
      e.preventDefault(); // cancel the default browser click
    }
  });
}

function interceptEnterKeyAndReroute(txt, btn, cb) {
  vrtxAdmin.cachedAppContent.delegate(txt, "keypress", function (e) {
    if ((e.which && e.which == 13) || (e.keyCode && e.keyCode == 13)) {
      if ($(this).hasClass("blockSubmit")) { // submit/rerouting can be blocked elsewhere on textfield
        $(this).removeClass("blockSubmit");
      } else {
        $(btn).click(); // click the associated button
      }
      if(typeof cb === "function") {
        cb($(this));
      }
      e.preventDefault();
    }
  });
}

VrtxAdmin.prototype.mapShortcut = function mapShortcut(selectors, reroutedSelector) {
  this.cachedAppContent.on("click", selectors, function (e) {
    $(reroutedSelector).click();
    e.stopPropagation();
    e.preventDefault();
  });
};

VrtxAdmin.prototype.initStickyBar = function initStickyBar(wrapperId, stickyClass, extraWidth) {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  var wrapper = _$(wrapperId);
  var thisWindow = _$(window);
  if (wrapper.length && !vrtxAdm.isIPhone) { // Turn off for iPhone. 
    var wrapperPos = wrapper.offset();
    if (vrtxAdm.isIE8) {
      wrapper.append("<span class='sticky-bg-ie8-below'></span>");
    }
    thisWindow.on("scroll", function () {
      if (thisWindow.scrollTop() >= wrapperPos.top + 1) {
        if (!wrapper.hasClass(stickyClass)) {
          wrapper.addClass(stickyClass);
          vrtxAdmin.cachedContent.css("paddingTop", wrapper.outerHeight(true) + "px");
        }
        wrapper.css("width", (_$("#main").outerWidth(true) - 2 + extraWidth) + "px");
      } else {
        if (wrapper.hasClass(stickyClass)) {
          wrapper.removeClass(stickyClass);
          wrapper.css("width", "auto");
          vrtxAdmin.cachedContent.css("paddingTop", "0px");
        }
      }
    });
    thisWindow.on("resize", function () {
      if (thisWindow.scrollTop() >= wrapperPos.top + 1) {
        wrapper.css("width", (_$("#main").outerWidth(true) - 2 + extraWidth) + "px");
      }
    });
  }
};

VrtxAdmin.prototype.destroyStickyBar = function destroyStickyBar(wrapperId, stickyClass) {
  var _$ = this._$;
  
  var thisWindow = _$(window);
  thisWindow.off("scroll");
  thisWindow.off("resize");
  
  var wrapper = _$(wrapperId);
  
  if (wrapper.hasClass(stickyClass)) {
    wrapper.removeClass(stickyClass);
    wrapper.css("width", "auto");
    vrtxAdmin.cachedContent.css("paddingTop", "0px");
  }
};

VrtxAdmin.prototype.logoutButtonAsLink = function logoutButtonAsLink() {
  var _$ = this._$;

  var btn = _$('input#logoutAction');
  if (!btn.length) return;
  btn.hide();
  btn.after('&nbsp;<a id=\"logoutAction.link\" name=\"logoutAction\" href="javascript:void(0);">' + btn.attr('value') + '</a>');
  _$("#app-head-wrapper").on("click", '#logoutAction\\.link', function (e) {
    btn.click();
    e.stopPropagation();
    e.preventDefault();
  });
};


/*-------------------------------------------------------------------*\
    5. Create/File upload
       XXX: optimize more and needs more seperation
\*-------------------------------------------------------------------*/

function createFuncComplete() {
  var vrtxAdm = vrtxAdmin;

  vrtxAdm.cachedDoc.on("keyup", "#vrtx-textfield-collection-title input", $.debounce(50, true, function () {
    createTitleChange($(this), $("#vrtx-textfield-collection-name input"), null);
  }));
  vrtxAdm.cachedDoc.on("keyup", "#vrtx-textfield-file-title input", $.debounce(50, true, function () {
    createTitleChange($(this), $("#vrtx-textfield-file-name input"), $("#isIndex"));
  }));
  vrtxAdm.cachedDoc.on("keyup", "#vrtx-textfield-file-name input, #vrtx-textfield-collection-name input", $.debounce(50, true, function () {
    createFileNameChange($(this));
  }));

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
    $("<a id='vrtx-create-templates-not-recommended-toggle' href='javascript:void(0);'>Vis flere maler</a>").insertBefore(notRecommendedTemplates);
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
        createCheckUncheckIndexFile($("#vrtx-textfield-file-name input"), indexCheckbox);
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
    nameField.val('index');
    growField(nameField, 'index', 5, 35, 530);

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
    var nameFieldVal = replaceInvalidChar(titleField.val());
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

  var currentCaretPos = getCaretPos(nameField[0]);

  var nameFieldValBeforeReplacement = nameField.val();
  var nameFieldVal = replaceInvalidChar(nameFieldValBeforeReplacement);
  nameField.val(nameFieldVal);
  growField(nameField, nameFieldVal, 5, 100, 530);

  setCaretToPos(nameField[0], currentCaretPos - (nameFieldValBeforeReplacement.length - nameFieldVal.length));

  $(".file-name-from-title").removeClass("file-name-from-title");
}

function replaceInvalidChar(val) {
  val = val.toLowerCase();
  var replaceMap = {
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

  for (var key in replaceMap) {
    var replaceThisCharGlobally = new RegExp(key, "g");
    val = val.replace(replaceThisCharGlobally, replaceMap[key]);
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
VrtxAdmin.prototype.initFileUpload = function initFileUpload() {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;
  var form = _$("form[name=fileUploadService]");
  if (!form.length) return;
  var inputFile = form.find("#file");

  _$("<div class='vrtx-textfield vrtx-file-upload'><input id='fake-file' type='text' /><a class='vrtx-button vrtx-file-upload'><span>Browse...</span></a></div>'")
    .insertAfter(inputFile);

  inputFile.addClass("js-on").change(function (e) {
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

  inputFile.hover(function () {
    _$("a.vrtx-file-upload").addClass("hover");
  }, function () {
    _$("a.vrtx-file-upload").removeClass("hover");
  });

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

/**
 * Check if browser supports 'multiple' attribute
 * Credits: http://miketaylr.com/code/input-type-attr.html (MIT license)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.supportsMultipleAttribute = function supportsMultipleAttribute(inputfield) {
  return ( !! (inputfield.multiple === false) && !! (inputfield.multiple !== "undefined"));
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

  vrtxAdmin.cachedAppContent.on("click", "#vrtx-checkbox-is-index input", function (e) {
    createCheckUncheckIndexFile($("#vrtx-textfield-file-name input"), $(this));
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
              dialogTemplate.find(".vrtx-focus-button button").trigger("click");
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
          dialogTemplate.find(".vrtx-focus-button button").trigger("click");
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
    vrtxAdm.cachedDirectoryListing.find("th.checkbox").append("<input type='checkbox' name='checkUncheckAll' />");
  }
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
 * @param {boolean} useTitle Whether to use title- instead of name-attribute
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

function editorInteraction(bodyId, vrtxAdm, _$) {
  if (_$("form#editor").length) {
    // Dropdowns
    vrtxAdm.dropdownPlain("#editor-help-menu");
    vrtxAdm.dropdown({
      selector: "ul#editor-menu"
    });

    // Save shortcut and AJAX
    vrtxAdm.cachedDoc.bind('keydown', 'ctrl+s', $.debounce(150, true, function (e) {
      ctrlSEventHandler(_$, e);
    }));
    
    // Save
    vrtxAdm.cachedAppContent.on("click", ".vrtx-save-button input", function (e) {
      var link = _$(this);
      vrtxAdm.editorSaveButtonName = link.attr("name");
      var isRedirectView = (this.id === "saveAndViewButton" || this.id === "saveViewAction");
      ajaxSave();
      $.when(vrtxAdm.asyncEditorSavedDeferred).done(function () {
        vrtxAdm.removeMsg("error");
        if(isRedirectView) {
          var isCollection = $("#resource-title.true").length;
          if(isCollection) {
            location.href = "./?vrtx=admin&action=preview";
          } else {
            location.href = location.pathname + "/?vrtx=admin";
          }
        }
      }).fail(function (xhr, textStatus) {
        if (xhr !== null) {
          /* Fail in performSave() for exceeding 1500 chars in intro/add.content is handled in editor.js with popup */

          var msg = vrtxAdmin.serverFacade.error(xhr, textStatus, false);
          if(msg === "RE_AUTH") {
            reAuthenticateRetokenizeForms(link);
          } else if(msg === "LOCKED") {
            var d = new VrtxMsgDialog({
              msg: vrtxAdm.serverFacade.errorMessages.lockStolen,
              title: vrtxAdm.serverFacade.errorMessages.lockStolenTitle
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
      });
      e.stopPropagation();
      e.preventDefault();
    });
  }
}

function ctrlSEventHandler(_$, e) {
  if (!_$("#dialog-loading:visible").length) {
    _$(".vrtx-focus-button:last input").click();
  }
  e.preventDefault();
  return false;
}

function ajaxSave() {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  vrtxAdm.asyncEditorSavedDeferred = _$.Deferred();

  if (typeof CKEDITOR !== "undefined") {
    for (var instance in CKEDITOR.instances) {
      CKEDITOR.instances[instance].updateElement();
    }
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
  
  // TODO: rootUrl and jQueryUiVersion should be retrieved from Vortex config/properties somehow
  var rootUrl = "/vrtx/__vrtx/static-resources";
  var futureFormAjax = $.Deferred();
  if (typeof $.fn.ajaxSubmit !== "function") {
    $.getScript(rootUrl + "/jquery/plugins/jquery.form.js", function () {
      futureFormAjax.resolve();
    });
  } else {
    futureFormAjax.resolve();
  }
  $.when(futureFormAjax).done(function() {
    _$("#editor").ajaxSubmit({
      success: function () {
        var endTime = new Date() - startTime;
        var waitMinMs = 800;
        if (endTime >= waitMinMs) { // Wait minimum 0.8s
          d.close();
          vrtxAdm.asyncEditorSavedDeferred.resolve();
        } else {
          setTimeout(function () {
            d.close();
            vrtxAdm.asyncEditorSavedDeferred.resolve();
          }, Math.round(waitMinMs - endTime));
        }
      },
      error: function (xhr, textStatus, errMsg) {
        d.close();
        vrtxAdm.asyncEditorSavedDeferred.rejectWith(this, [xhr, textStatus]);
      }
    });
  });
}

function reAuthenticateRetokenizeForms(link) {  
  // Open reauth dialog
  var d = new VrtxHtmlDialog({
    name: "reauth-open",
    html: vrtxAdmin.serverFacade.errorMessages.sessionInvalid,
    title: vrtxAdmin.serverFacade.errorMessages.sessionInvalidTitle,
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
              retokenizeFormsOpenSaveDialog(link, d2);
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

function retokenizeFormsOpenSaveDialog(link, d2) {
  // Repopulate tokens
  var current = $("body input[name='csrf-prevention-token']");
  var currentLen = current.length;
  vrtxAdmin.serverFacade.getHtml(location.href, {
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
        html: vrtxAdmin.serverFacade.errorMessages.sessionValidated,
        title: vrtxAdmin.serverFacade.errorMessages.sessionValidatedTitle,
        onOk: function() {
          // Trigger save
          link.click();
        },
        btnTextOk: vrtxAdmin.serverFacade.errorMessages.sessionValidatedOk
      });
      d.open();
    }
  });
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
  vrtxAdmin.cachedAppContent.delegate("." + selectorClass + " ul.shortcuts label[for=custom]", "click", function (e) {
    var elem = $(this).closest("form").find(".principalList.hidden");
    customConfigAnimation.updateElem(elem);
    customConfigAnimation.topDown();
    e.stopPropagation();
  });
  vrtxAdmin.cachedAppContent.delegate("." + selectorClass + " ul.shortcuts label:not([for=custom])", "click", function (e) {
    var elem = $(this).closest("form").find(".principalList:not(.hidden)");
    customConfigAnimation.updateElem(elem);
    customConfigAnimation.bottomUp();
    e.stopPropagation();
  });
}

function checkStillAdmin(options) {
  var stillAdmin = options.form.find(".still-admin").text();
  vrtxAdmin.reloadFromServer = false;
  if (stillAdmin == "false") {
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

function autocompleteUsernames(selector) {
  var _$ = vrtxAdmin._$;
  var autocompleteTextfields = _$(selector).find('.vrtx-textfield input');
  var i = autocompleteTextfields.length;
  while (i--) {
    permissionsAutocomplete(_$(autocompleteTextfields[i]).attr("id"), 'userNames', vrtxAdmin.usernameAutocompleteParams, true);
  }
}

function autocompleteUsername(selector, subselector) {
  var autocompleteTextfield = vrtxAdmin._$(selector).find('input#' + subselector);
  if (autocompleteTextfield.length) {
    permissionsAutocomplete(subselector, 'userNames', vrtxAdmin.usernameAutocompleteParams, true);
  }
}

function autocompleteTags(selector) {
  var _$ = vrtxAdmin._$;
  var autocompleteTextfields = _$(selector).find('.vrtx-textfield input');
  var i = autocompleteTextfields.length;
  while (i--) {
    setAutoComplete(_$(autocompleteTextfields[i]).attr("id"), 'tags', vrtxAdmin.tagAutocompleteParams);
  }
}


/*-------------------------------------------------------------------*\
    9. Versioning
\*-------------------------------------------------------------------*/

function versioningInteraction(bodyId, vrtxAdm, _$) {
  vrtxAdm.cachedAppContent.on("click", "a.vrtx-revision-view", function (e) {
    var openedRevision = openRegular(this.href, 1020, 800, "DisplayRevision");
    e.stopPropagation();
    e.preventDefault();
  });

  if (bodyId == "vrtx-revisions") {

    // Delete revisions
    vrtxAdm.cachedContent.on("click", ".vrtx-revisions-delete-form input[type=submit]", function (e) {
      var link = _$(this);
      var form = link.closest("form");
      var url = form.attr("action");
      var dataString = form.serialize();
      vrtxAdm.serverFacade.postHtml(url, dataString, {
        success: function (results, status, resp) {
          var tr = form.closest("tr");
          if (vrtxAdm.animateTableRows) {
            tr.prepareTableRowForSliding().hide(0).slideDown(0, "linear");
          }
          // Check when multiple animations are complete; credits: http://tinyurl.com/83oodnp
          var animA = tr.find("td").animate({
            paddingTop: '0px',
            paddingBottom: '0px'
          },
          vrtxAdm.transitionDropdownSpeed, vrtxAdm.transitionEasingSlideUp, _$.noop);
          var animB = tr.slideUp(vrtxAdm.transitionDropdownSpeed, vrtxAdm.transitionEasingSlideUp, _$.noop);
          _$.when(animA, animB).done(function () {
            var result = _$($.parseHTML(results));
            vrtxAdm.cachedContent.html(result.find("#contents").html());
            _$("#app-tabs").html(result.find("#app-tabs").html());
          });
        }
      });
      e.stopPropagation();
      e.preventDefault();
    });

    // Restore revisions
    vrtxAdm.cachedContent.on("click", ".vrtx-revisions-restore-form input[type=submit]", function (e) {
      var link = _$(this);
      var form = link.closest("form");
      var url = form.attr("action");
      var dataString = form.serialize();
      _$("td.vrtx-revisions-buttons-column input").attr("disabled", "disabled"); // Lock buttons
      vrtxAdm.serverFacade.postHtml(url, dataString, {
        success: function (results, status, resp) {
          vrtxAdm.cachedContent.html($($.parseHTML(results)).find("#contents").html());
          if (typeof versionsRestoredInfoMsg !== "undefined") {
            var revisionNr = url.substring(url.lastIndexOf("=") + 1, url.length);
            var versionsRestoredInfoMsgTmp = versionsRestoredInfoMsg.replace("X", revisionNr);
            vrtxAdm.displayInfoMsg(versionsRestoredInfoMsgTmp);
          }
          scroll(0, 0);
        },
        error: function (xhr, textStatus) {
          _$("td.vrtx-revisions-buttons-column input").removeAttr("disabled"); // Unlock buttons
        }
      });
      e.stopPropagation();
      e.preventDefault();
    });

    // Make working copy into current version
    vrtxAdm.cachedContent.on("click", "#vrtx-revisions-make-current-form input[type=submit]", function (e) {
      var link = _$(this);
      var form = link.closest("form");
      var url = form.attr("action");
      var dataString = form.serialize();
      vrtxAdm.serverFacade.postHtml(url, dataString, {
        success: function (results, status, resp) {
          vrtxAdm.cachedContent.html(_$($.parseHTML(results)).find("#contents").html());
          _$("#app-tabs").html(_$($.parseHTML(results)).find("#app-tabs").html());
          if (typeof versionsMadeCurrentInfoMsg !== "undefined") {
            vrtxAdm.displayInfoMsg(versionsMadeCurrentInfoMsg);
          }
        }
      });
      e.stopPropagation();
      e.preventDefault();
    });
  }
}


/*-------------------------------------------------------------------*\
    10. Async functions  
\*-------------------------------------------------------------------*/

/**
 * Retrieve a form async
 * 
 * XXX: need some consolidating of callback functions and class-filtering for getting existing form
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuration
 * @param {string} options.selector Selector for links that should retrieve a form async
 * @param {string} options.selectorClass Selector for form
 * @param {string} options.insertAfterOrReplaceClass Where to put the form
 * @param {boolean} options.isReplacing Whether to replace instead of insert after
 * @param {string} options.nodeType Node type that should be replaced or inserted
 * @param {function} options.funcComplete Callback function to run on success
 * @param {boolean} options.simultanSliding Whether to slideUp existing form at the same time slideDown new form (only when there is an existing form)
 * @param {number} options.transitionSpeed Transition speed in ms
 * @param {string} options.transitionEasingSlideDown Transition easing algorithm for slideDown()
 * @param {string} options.transitionEasingSlideUp Transition easing algorithm for slideUp()
 * @return {boolean} Whether or not to proceed with regular link operation
 */
VrtxAdmin.prototype.getFormAsync = function getFormAsync(options) {
  var args = arguments, // this function
    vrtxAdm = this, // use prototypal hierarchy 
    _$ = vrtxAdm._$;

  vrtxAdm.cachedBody.dynClick(options.selector, function (e) {
    var link = _$(this);
    var url = link.attr("href") || link.closest("form").attr("action");

    if (vrtxAdm.asyncGetFormsInProgress) { // If there are any getFormAsync() in progress
      return false;
    }
    vrtxAdm.asyncGetFormsInProgress++;

    var selector = options.selector,
      selectorClass = options.selectorClass,
      simultanSliding = options.simultanSliding,
      transitionSpeed = options.transitionSpeed,
      transitionEasingSlideDown = options.transitionEasingSlideDown,
      transitionEasingSlideUp = options.transitionEasingSlideUp,
      modeUrl = location.href,
      fromModeToNotMode = false,
      existExpandedFormIsReplaced = false,
      expandedForm = $(".expandedForm"),
      existExpandedForm = expandedForm.length;

    // Make sure we get the mode markup (current page) if service is not mode
    // -- only if a expandedForm exists and is of the replaced kind..
    //
    if (existExpandedForm && expandedForm.hasClass("expandedFormIsReplaced")) {
      if (url.indexOf("&mode=") == -1 && modeUrl.indexOf("&mode=") != -1) {
        fromModeToNotMode = true;
      }
      existExpandedFormIsReplaced = true;
    }

    vrtxAdmin.serverFacade.getHtml(url, {
      success: function (results, status, resp) {
        var form = _$(_$.parseHTML(results)).find("." + selectorClass).html();

        // If something went wrong
        if (!form) {
          vrtxAdm.error({
            args: args,
            msg: "retrieved form from " + url + " is null"
          });
          if (vrtxAdm.asyncGetFormsInProgress) {
            vrtxAdm.asyncGetFormsInProgress--;
          }
          return;
        }
        // Another form is already open
        if (existExpandedForm) {
          // Get class for original markup
          var resultSelectorClasses = expandedForm.attr("class").split(" ");
          var resultSelectorClass = "";
          var ignoreClasses = {
            "even": "",
            "odd": "",
            "first": "",
            "last": ""
          };
          for (var i = resultSelectorClasses.length; i--;) {
            var resultSelectorClassCache = resultSelectorClasses[i];
            if (resultSelectorClassCache && resultSelectorClassCache !== "" && !(resultSelectorClassCache in ignoreClasses)) {
              resultSelectorClass = "." + resultSelectorClasses[i];
              break;
            }
          }
          var succeededAddedOriginalMarkup = true;
          var animation = new VrtxAnimation({
            elem: expandedForm,
            animationSpeed: transitionSpeed,
            easeIn: transitionEasingSlideDown,
            easeOut: transitionEasingSlideUp,
            afterOut: function(animation) {
              if (existExpandedFormIsReplaced) {
                if (fromModeToNotMode) { // When we need the 'mode=' HTML when requesting a 'not mode=' service
                  vrtxAdmin.serverFacade.getHtml(modeUrl, {
                    success: function (results, status, resp) {
                      var succeededAddedOriginalMarkup = vrtxAdm.addOriginalMarkup(modeUrl, _$.parseHTML(results), resultSelectorClass, expandedForm);
                      if (succeededAddedOriginalMarkup) {
                        vrtxAdmin.addNewMarkup(options, selectorClass, transitionSpeed, transitionEasingSlideDown, transitionEasingSlideUp, form);
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
                if (node.is("tr") && vrtxAdmin.animateTableRows) { // Because 'this' can be tr > td > div
                  node.remove();
                } else {
                  animation.__opts.elem.remove();
                }
              }
              if (!simultanSliding && !fromModeToNotMode) {
                if (!succeededAddedOriginalMarkup) {
                  if (vrtxAdmin.asyncGetFormsInProgress) {
                    vrtxAdmin.asyncGetFormsInProgress--;
                  }
                } else {
                  vrtxAdmin.addNewMarkup(options, selectorClass, transitionSpeed, transitionEasingSlideDown, transitionEasingSlideUp, form);
                }
              }
            }
          });
          animation.bottomUp();
        }
        if ((!existExpandedForm || simultanSliding) && !fromModeToNotMode) {
          vrtxAdm.addNewMarkup(options, selectorClass, transitionSpeed, transitionEasingSlideDown, transitionEasingSlideUp, form);
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
 * @param {object} expanded The expanded form
 * @return {boolean} Whether it succeeded or not
 */
VrtxAdmin.prototype.addOriginalMarkup = function addOriginalMarkup(url, results, resultSelectorClass, expanded) {
  var args = arguments,
    vrtxAdm = this;

  var resultHtml = vrtxAdm.outerHTML(results, resultSelectorClass);
  if (!resultHtml) { // If all went wrong
    vrtxAdm.error({
      args: args,
      msg: "trying to retrieve existing expandedForm from " + url + " returned null"
    });
    return false;
  }
  var node = expanded.parent().parent();
  if (node.is("tr") && vrtxAdm.animateTableRows) { // Because 'this' can be tr > td > div
    node.replaceWith(resultHtml).show(0);
  } else {
    expanded.replaceWith(resultHtml).show(0);
  }
  return true;
};

/**
 * Add new form markup after async retrieve
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuration
 * @param {string} selectorClass The selector for form
 * @param {string} transitionSpeed Transition speed in ms
 * @param {string} transitionEasingSlideDown Transition easing algorithm for slideDown()
 * @param {string} transitionEasingSlideUp Transition easing algorithm for slideUp()
 * @param {object} form The form
 */
VrtxAdmin.prototype.addNewMarkup = function addNewMarkup(options, selectorClass, transitionSpeed, transitionEasingSlideDown, transitionEasingSlideUp, form) {
  var vrtxAdm = this,
    insertAfterOrReplaceClass = options.insertAfterOrReplaceClass,
    secondaryInsertAfterOrReplaceClass = options.secondaryInsertAfterOrReplaceClass,
    isReplacing = options.isReplacing || false,
    nodeType = options.nodeType,
    funcComplete = options.funcComplete,
    _$ = vrtxAdm._$;

  var inject = _$(insertAfterOrReplaceClass);
  if (!inject.length) {
    inject = _$(secondaryInsertAfterOrReplaceClass);
  }

  if (isReplacing) {
    var classes = inject.attr("class");
    inject.replaceWith(vrtxAdm.wrap(nodeType, "expandedForm expandedFormIsReplaced nodeType" + nodeType + " " + selectorClass + " " + classes, form));
  } else {
    _$(vrtxAdm.wrap(nodeType, "expandedForm nodeType" + nodeType + " " + selectorClass, form))
      .insertAfter(inject);
  }
  if (funcComplete) {
    funcComplete(selectorClass);
  }
  if (vrtxAdm.asyncGetFormsInProgress) {
    vrtxAdm.asyncGetFormsInProgress--;
  }
  if (nodeType == "tr" && vrtxAdm.animateTableRows) {
    _$(nodeType + "." + selectorClass).prepareTableRowForSliding();
  }
  
  var animation = new VrtxAnimation({
    elem: $(nodeType + "." + selectorClass).hide(),
    animationSpeed: transitionSpeed,
    easeIn: transitionEasingSlideDown,
    easeOut: transitionEasingSlideUp,
    afterIn: function(animation) {
      animation.__opts.elem.find("input[type=text]:visible:first").focus();
    }
  });
  animation.topDown();
};

/**
 * Complete a form async
 * 
 * XXX: need some consolidating of callback functions
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuration
 * @param {string} options.selector Selector for links that should complete a form async
 * @param {boolean} options.isReplacing Whether to replace instead of insert after
 * @param {string} options.updateSelectors One or more containers that should update after POST
 * @param {string} options.errorContainerInsertAfter Selector where to place the new error container
 * @param {string} options.errorContainer The className of the error container
 * @param {function} options.funcProceedCondition Callback function that proceedes with completeFormAsyncPost(options)
 * @param {function} options.funcComplete Callback function to run on success
 * @param {number} options.transitionSpeed Transition speed in ms
 * @param {string} options.transitionEasingSlideDown Transition easing algorithm for slideDown()
 * @param {string} options.transitionEasingSlideUp Transition easing algorithm for slideUp()
 * @param {boolean} options.post POST or only cancel
 * @return {boolean} Whether or not to proceed with regular link operation
 */
VrtxAdmin.prototype.completeFormAsync = function completeFormAsync(options) {
  var args = arguments,
    vrtxAdm = this,
    _$ = vrtxAdm._$;

  vrtxAdm.cachedBody.dynClick(options.selector, function (e) {

    var isReplacing = options.isReplacing || false,
      funcBeforeComplete = options.funcBeforeComplete,
      funcProceedCondition = options.funcProceedCondition,
      funcComplete = options.funcComplete,
      transitionSpeed = options.transitionSpeed,
      transitionEasingSlideDown = options.transitionEasingSlideDown,
      transitionEasingSlideUp = options.transitionEasingSlideUp,
      post = options.post || false,
      link = _$(this),
      isCancelAction = link.attr("name").toLowerCase().indexOf("cancel") != -1;

    if (!post) {
      if (isCancelAction && !isReplacing) {
        var animation = new VrtxAnimation({
          elem: $(".expandedForm"),
          animationSpeed: transitionSpeed,
          easeIn: transitionEasingSlideDown,
          easeOut: transitionEasingSlideUp,
          afterOut: function(animation) {
            animation.__opts.elem.remove();
          }
        });
        animation.bottomUp();
        e.preventDefault();
      } else {
        e.stopPropagation();
        if(!isCancelAction && funcBeforeComplete) {
          funcBeforeComplete();
        }
        if (funcComplete && !isCancelAction) {
          var returnVal = funcComplete(link);
          return returnVal;
        } else {
          return;
        }
      }
    } else {
      options.form = link.closest("form");
      options.link = link;
      if (!isCancelAction && funcProceedCondition) {
        funcProceedCondition(options);
      } else {
        vrtxAdm.completeFormAsyncPost(options);
      }
      e.stopPropagation();
      e.preventDefault();
    }
  });
};

/**
 * Complete a form async POST
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuration
 * @param {object} options.form The form
 * @param {object} options.link The action link
 */
VrtxAdmin.prototype.completeFormAsyncPost = function completeFormAsyncPost(options) {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$,
    selector = options.selector,
    isReplacing = options.isReplacing || false,
    isUndecoratedService = options.isUndecoratedService || false,
    updateSelectors = options.updateSelectors,
    errorContainer = options.errorContainer,
    errorContainerInsertAfter = options.errorContainerInsertAfter,
    funcBeforeComplete = options.funcBeforeComplete,
    funcComplete = options.funcComplete,
    transitionSpeed = options.transitionSpeed,
    transitionEasingSlideDown = options.transitionEasingSlideDown,
    transitionEasingSlideUp = options.transitionEasingSlideUp,
    form = options.form,
    link = options.link,
    url = form.attr("action");
  
  if(funcBeforeComplete) {
    funcBeforeComplete();
  }
  
  var dataString = form.serialize() + "&" + link.attr("name"),
      modeUrl = location.href;

  vrtxAdmin.serverFacade.postHtml(url, dataString, {
    success: function (results, status, resp) {
      if (vrtxAdm.hasErrorContainers(_$.parseHTML(results), errorContainer)) {
        vrtxAdm.displayErrorContainers(_$.parseHTML(results), form, errorContainerInsertAfter, errorContainer);
      } else {
        if (isReplacing) {
          var animation = new VrtxAnimation({
            elem: form.parent(),
            animationSpeed: transitionSpeed,
            easeIn: transitionEasingSlideDown,
            easeOut: transitionEasingSlideUp,
            afterOut: function(animation) {
              for (var i = updateSelectors.length; i--;) {
                var outer = vrtxAdm.outerHTML(_$.parseHTML(results), updateSelectors[i]);
                vrtxAdm.cachedBody.find(updateSelectors[i]).replaceWith(outer);
              }
              var resultsResourceTitle = _$($.parseHTML(results)).find("#resource-title");
              var currentResourceTitle = vrtxAdm.cachedBody.find("#resource-title");
              if (resultsResourceTitle.length && currentResourceTitle.length) {
                if (resultsResourceTitle.hasClass("compact") && !currentResourceTitle.hasClass("compact")) {
                  currentResourceTitle.addClass("compact");
                } else if (!resultsResourceTitle.hasClass("compact") && currentResourceTitle.hasClass("compact")) {
                  currentResourceTitle.removeClass("compact");
                }
              }
              if (funcComplete) {
                funcComplete();
              }
            }
          });
          animation.bottomUp();
        } else {
          var sameMode = false;
          if (url.indexOf("&mode=") !== -1) {
            if (gup("mode", url) === gup("mode", modeUrl)) {
              sameMode = true;
            }
          }
          if (isUndecoratedService || (modeUrl.indexOf("&mode=") !== -1 && !sameMode)) { // When we need the 'mode=' HTML. TODO: should only run when updateSelector is inside content
            vrtxAdmin.serverFacade.getHtml(modeUrl, {
              success: function (results, status, resp) {
                for (var i = updateSelectors.length; i--;) {
                  var outer = vrtxAdm.outerHTML(_$.parseHTML(results), updateSelectors[i]);
                  vrtxAdm.cachedBody.find(updateSelectors[i]).replaceWith(outer);
                }
                var resultsResourceTitle = _$($.parseHTML(results)).find("#resource-title");
                var currentResourceTitle = vrtxAdm.cachedBody.find("#resource-title");
                if (resultsResourceTitle.length && currentResourceTitle.length) {
                  if (resultsResourceTitle.hasClass("compact") && !currentResourceTitle.hasClass("compact")) {
                    currentResourceTitle.addClass("compact");
                  } else if (!resultsResourceTitle.hasClass("compact") && currentResourceTitle.hasClass("compact")) {
                    currentResourceTitle.removeClass("compact");
                  }
                }
                if (funcComplete) {
                  funcComplete();
                }
                var animation = new VrtxAnimation({
                  elem: form.parent(),
                  animationSpeed: transitionSpeed,
                  easeIn: transitionEasingSlideDown,
                  easeOut: transitionEasingSlideUp,
                  afterOut: function(animation) {
                    animation.__opts.elem.remove();
                  }
                });
                animation.bottomUp();
              }
            });
          } else {
            for (var i = updateSelectors.length; i--;) {
              var outer = vrtxAdm.outerHTML(_$.parseHTML(results), updateSelectors[i]);
              vrtxAdm.cachedBody.find(updateSelectors[i]).replaceWith(outer);
            }
            var resultsResourceTitle = _$(_$.parseHTML(results)).find("#resource-title");
            var currentResourceTitle = vrtxAdm.cachedBody.find("#resource-title");
            if (resultsResourceTitle.length && currentResourceTitle.length) {
              if (resultsResourceTitle.hasClass("compact") && !currentResourceTitle.hasClass("compact")) {
                currentResourceTitle.addClass("compact");
              } else if (!resultsResourceTitle.hasClass("compact") && currentResourceTitle.hasClass("compact")) {
                currentResourceTitle.removeClass("compact");
              }
            }
            if (funcComplete) {
              funcComplete();
            }
            var animation = new VrtxAnimation({
              elem: form.parent(),
              animationSpeed: transitionSpeed,
              easeIn: transitionEasingSlideDown,
              easeOut: transitionEasingSlideUp,
              afterOut: function(animation) {
                animation.__opts.elem.remove();
              }
            });
            animation.bottomUp();
          }
        }
      }
    }
  });
};

/**
 * Remove permission async
 *
 * @this {VrtxAdmin}
 * @param {string} selector Selector for links that should do removal async
 * @param {string} updateSelector The selector for container to be updated on success
 */
VrtxAdmin.prototype.removePermissionAsync = function removePermissionAsync(selector, updateSelector) {
  var args = arguments,
    vrtxAdm = this,
    _$ = vrtxAdm._$;

  vrtxAdm.cachedAppContent.on("click", selector, function (e) {
    var link = _$(this);
    var form = link.closest("form");
    var url = form.attr("action");
    var listElement = link.parent();

    var dataString = "&csrf-prevention-token=" + form.find("input[name='csrf-prevention-token']").val() +
      "&" + escape(link.attr("name"));

    vrtxAdmin.serverFacade.postHtml(url, dataString, {
      success: function (results, status, resp) {
        form.find(updateSelector).html(_$($.parseHTML(results)).find(updateSelector).html());
        initSimplifiedPermissionForm();
      }
    });
    e.preventDefault();
  });
};

/**
 * Add permission async
 *
 * @this {VrtxAdmin}
 * @param {string} selector Selector for links that should do add async
 * @param {string} updateSelector The selector for container to be updated on success
 * @param {string} errorContainerInsertAfter Selector where to place the new error container
 * @param {string} errorContainer The className of the error container
 */
VrtxAdmin.prototype.addPermissionAsync = function addPermissionAsync(selector, updateSelector, errorContainerInsertAfter, errorContainer) {
  var args = arguments,
    vrtxAdm = this,
    _$ = vrtxAdm._$;

  vrtxAdm.cachedAppContent.on("click", selector + " input[type=submit]", function (e) {
    var link = _$(this);
    var form = link.closest("form");
    var url = form.attr("action");
    var parent = link.parent().parent();
    var textfield = parent.find("input[type=text]");
    var textfieldName = textfield.attr("name");
    var textfieldVal = textfield.val();
    var dataString = textfieldName + "=" + textfieldVal +
      "&csrf-prevention-token=" + form.find("input[name='csrf-prevention-token']").val() +
      "&" + link.attr("name");

    var hiddenAC = parent.find("input#ac_userNames");
    if (hiddenAC.length) {
      var hiddenACName = hiddenAC.attr("name");
      var hiddenACVal = hiddenAC.val();
      dataString += "&" + hiddenACName + "=" + hiddenACVal;
    }

    vrtxAdmin.serverFacade.postHtml(url, dataString, {
      success: function (results, status, resp) {
        if (vrtxAdm.hasErrorContainers(_$.parseHTML(results), errorContainer)) {
          vrtxAdm.displayErrorContainers(_$.parseHTML(results), form, errorContainerInsertAfter, errorContainer);
        } else {
          var upSelector = form.find(updateSelector);
          upSelector.parent().find("div." + errorContainer).remove();
          upSelector.html(_$(_$.parseHTML(results)).find(updateSelector).html());
          textfield.val("");
          initSimplifiedPermissionForm();
        }
      }
    });
    e.preventDefault();
  });
};

/**
 * Retrieves HTML templates in a Mustache file seperated by ###
 *
 * @this {VrtxAdmin}
 * @param {string} fileName The filename for the Mustache file
 * @param {array} templateNames Preferred name of the templates
 * @param {object} templatesIsRetrieved Deferred
 * @return {array} Templates with templateName as hash
 */
VrtxAdmin.prototype.retrieveHTMLTemplates = function retrieveHTMLTemplates(fileName, templateNames, templatesIsRetrieved) {
  var templatesHashArray = [];
  vrtxAdmin.serverFacade.getText("/vrtx/__vrtx/static-resources/js/templates/" + fileName + ".mustache", {
    success: function (results, status, resp) {
      var templates = results.split("###");
      for (var i = 0, len = templates.length; i < len; i++) {
        templatesHashArray[templateNames[i]] = $.trim(templates[i]);
      }
      templatesIsRetrieved.resolve();
    }
  });
  return templatesHashArray;
};


/*-------------------------------------------------------------------*\
    11. Async helper functions and AJAX server façade   
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
    _$(outer).insertAfter(wrapper.find(errorContainerInsertAfter));
  }
};

/**
 * Display error message
 *
 * @this {VrtxAdmin}
 * @param {string} msg The message
 */
VrtxAdmin.prototype.displayErrorMsg = function displayErrorMsg(msg) {
  var vrtxAdm = this;
  if (!vrtxAdm.ignoreAjaxErrors) {
    vrtxAdm.displayMsg(msg, "error");
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
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var current = (type === "info") ? "infomessage" : "errormessage";
  var other = (type === "info") ? "errormessage" : "infomessage";

  var currentMsg = vrtxAdm.cachedAppContent.find("> ." + current);
  var otherMsg = vrtxAdm.cachedAppContent.find("> ." + other);
  if (typeof msg !== "undefined" && msg !== "") {
    if (currentMsg.length) {
      currentMsg.html(msg).fadeTo(100, 0.25).fadeTo(100, 1);
    } else if (otherMsg.length) {
      otherMsg.html(msg).removeClass(other).addClass(current).fadeTo(100, 0.25).fadeTo(100, 1);
    } else {
      vrtxAdm.cachedAppContent.prepend("<div class='" + current + " message'>" + msg + "</div>");
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
  var vrtxAdm = this,
    _$ = vrtxAdm._$;

  var current = (type === "info") ? "infomessage" : "errormessage";
  var currentMsg = vrtxAdm.cachedAppContent.find("> ." + current);
  if (currentMsg.length) {
    currentMsg.remove();
  }
};

/**
 * Display error message in dialog
 *
 * @this {VrtxAdmin}
 * @param {string} msg The message
 */
VrtxAdmin.prototype.displayDialogErrorMsg = function displayDialogErrorMsg(selector, msg) {
  var msgWrp = $(".dialog-error-msg");
  if(!msgWrp.length) {
    $("<p class='dialog-error-msg'>" + msg + "</p>").insertBefore(selector);
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
   */
  getHtml: function (url, callbacks) {
    this.get(url, callbacks, "html");
  },
  /**
   * GET JSON
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {object} callbacks The callback functions
   */
  getJSON: function (url, callbacks) {
    this.get(url, callbacks, "json");
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
   * POST JSON
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
   * GET Ajax <data type>
   *
   * @this {serverFacade}
   * @param {string} url The URL
   * @param {object} callbacks The callback functions
   * @param {string} type The data type
   */
  get: function (url, callbacks, type) {
    vrtxAdmin._$.ajax({
      type: "GET",
      url: url,
      dataType: type,
      success: callbacks.success,
      error: function (xhr, textStatus) {
        vrtxAdmin.displayErrorMsg(vrtxAdmin.serverFacade.error(xhr, textStatus, true));
        if (callbacks.error) {
          callbacks.error(xhr, textStatus);
        }
      },
      complete: function (xhr, testStatus) {
        if (callbacks.complete) {
          callbacks.complete(xhr, testStatus);
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
    vrtxAdmin._$.ajax({
      type: "POST",
      url: url,
      data: params,
      dataType: type,
      contentType: contentType,
      success: callbacks.success,
      error: function (xhr, textStatus) {
        vrtxAdmin.displayErrorMsg(vrtxAdmin.serverFacade.error(xhr, textStatus, true));
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
   * Error Ajax handler
   * 
   * XXX: More specific error-messages on what action that failed with function-origin
   *      
   * @this {serverFacade}
   * @param {object} xhr The XMLHttpRequest object
   * @param {string} textStatus The text status
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
      var serverFacade = this;
      vrtxAdmin._$.ajax({
        type: "GET",
        url: "/vrtx/__vrtx/static-resources/themes/default/images/globe.png?" + (+new Date()),
        async: false,
        success: function (results, status, resp) { // Online - Re-authentication needed
          msg = useStatusCodeInMsg ? serverFacade.errorMessages.sessionInvalid : "RE_AUTH";
        },
        error: function (xhr, textStatus) {         // Not Online
          msg = serverFacade.errorMessages.offline;
        }
      });
    } else if (status === 503 || (xhr.readyState === 4 && status === 200)) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.down;
    } else if (status === 500) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s500;
    } else if (status === 400) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s400;
    } else if (status === 401) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s401;
    } else if (status === 403) {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.s403;
    } else if (status === 404) {
      var serverFacade = this;
      vrtxAdmin._$.ajax({
        type: "GET",
        url: location.href,
        async: false,
        success: function (results, status, resp) { // Exists - soneone has locked it
          msg = useStatusCodeInMsg ? serverFacade.errorMessages.s404 : "LOCKED";
        },
        error: function (xhr, textStatus) {         // Removed/moved
          msg = (useStatusCodeInMsg ? status + " - " : "") + serverFacade.errorMessages.s404;
        }
      });
      
    } else {
      msg = (useStatusCodeInMsg ? status + " - " : "") + this.errorMessages.general + " " + textStatus;
    }
    return msg;
  },
  errorMessages: {} /* Populated with i18n in resource-bar.ftl */
};


/*-------------------------------------------------------------------*\
    12. CK browse server integration
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
  var sOptions = "toolbar=no,status=no,resizable=yes"; // http://www.quirksmode.org/js/popup.html
  return openGeneral(url, width, height, "BrowseServer", sOptions); // title must be without spaces in IE
}

function openRegular(url, width, height, winTitle) {
  var sOptions = "toolbar=yes,status=yes,resizable=yes";
  sOptions += ",location=yes,menubar=yes,scrollbars=yes";
  sOptions += ",directories=yes";
  var now = +new Date();
  return openGeneral(url, width, height, winTitle + now, sOptions);
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

// Callback from the CKEditor image browser:
function SetUrl(url) {
  url = decodeURIComponent(url);
  if (urlobj) {
    document.getElementById(urlobj).value = url;
  }
  oWindow = null;
  if (typestr === "Image" && typeof previewImage !== "undefined") {
    previewImage(urlobj);
  }
  urlobj = ""; // NB: reset global vars
  typestr = "";
}


/*-------------------------------------------------------------------*\
    13. Utils
\*-------------------------------------------------------------------*/

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
  return this._$.parseHTML("<" + node + " class='" + cls + "'>" + html +
    "</" + node + ">");
};

/**
 * jQuery outerHTML (because FF don't support regular outerHTML) 
 *
 * @this {VrtxAdmin}
 * @param {string} selector Context selector
 * @param {string} subselector The node to get outer HTML from
 * @return {string} outer HTML
 */
VrtxAdmin.prototype.outerHTML = function outerHTML(selector, subselector) {
  var _$ = this._$;
  
  if (_$(selector).find(subselector).length) {
    if (typeof _$(selector).find(subselector)[0].outerHTML !== "undefined") {
      return _$.parseHTML(_$(selector).find(subselector)[0].outerHTML);
    } else {
      return _$.parseHTML(_$('<div>').append(_$(selector).find(subselector).clone()).html());
    }
  }
};

/**
 * Load script Async / lazy-loading
 *
 * @this {VrtxAdmin}
 * @param {string} url The url to the script
 * @param {function} callback Callback function to run on success
 */
VrtxAdmin.prototype.loadScript = function loadScript(url, callback) {
  $.cachedScript(url).done(callback).fail(function (jqxhr, settings, exception) {
    vrtxAdmin.log({
      msg: exception
    });
  });
};

/**
 * Log to console with calle.name if exists (Function name)
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuraton
 * @param {string} options.msg The message
 * @param {array} options.args Arguments
 */
VrtxAdmin.prototype.log = function log(options) {
  if (vrtxAdmin.hasConsoleLog) {
    var msgMid = options.args ? " -> " + options.args.callee.name : "";
    console.log("Vortex admin log" + msgMid + ": " + options.msg);
  }
};

/**
 * Error to console with calle.name if exists (Function name)
 * with fallback to regular log
 *
 * @this {VrtxAdmin}
 * @param {object} options Configuraton
 * @param {string} options.msg The message
 * @param {array} options.args Arguments
 */
VrtxAdmin.prototype.error = function error(options) {
  if (vrtxAdmin.hasConsoleError) {
    var msgMid = options.args ? " -> " + options.args.callee.name : "";
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
  if ((vrtxAdmin.isIE && vrtxAdmin.browserVersion < 9) || vrtxAdmin.isOpera) { // http://www.quirksmode.org/css/contents.html
    table.find("tbody tr:odd").addClass("even"); // hmm.. somehow even is odd and odd is even
    table.find("tbody tr:first-child").addClass("first");
  }
};

/* Read a cookie
 *
 * Credits: http://www.javascripter.net/faq/readingacookie.htm
 *
 */
function readCookie(cookieName, defaultVal) {
  var match = (" " + document.cookie).match(new RegExp('[; ]' + cookieName + '=([^\\s;]*)'));
  return match ? unescape(match[1]) : defaultVal;
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
  var o = {}, i, l = array.length,
    r = [];
  for (i = 0; i < l; i += 1) o[array[i]] = array[i];
  for (i in o) r.push(o[i]);
  return r;
}


/*-------------------------------------------------------------------*\
    14. Override JavaScript / jQuery
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

var maxRuns = 0;
vrtxAdmin._$(window).resize(vrtxAdmin._$.throttle(150, function () {
  if (vrtxAdmin.runReadyLoad) {
    if (maxRuns < 2) {
      vrtxAdmin.scrollBreadcrumbsRight();
      vrtxAdmin.adjustResourceTitle();
      maxRuns++;
    } else {
      maxRuns = 0; /* IE8: let it rest */
    }
  }
}));

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