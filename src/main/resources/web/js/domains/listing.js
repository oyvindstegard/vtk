/*
 * Collection-listing and trash-can
 */

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

  _$("<input class='vrtx-textfield vrtx-file-upload' tabindex='-1' id='fake-file' type='text' /><a tabindex='-1' class='vrtx-button vrtx-file-upload'>Browse...</a>")
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
    });
  } else {
    futureFormAjax.resolve();
  }
  _$.when(futureFormAjax).done(function() {
    var fileField = _$("#file");
    var filePaths = "";
    var numberOfFiles = 0;
    // var size = 0;
    if (vrtxAdm.supportsFileList) {
      var files = fileField[0].files;
      for (var i = 0, numberOfFiles = files.length; i < numberOfFiles; i++) {
        filePaths += files[i].name + ",";
        // size += files[i].size;
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
                ajaxUploadPerform(opts/*, size*/);
              },
              cancelFn: function() {
                vrtxAdm.uploadCopyMoveSkippedFiles = {};;
                var animation = new VrtxAnimation({
                  elem: opts.form.parent(),
                  animationSpeed: opts.transitionSpeed,
                  easeIn: opts.transitionEasingSlideDown,
                  easeOut: opts.transitionEasingSlideUp
                });
                animation.bottomUp();
              },
              isAllSkippedEqualComplete: false
            });
          } else {
            ajaxUploadPerform(opts/*, size*/);
          }
        }
      }
    });
  });
  return false;
}

function ajaxUploadPerform(opts/*, size*/) {
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
            for (var i = opts.updateSelectors.length; i--;) {
              var outer = vrtxAdm.outerHTML(result, opts.updateSelectors[i]);
              vrtxAdm.cachedBody.find(opts.updateSelectors[i]).replaceWith(outer);
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
    }
  });
    
  var ajaxUploadAbort = function(e) {
    if(uploadXhr !== null) {
      uploadXhr.abort();
    }
    uploadingD.close();
    $(this).prev().removeClass("tab-visible");
    e.stopPropagation();
    e.preventDefault();
  };
    
  vrtxAdm.cachedDoc.off("click", "#dialog-uploading-abort", ajaxUploadAbort)
                   .on("click", "#dialog-uploading-abort", ajaxUploadAbort);
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