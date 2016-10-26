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
          funcComplete: setupPermissions,
          simultanSliding: false
        });
        vrtxAdm.completeFormAsync({
          selector: "div.permissions-" + privilegiesPermissions[i] + "-wrapper .submitButtons input",
          isReplacing: true,
          updateSelectors: [".permissions-" + privilegiesPermissions[i] + "-wrapper",
                            "#resourceMenuRight"],
          errorContainer: "errorContainer",
          errorContainerInsertAfter: ".groups-wrapper",
          funcProceedCondition: isUserStillAdmin,
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
          funcComplete: setupPermissions,
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
        fnComplete: setupPermissionsAutocomplete
      });
      vrtxAdm.completeSimpleFormAsync({
        selector: "span.addGroup input[type='submit']",
        updateSelectors: [".principalList"],
        errorContainer: "errorContainer",
        errorContainerInsertAfter: ".groups-wrapper",
        fnComplete: function() {
          $("input#groupNames").val("");
          setupPermissionsAutocomplete();
        }
      });
      vrtxAdm.completeSimpleFormAsync({
        selector: "span.addUser input[type='submit']",
        updateSelectors: [".principalList"],
        errorContainer: "errorContainer",
        errorContainerInsertAfter: ".users-wrapper",
        fnComplete: function() {
          $("input#userNames").val("");
          setupPermissionsAutocomplete();
        }
      });

      if(typeof confirmSetInheritedPermissionsMsg === "string") {
        var toggledInheritancePermission = false;
        vrtxAdm.cachedDoc.on("click", "#permissions\\.toggleInheritance\\.submit", function (e) {
          if (!toggledInheritancePermission) {
            var link = $(this);
            var d = new VrtxConfirmDialog({
              msg: confirmSetInheritedPermissionsMsg,
              title: confirmSetInheritedPermissionsTitle,
              onOk: function () {
                toggledInheritancePermission = true;
                link.trigger("click");
              }
            });
            d.open();
            e.stopPropagation();
            e.preventDefault();
          } else {
            e.stopPropagation();
          }
        });
      }

      break;
    default:
      break;
  }
});

function setupPermissions(selectorClass) {
  var permissonElm = $("." + selectorClass);
  if (!permissonElm.find(".aclEdit").length) return;

  toggleConfigCustomPermissions(permissonElm);

  listenerRerouteInputEnterToButton(permissonElm.find(".addUser input[type=text]"), permissonElm.find("input.addUserButton"));
  listenerRerouteInputEnterToButton(permissonElm.find(".addGroup input[type=text]"), permissonElm.find("input.addGroupButton"));

  setupPermissionsAutocomplete();
}

function toggleConfigCustomPermissions(permissonElm) {
  var customSelector = "ul.shortcuts label[for=custom]";
  var notCustomSelector = "ul.shortcuts label:not([for=custom])";
  var principalListSelector = ".principalList";

  var customToggle = permissonElm.find(customSelector + " input");
  if (customToggle.length && !customToggle.is(":checked")) {
    permissonElm.find(principalListSelector).addClass("hidden");
  }

  var anim = new VrtxAnimation({
    afterIn: function(animation) {
      animation.__opts.elem.removeClass("hidden");
    },
    afterOut: function(animation) {
      animation.__opts.elem.addClass("hidden");
    }
  });
  permissonElm.on("click", customSelector, function (e) {
    var elm = $(this).closest("form").find(principalListSelector + ".hidden");
    anim.updateElem(elm);
    anim.topDown();
    e.stopPropagation();
  });
  permissonElm.on("click", notCustomSelector, function (e) {
    var elm = $(this).closest("form").find(principalListSelector + ":not(.hidden)");
    anim.updateElem(elm);
    anim.bottomUp();
    e.stopPropagation();
  });
}

function setupPermissionsAutocomplete() {
  permissionsAutocomplete('userNames', 'userNames', vrtxAdmin.permissionsAutocompleteParams, false);
  splitAutocompleteSuggestion('userNames');
  permissionsAutocomplete('groupNames', 'groupNames', vrtxAdmin.permissionsAutocompleteParams, false);
}

function listenerRerouteInputEnterToButton(input, btn) {
  vrtxAdmin.cachedAppContent.on("keypress", input, function (e) {
    if (isKey(e, [vrtxAdmin.keys.ENTER])) {
      var inputElm = $(this);
      if (inputElm.hasClass("blockSubmit")) { // Avoid submitting form in some browser when adding permissions. See: jquery/plugins/jquery.autocomplete.js
        inputElm.removeClass("blockSubmit");
      } else {
        btn.click(); // click the associated button
      }
      inputElm.unautocomplete();
      e.preventDefault();
    }
  });
}

function isUserStillAdmin(opts) {
  vrtxAdmin.reloadFromServer = false;
  var stillAdmin = opts.form.find(".still-admin").text();
  if (stillAdmin === "false") {
    vrtxAdmin.reloadFromServer = true;
    var d = new VrtxConfirmDialog({
      msg: removeAdminPermissionsMsg,
      title: removeAdminPermissionsTitle,
      onOk: vrtxAdmin.completeFormAsyncPost,
      onOkOpts: opts,
      onCancel: function () {
        vrtxAdmin.reloadFromServer = false;
      }
    });
    d.open();
  } else {
    vrtxAdmin.completeFormAsyncPost(opts);
  }
}
