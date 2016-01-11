/*
 * Permissions
 * 
 * Possible to set permissions for multiple users and groups on different levels:
 * read, write, admin/all etc.
 * 
 */
 
$.when(vrtxAdmin.domainsIsReady).done(function() {
  var vrtxAdm = vrtxAdmin, _$ = vrtxAdm._$;
  
  switch (vrtxAdm.bodyId) {
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
    default:
      break;
  }
});

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
