/*
 * Collection-listing and trash-can
 *
 * Listing is in the core of the Vortex - the center of the information storm!
 * Works like a web folder where you can:
 * Create, Copy/move, Delete, Upload and Publish
 *
 * In the trash-can you can: Restore or Permanently delete resources
 *
 */

$.when(vrtxAdmin.domainsInstantIsReady).done(function() {
  var vrtxAdm = vrtxAdmin, _$ = vrtxAdm._$;

  switch (vrtxAdm.bodyId) {
    case "vrtx-trash-can":
    case "vrtx-manage-collectionlisting":
      vrtxAdm.collectionListingInteraction();
      break;
    default: // noop
      break;
  }
});

$.when(vrtxAdmin.domainsIsReady).done(function() {
  var vrtxAdm = vrtxAdmin, _$ = vrtxAdm._$;

  switch (vrtxAdm.bodyId) {
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
              funcComplete: createFuncComplete,
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
              initDragAndDropUpload({
                uploadSeviceSelector: "#fileUploadService",
                contentSelector: "#contents",
                formSelector: "form#fileUploadService-form"
              });
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
      var dialogDelete = null;
      vrtxAdm.completeSimpleFormAsync({
        selector: "input#collectionListing\\.action\\.delete-resources",
        updateSelectors: ["#contents"],
        useClickVal: true,
        rowCheckedAnimateOut: true,
        minDelay: 800,
        fnBeforePost: function() {
          dialogDelete = new VrtxLoadingDialog({title: vrtxAdm.messages.deleting.inprogress});
          dialogDelete.open();
        },
        fnCompleteInstant: function() {
          dialogDelete.close();
        },
        fnComplete: function(resultElm) {
          vrtxAdm.displayErrorMsg(resultElm.find(".errormessage").html());
          vrtxAdm.updateCollectionListingInteraction();
        },
        fnError: function() {
          dialogDelete.close();
        }
      });

      break;
    case "vrtx-trash-can":
      var dialogDeletePermanent = null;
      var isTrashCanEmpty = false;
      vrtxAdm.completeSimpleFormAsync({
        selector: "input.deleteResourcePermanent",
        updateSelectors: ["#contents"],
        rowCheckedAnimateOut: true,
        minDelay: 800,
        fnBeforePost: function(form, link) {
          if (vrtxAdm.trashcanCheckedFiles >= vrtxAdm.cachedContent.find("tbody tr").length) {
            isTrashCanEmpty = true;
          }
          vrtxAdm.trashcanCheckedFiles = 0;
          startTime = new Date();
          dialogDeletePermanent = new VrtxLoadingDialog({title: vrtxAdm.messages.deleting.inprogress});
          dialogDeletePermanent.open();
        },
        fnCompleteInstant: function() {
          dialogDeletePermanent.close();
        },
        fnComplete: function(resultElm) {
          vrtxAdm.displayErrorMsg(resultElm.find(".errormessage").html());
          vrtxAdm.updateCollectionListingInteraction();
          dialogDeletePermanent.close();
          if(isTrashCanEmpty) { // Redirect on empty trash can
            window.location.href = "./?vrtx=admin";
          }
        },
        fnError: function() {
          dialogDeletePermanent.close();
        }
      });
      break;
    default:
      break;
  }
});

