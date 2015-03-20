/*
 * Editors Save - have functionality for saving in a copy
 */
 
$.when(vrtxAdmin.domainsIsReady).done(function() {
  var vrtxAdm = vrtxAdmin, _$ = vrtxAdm._$;
  
  switch (vrtxAdm.bodyId) {
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
    default:
      break;
  }
});

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
  $.ajax({
    type: "GET",
    url: window.location.href,
    cache: true,
    dataType: "html",
    success: function (results, status, resp) {
    
      // Repopulate all tokens
      var current = $("input[name='csrf-prevention-token']");
      var updated = $($.parseHTML(results)).find("input[name='csrf-prevention-token']");
      current.forEach(function(elm, i) {
        elm.value = updated[i].value;
      });

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