/*-------------------------------------------------------------------*\
    Create / Upload
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
  vrtxAdmin.inputUpdateEngine.grow(name, name.val(), 5, isIndexOrReplaceTitle ? 35 : 100, 530);

  if (vrtxAdmin.createResourceReplaceTitle) {
    $(".vrtx-admin-form").addClass("file-name-from-title");
  }
}

function createCheckUncheckIndexFile(nameField, indexCheckbox) {
  if (indexCheckbox.is(":checked")) {
    vrtxAdmin.createDocumentFileName = nameField.val();
    vrtxAdmin.inputUpdateEngine.grow(nameField, 'index', 5, 35, 530);
    nameField.val("index");

    nameField[0].disabled = true;
    $("#vrtx-textfield-file-type").addClass("disabled");
  } else {
    nameField[0].disabled = false;
    $("#vrtx-textfield-file-type").removeClass("disabled");

    nameField.val(vrtxAdmin.createDocumentFileName);
    vrtxAdmin.inputUpdateEngine.grow(nameField, vrtxAdmin.createDocumentFileName, 5, (vrtxAdmin.createResourceReplaceTitle ? 35 : 100), 530);
  }
}

function createTitleChange(titleField, nameField, indexCheckbox) {
  if (vrtxAdmin.createResourceReplaceTitle) {
    var nameFieldVal = vrtxAdmin.inputUpdateEngine.substitute(titleField.val(), true);
    if (!indexCheckbox || !indexCheckbox.length || !indexCheckbox.is(":checked")) {
      if (nameFieldVal.length > 50) {
        nameFieldVal = nameFieldVal.substring(0, 50);
      }
      nameField.val(nameFieldVal);
      vrtxAdmin.inputUpdateEngine.grow(nameField, nameFieldVal, 5, 35, 530);
    } else {
      vrtxAdmin.createDocumentFileName = nameFieldVal;
    }
  }
}

function createFileNameChange(nameField) {
  if (vrtxAdmin.createResourceReplaceTitle) {
    vrtxAdmin.createResourceReplaceTitle = false;
  }
  vrtxAdmin.inputUpdateEngine.update({
    input: nameField,
    toLowerCase: true,
    afterUpdate: function(after) {
      vrtxAdmin.inputUpdateEngine.grow(this.input, after, 5, 100, 530);
    }
  });
  $(".file-name-from-title").removeClass("file-name-from-title");
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

  var html = "<input class='vrtx-textfield vrtx-file-upload' tabindex='-1' id='fake-file' type='text' />" +
             "<a tabindex='-1' class='vrtx-button vrtx-file-upload'>" + vrtxAdmin.messages.upload.browse + "</a>";
  _$(html).insertAfter(inputFile);

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
      _$("<p id='vrtx-file-upload-info-text'>" + multipleFilesInfoText + "</p>").insertAfter("#fileUploadService-form .vrtx-textfield");
    }
  }
};

// Credits: https://css-tricks.com/drag-and-drop-file-uploading/
function initDragAndDropUpload(opts) {
  vrtxAdmin.getFormAsync({
    selector: "ul#tabMenuRight a#fileUploadService",
    selectorClass: "vrtx-admin-form",
    insertAfterOrReplaceClass: "#active-tab ul#tabMenuRight",
    useExistingHiddenMarkup: ".hidden-upload-wrapper",
    nodeType: "div",
    focusElement: "",
    funcComplete: vrtxAdmin.initFileUpload,
    simultanSliding: true
  });
  vrtxAdmin.completeFormAsync({
    selector: "form#fileUploadService-form input[type=submit]",
    updateSelectors: ["#contents"],
    errorContainer: "errorContainer",
    errorContainerInsertAfter: "h3",
    post: true,
    funcProceedCondition: ajaxUpload,
    funcComplete: function() {
      $(".expandedForm.vrtx-admin-form").remove();
      $(vrtxAdmin.storedExistingHiddenMarkup).insertAfter("#active-tab ul#tabMenuRight");
    }
  });

  var isUploadAction = $("#fileUploadService-form").length;

  if(vrtxAdmin.uploadIsAdvanced && !isUploadAction) {
    vrtxAdmin.serverFacade.getHtml($(opts.uploadSeviceSelector)[0].href, {
      success: function (results, status, resp) {
        // Add upload form
        var html = $($.parseHTML(results)).find(".expandedForm.vrtx-admin-form");
        vrtxAdmin.cachedActiveTab.append(html);
        vrtxAdmin.initFileUpload();

        vrtxAdmin.cachedActiveTab.find(".expandedForm.vrtx-admin-form:last-child")
                                 .removeClass("expandedForm vrtx-admin-form")
                                 .addClass("hidden-upload-wrapper");

        setupDragAndDropUpload(opts);
      }
    });
  }
  if(isUploadAction) {
    vrtxAdmin.initFileUpload();
    vrtxAdmin.storedExistingHiddenMarkup = $(".expandedForm.vrtx-admin-form").clone()
                                             .removeClass("expandedForm vrtx-admin-form")
                                             .addClass("hidden-upload-wrapper").remove();
    setupDragAndDropUpload(opts);
  }
}

function setupDragAndDropUpload(opts) {
  if(!vrtxAdmin.uploadIsAdvanced) return;

  var content = $(opts.contentSelector);
  if(!content.length) return;

  content.addClass("has-advanced-upload");

  var uploadOverlay = "upload-overlay";
  var uploadOverlayIcon = uploadOverlay + "-icon";
  var uploadOverlayIconText = uploadOverlayIcon + "-text";
  var uploadOverlayTop = uploadOverlay + "-top"; // Actual drop zone above graphical stuff
  var uploadHtml = "<div id='" + uploadOverlay + "'><span id='" + uploadOverlayIcon + "'>" +
                     "<span id='" + uploadOverlayIconText + "'>" + vrtxAdmin.messages.upload.drag + "</span>" +
                   "</span></div>" +
                   "<div id='" + uploadOverlayTop + "' />";
  content.append(uploadHtml);

  var uploadOverlayIconTextElm = $("#" + uploadOverlayIconText);
  var uploadOverlayTopElm = $("#" + uploadOverlayTop);

  var dragPreventDefault = function(e) {
    e.stopPropagation();
    if (e.preventDefault) {
      return e.preventDefault();
    } else {
      return e.returnValue = false;
    }
  };
  var dragStart = function(e) {
    if(!content.hasClass("is-dragover")) {
      content.addClass('is-dragover');
      uploadOverlayIconTextElm.text(vrtxAdmin.messages.upload.drop);
      var effect = "";
      try {
        effect = e.dataTransfer.effectAllowed;
      } catch (err) {}
      e.dataTransfer.dropEffect = 'copy';
    }
    dragPreventDefault(e);
  };
  var dragComplete = function(e) {
    if(content.hasClass("is-dragover")) {
      content.removeClass('is-dragover');
      uploadOverlayIconTextElm.text(vrtxAdmin.messages.upload.drag);
    }
    dragPreventDefault(e);
  };
  var drop = function(e) {
    vrtxAdmin.droppedFiles = e.dataTransfer.files;

    var form = $(opts.formSelector);
    ajaxUpload({
      selector: opts.formSelector,
      form: form,
      link: form.find("input.vrtx-focus-button"),
      updateSelectors: [opts.contentSelector],
      errorContainer: "errorContainer",
      errorContainerInsertAfter: "h3",
      post: true
    });
    dragComplete(e);
  };

  var addListenerMulti = function(elm, s, fn) {
    var evts = s.split(' ');
    for (var i = 0, len = evts.length; i < len; i++) {
      elm.addEventListener(evts[i], fn, false);
    }
  };

  addListenerMulti(document, 'dragenter', dragStart);
  addListenerMulti(uploadOverlayTopElm[0], 'dragleave dragend', dragComplete);
  addListenerMulti(uploadOverlayTopElm[0], 'drop', drop);
  addListenerMulti(document, 'drag drop dragstart dragleave dragend dragover', dragPreventDefault);
}

function ajaxUpload(opts) {
  var vrtxAdm = vrtxAdmin,
      _$ = vrtxAdm._$;

  opts.isDragAndDrop = typeof vrtxAdm.droppedFiles === "object" && vrtxAdm.droppedFiles != null;
  var uploadCheckServiceSelector = "#fileUploadCheckService-form";
  var existingFilenamesSelector = "#file-upload-existing-filenames";
  var existingFilesnamesFixedSelector = "#file-upload-existing-filenames-fixed";

  var futureFormAjax = _$.Deferred();
  if (typeof _$.fn.ajaxSubmit !== "function") {
    var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
    getScriptFn(vrtxAdm.rootUrl + "/jquery/plugins/jquery.form.js").done(function() {
      futureFormAjax.resolve();
    }).fail(function(xhr, textStatus, errMsg) {
      var dialogUploadingFailed = new VrtxMsgDialog({ title: xhr.status + " " + vrtxAdm.serverFacade.errorMessages.uploadingFilesFailedTitle,
                                                      msg: vrtxAdm.serverFacade.errorMessages.uploadingFilesFailed
                                                    });
      dialogUploadingFailed.open();

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
    var dirPaths = [];
    var numberOfFiles = 0;
    var size = 0;
    if (vrtxAdm.supportsFileList || opts.isDragAndDrop) {
      var files = !opts.isDragAndDrop ? fileField[0].files : vrtxAdm.droppedFiles;
      for (var i = 0, numberOfFiles = files.length; i < numberOfFiles; i++) {
        var file = files[i];
        if(file.size % 4096 == 0 && file.type === "") {
          dirPaths.push(file.name);
        } else {
          filePaths += file.name + ",";
        }
      }
      numberOfFiles -= dirPaths.length;
    } else {
      filePaths = fileField.val().substring(fileField.val().lastIndexOf("\\") + 1);
      numberOfFiles = 1;
    }

    if(numberOfFiles > 0) {
      var checkForm = _$(uploadCheckServiceSelector);
      var filenamesToCheckField = checkForm.find("input[name='filenamesToBeChecked']");
      if(!filenamesToCheckField.length) {
        checkForm.append("<input type='hidden' name='filenamesToBeChecked' value='" + filePaths + "' />");
      } else {
        filenamesToCheckField.val(filePaths);
      }
      vrtxAdm.removeErrorContainers(opts.form, opts.errorContainerInsertAfter, opts.errorContainer);

      checkForm.ajaxSubmit({
        success: function(results, status, xhr) {
          var result = _$.parseHTML(results);
          var existingFilenamesField = _$(result).find(existingFilenamesSelector);

          if (!existingFilenamesField.length && vrtxAdm.hasErrorContainers(result, opts.errorContainer)) {
            vrtxAdm.displayErrorContainers(result, opts.form, opts.errorContainerInsertAfter, opts.errorContainer);
            vrtxAdm.droppedFiles = null;
          } else {
            if(existingFilenamesField.length || dirPaths.length) {
              var existingFilenamesFixedField = _$(result).find(existingFilesnamesFixedSelector);
              var existingFilenames = existingFilenamesField.length ? existingFilenamesField.text().split("#") : [];
              var existingFilenamesFixed = existingFilenamesField.length ? existingFilenamesFixedField.text().split("#") : [];
              userProcessExistingFiles({
                dirs: dirPaths,
                filenames: existingFilenames,
                filenamesFixed: existingFilenamesFixed,
                numberOfFiles: numberOfFiles,

                completeFn: function() {
                  ajaxUploadPerform(opts/*, size*/);
                },
                cancelFn: function() {
                  vrtxAdm.uploadCopyMoveSkippedFiles = {};
                  vrtxAdm.droppedFiles = null;

                  var afterOut = function() {
                    if(opts.funcAfterComplete) {
                      opts.funcAfterComplete();
                    }
                  };

                  if(!opts.isDragAndDrop) {
                    var animation = new VrtxAnimation({
                      elem: opts.form.parent(),
                      animationSpeed: opts.transitionSpeed,
                      easeIn: opts.transitionEasingSlideDown,
                      easeOut: opts.transitionEasingSlideUp,
                      afterOut: afterOut
                    });
                    animation.bottomUp();
                  } else {
                    afterOut();
                  }
                },
                isAllSkippedEqualComplete: false
              });
            } else {
              ajaxUploadPerform(opts/*, size*/);
            }
          }
        }
      });
    } else {
      userProcessExistingFiles({
        dirs: dirPaths,
        filenames: [],
        filenamesFixed: [],
        numberOfFiles: 0,
        completeFn: function() {
          vrtxAdm.uploadCopyMoveSkippedFiles = {};
          vrtxAdm.droppedFiles = null;

          if(opts.funcAfterComplete) {
            opts.funcAfterComplete();
          }
        },
        cancelFn: function() {
          vrtxAdm.uploadCopyMoveSkippedFiles = {};
          vrtxAdm.droppedFiles = null;

          if(opts.funcAfterComplete) {
            opts.funcAfterComplete();
          }
        },
        isAllSkippedEqualComplete: true
      });
    }
  });
  return false;
}

function ajaxUploadPerform(opts/*, size*/) {
  var vrtxAdm = vrtxAdmin,
  _$ = vrtxAdm._$;

  var dialogLoadingContentSelector = "#dialog-loading-content";
  var dialogUploadingSelectorPrefix = "dialog-uploading";

  if(opts.isDragAndDrop) { // Extra flag so that uses FormData in jquery/plugins/jquery.form.js
    vrtxAdmin.droppedFilesHasBeenChecked = true;
  }

  var dialogUploading = new VrtxLoadingDialog({title: vrtxAdm.messages.upload.inprogress});
  dialogUploading.open();

  var dialogUploadingElm = _$(dialogLoadingContentSelector);

  // Set role and ARIA on dialog
  dialogUploadingElm.attr("role", "progressbar");
  dialogUploadingElm.attr("aria-valuemin", "0");
  dialogUploadingElm.attr("aria-valuemax", "100");
  dialogUploadingElm.attr("aria-valuenow", "0");
  dialogUploadingElm.append(
      "<div id='" + dialogUploadingSelectorPrefix + "-bar' />" +
      "<div id='" + dialogUploadingSelectorPrefix + "-percent'>&nbsp;</div>" +
      "<a id='" + dialogUploadingSelectorPrefix + "-focus' style='outline: none;' tabindex='-1' />" +
      "<a id='" + dialogUploadingSelectorPrefix + "-abort' href='javascript:void(0);'>Avbryt</a>"
  );
  var dialogUploadingBar = dialogUploadingElm.find("#" + dialogUploadingSelectorPrefix + "-bar");

  // Set focus on element before cancel link
  var focusElm = dialogUploadingElm.find("#" + dialogUploadingSelectorPrefix + "-focus");
  if(focusElm.length) focusElm.focus();
  focusElm.keydown(function(e) {
    if (isKey(e, [vrtxAdm.keys.TAB])) {
      $(this).next().addClass("tab-visible")[0].focus();
      return false;
    }
  });

  var uploadXhr = null;
  var dialogProcessing = null;
  var processingAfterUpload = false;

  // Set form to overwrite-mode
  opts.form.append("<input type='hidden' name='overwrite' value='overwrite' />");

  opts.form.ajaxSubmit({
    timeout: 0, // No timeout for upload, rely on the server timeout
    uploadProgress: function(event, position, total, percent) {
      _$("#" + dialogUploadingSelectorPrefix + "-percent").text(percent + "%");
      dialogUploadingBar.css("width", percent + "%");
      dialogUploadingElm.attr("aria-valuenow", percent);

      if(percent >= 100 && !processingAfterUpload) {
        processingAfterUpload = true;
        var waitAndProcess = setTimeout(function() {
          if(processingAfterUpload) {
            dialogUploading.close();
            dialogProcessing = new VrtxLoadingDialog({title: vrtxAdm.messages.upload.processes});
            dialogProcessing.open();
          }
        }, vrtxAdm.uploadCompleteTimeoutBeforeProcessingDialog);
      }
    },
    beforeSend: function(xhr) {
      uploadXhr = xhr;
    },
    success: function(results, status, xhr) {
      dialogUploadingBar.css("width", "100%");
      dialogUploadingElm.attr("aria-valuenow", 100);

      var result = _$.parseHTML(results);

      var afterOut = function() {
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
      };

      if (vrtxAdm.hasErrorContainers(result, opts.errorContainer)) {
        vrtxAdm.displayErrorContainers(result, opts.form, opts.errorContainerInsertAfter, opts.errorContainer);
      } else if(!opts.isDragAndDrop) {
        var animation = new VrtxAnimation({
          elem: opts.form.parent(),
          animationSpeed: opts.transitionSpeed,
          easeIn: opts.transitionEasingSlideDown,
          easeOut: opts.transitionEasingSlideUp,
          afterOut: afterOut
        });
        animation.bottomUp();
      } else {
        afterOut();
      }
    },
    error: function (xhr, textStatus, errMsg) {
      if(uploadXhr === null) {
        var dialogUploadingFailed = new VrtxMsgDialog({
          title: xhr.status + " " + vrtxAdm.serverFacade.errorMessages.uploadingFilesFailedTitle,
          msg: vrtxAdm.serverFacade.errorMessages.uploadingFilesFailed
        });
        dialogUploadingFailed.open();
      }

   	  if(opts.funcAfterComplete) {
        opts.funcAfterComplete();
      }
    },
    complete: function (xhr, textStatus) {
      vrtxAdm.uploadCopyMoveSkippedFiles = {};
      vrtxAdm.droppedFiles = null;
      vrtxAdm.droppedFilesHasBeenChecked = null;

      processingAfterUpload = false;
      if(dialogProcessing !== null) {
        dialogProcessing.close();
      } else {
        dialogUploading.close();
      }
    }
  });

  var ajaxUploadAbort = function(e) {
    if(uploadXhr !== null) {
      uploadXhr.abort();
    }
    dialogUploading.close();
    $(this).prev().removeClass("tab-visible");

  	if(opts.funcAfterComplete) {
      opts.funcAfterComplete();
    }

    e.stopPropagation();
    e.preventDefault();
  };
  vrtxAdm.cachedDoc.off("click", "#" + dialogUploadingSelectorPrefix + "-abort", ajaxUploadAbort)
                   .on("click", "#" + dialogUploadingSelectorPrefix + "-abort", ajaxUploadAbort);
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
          title: vrtxAdm.messages.upload.existing.title,
          msg: filenameFixed,
          onOk: userProcessNextFilename, // Keep/overwrite file
          btnTextOk: vrtxAdm.messages.upload.existing.overwrite
        };
      } else {
        var skipOverwriteDialogOpts = {
          title: vrtxAdm.messages.upload.existing.title,
          msg: filenameFixed,
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

      var uploadOrCancel = function() {
        if(opts.numberOfFiles > numberOfSkippedFiles || opts.isAllSkippedEqualComplete) {
          opts.completeFn();
        } else {
          opts.cancelFn();
        }
      };

      var numberOfDirs = opts.dirs && opts.dirs.length;
      if(numberOfDirs) {
        for(var i = 0; i < numberOfDirs; i++) {
          vrtxAdm.uploadCopyMoveSkippedFiles[opts.dirs[i]] = "skip";
        }
        var userInfoDirsDialog = new VrtxMsgDialog({
          title: vrtxAdm.messages.upload.folder,
          msg: opts.dirs.join(", "),
          onOk: function () {
            opts.dirs = [];
            uploadOrCancel();
          }
        });
        userInfoDirsDialog.open();
      } else {
        uploadOrCancel();
      }
    }
  };
  userProcessNextFilename();
}

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
    var selectorDialogTemplate = "#vrtx-dialog-template-copy-move-content";
    var selectorTitle = ".vrtx-confirm-copy-move-confirmation";
    var selectorMsg = ".vrtx-confirm-copy-move-explanation";

    var dialogTemplate = $(selectorDialogTemplate);

    var openCopyMoveConfirmDialog = function() {
      var d = new VrtxConfirmDialog({
        msg: dialogTemplate.find(selectorMsg).text(),
        title: dialogTemplate.find(selectorTitle).text(),
        onOk: function () {
          dialogTemplate.find(".vrtx-focus-button").trigger("click");
        }
      });
      d.open();
    };

    if (!dialogTemplate.length) {
      vrtxAdm.serverFacade.getHtml(this.href, {
        success: function (results, status, resp) {
          var html = "<div id='" + selectorDialogTemplate.substring(1) + "'>" +
                       _$($.parseHTML(results)).find("#vrtx-dialog-template-content").html() +
                     "</div>";
          vrtxAdm.cachedBody.append(html);

          dialogTemplate = $(selectorDialogTemplate);
          dialogTemplate.hide();
          openCopyMoveConfirmDialog();
        }
      });
    } else {
      openCopyMoveConfirmDialog();
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

  if($("#vrtx-manage-collectionlisting tr#collectionlisting-empty").length) {
    $("body").addClass("is-empty-collection");
  } else {
    $("body").removeClass("is-empty-collection");
  }
  setupDragAndDropUpload({
    uploadSeviceSelector: "#fileUploadService",
    contentSelector: "#contents",
    formSelector: "form#fileUploadService-form"
  });
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

  var html = "<a id='" + options.service + "' href='javascript:void(0);'>" + btn.attr('title') + "</a>";
  li.html(html);

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

  var html = '<a id="deleteResourceService" href="javascript:void(0);">' + btn.attr('title') + '</a>';
  li.html(html);

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
  var html = '<li><a id="unpublishTheResourcesService" href="javascript:void(0);">' + btn.attr('title') + '</a></li>';
  menu.find("#collection-more-menu ul").append(html);

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

  var html = '<ul class="list-menu" id="tabMenuRight">' +
               '<li class="recoverResourceService">' +
                 '<a id="recoverResourceService" href="javascript:void(0);">' + btn.attr('value') + '</a>' +
               '</li>' +
             '</ul>';
  vrtxAdm.cachedActiveTab.prepend(html);

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

  var html = '<li class="deleteResourcePermanentService">' +
               '<a id="deleteResourcePermanentService" href="javascript:void(0);">' + btn.attr('value') + '</a>' +
             '</li>';
  vrtxAdm.cachedActiveTab.find("#tabMenuRight").append(html);

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
 * @return {string} The built HTML
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

/**
 * Check if browser supports 'readOnly' attribute
 * Credits: http://miketaylr.com/code/input-type-attr.html (MIT license)
 *
 * @this {VrtxAdmin}
 */
VrtxAdmin.prototype.supportsReadOnly = function supportsReadOnly(inputfield) {
  return ( !! (inputfield.readOnly === false) && !! (inputfield.readOnly !== "undefined"));
};

/**
 * Check if browser supports 'multiple' attribute
 * Credits: http://miketaylr.com/code/input-type-attr.html (MIT license)
 *
* @this {VrtxAdmin}
 */
VrtxAdmin.prototype.supportsMultipleAttribute = function supportsMultipleAttribute(inputfield) {
  return ( !! (inputfield.multiple === false) && !! (inputfield.multiple !== "undefined")) && !vrtxAdmin.isIOS;
};
