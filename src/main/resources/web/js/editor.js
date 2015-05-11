/*
 *  Vortex Editor
 *
 *  ToC: 
 *
 *  1.  Config
 *  2.  DOM is ready
 *  3.  DOM is fully loaded
 *  4.  RichTextEditor (CKEditor)
 *  5.  Validation and change detection
 *  6.  Image preview
 *  7.  Enhancements
 *  8.  Multiple fields and boxes
 *  9.  Accordions
 *  10. Send to approval
 *  11. Utils
 */
 
/*-------------------------------------------------------------------*\
    1. Config
\*-------------------------------------------------------------------*/

/**
 * Creates an instance of VrtxEditor
 * @constructor
 */
function VrtxEditor() {
  /** The editor form */
  this.editorForm = null;

  /** Text input fields at init */
  this.editorInitInputFields = [];
  /** Select fields at init */
  this.editorInitSelects = [];
  /** Checkboxes at init */
  this.editorInitCheckboxes = [];
  /** Radios at init */
  this.editorInitRadios = [];

  /** Select fields show/hide mappings 
    * Mapping: "select-id": ["option-value-1", ..., "option-value-n"]
    */
  this.selectMappings = {
    "teachingsemester":  ["particular-semester", "every-other"],
    "examsemester":      ["particular-semester", "every-other"],
    "typeToDisplay":     ["so", "nm", "em"],
    "type-of-agreement": ["other"],
    "program-type":      ["phd"]
  };

  /** Initial state for the need to confirm navigation away from editor */
  this.needToConfirm = true;

  this.multipleFieldsBoxes = {}; /* Make sure every new field and box have unique id's (important for CK-fields) */
  
  /** These needs better names. */
  this.multipleFieldsBoxesTemplates = [];
  this.multipleFieldsBoxesDeferred = null;
  this.multipleFieldsBoxesAccordionSwitchThenScrollTo = null;

  this.multipleBoxesTemplatesContract = [];
  this.multipleBoxesTemplatesContractBuilt = null;

  /** Check if this script is in admin or not */
  this.isInAdmin = typeof vrtxAdmin !== "undefined";
}

var vrtxEditor = new VrtxEditor();
var UNSAVED_CHANGES_CONFIRMATION;

var editorCourseSchedule = "";

 // Accordion JSON and grouped
var accordionJson = null;
var accordionGrouped = null;


/*-------------------------------------------------------------------*\
    2. DOM is ready
\*-------------------------------------------------------------------*/

$(document).ready(function () {
  var vrtxEdit = vrtxEditor;
  vrtxEdit.editorForm = $("#editor");
  
  // Simple structured / embedded editor
  $("#app-content").on("click", "#vrtx-simple-editor .vrtx-back a, .vrtx-close-dialog-editor", function(e) {
    $("#vrtx-embedded-cancel-button, #cancel").click();
    e.preventDefault();
  });  

  if (!vrtxEdit.isInAdmin || !vrtxEdit.editorForm.length) {
    vrtxEdit.richtextEditorFacade.setupMultiple(false);
    return; /* Exit if not is in admin or have regular editor */
  }
  
  vrtxAdmin.cacheDOMNodesForReuse();

  // Skip UI helper as first element in editor
  vrtxEdit.editorForm.find(".ui-helper-hidden").filter(":not(:last)").filter(":first").next().addClass("first");

  vrtxEdit.initPreviewImage();
  
  var waitALittle = setTimeout(function() {
    autocompleteUsernames($(".vrtx-autocomplete-username"));
    autocompleteTags(".vrtx-autocomplete-tag");
    vrtxEdit.initSendToApproval();
    
    if(!vrtxEdit.isInAdmin || (vrtxEdit.isInAdmin && !isEmbedded)) {
      var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
      var futureStickyBar = (typeof VrtxStickyBar === "undefined") ? getScriptFn("/vrtx/__vrtx/static-resources/js/vrtx-sticky-bar.js") : $.Deferred().resolve();
      $.when(futureStickyBar).done(function() {     
        var editorStickyBar = new VrtxStickyBar({
          wrapperId: "#vrtx-editor-title-submit-buttons",
          stickyClass: "vrtx-sticky-editor-title-submit-buttons",
          contentsId: "#contents",
          outerContentsId: "#main"
        });
      });
    }
  }, 15);

  vrtxEdit.initEnhancements();
  vrtxEdit.richtextEditorFacade.setupMultiple(true);
});


/*-------------------------------------------------------------------*\
    3. DOM is fully loaded
\*-------------------------------------------------------------------*/

$(window).load(function () {
  if (!vrtxEditor.isInAdmin) return; /* Exit if not is in admin */

  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  // Store initial counts and values when all is initialized in editor
  var nullDeferred = _$.Deferred();
  nullDeferred.resolve();
  _$.when(((typeof MANUALLY_APPROVE_INITIALIZED === "object") ? MANUALLY_APPROVE_INITIALIZED : nullDeferred),
          ((typeof MULTIPLE_INPUT_FIELD_INITIALIZED === "object") ? MULTIPLE_INPUT_FIELD_INITIALIZED : nullDeferred),
          ((typeof JSON_ELEMENTS_INITIALIZED === "object") ? JSON_ELEMENTS_INITIALIZED : nullDeferred),
          ((typeof DATE_PICKER_INITIALIZED === "object") ? DATE_PICKER_INITIALIZED : nullDeferred),
          ((typeof IMAGE_EDITOR_INITIALIZED === "object") ? IMAGE_EDITOR_INITIALIZED : nullDeferred)).done(function () {
    vrtxAdm.log({ msg: "Editor initialized." });
    storeInitPropValues($("#app-content > form, #contents"));
  });

  // CTRL+S save inside editors
  if (typeof CKEDITOR !== "undefined" && vrtxEditor.editorForm && vrtxEditor.editorForm.length) { // Don't add event if not regular editor
    vrtxEditor.richtextEditorFacade.setupCTRLS();
  }
});


/*-------------------------------------------------------------------*\
    4. RichTextEditor (CKEditor)
\*-------------------------------------------------------------------*/

/**
 * RichTextEditor facade
 *
 * Uses CKEditor
 *
 * @namespace
 */
VrtxEditor.prototype.richtextEditorFacade = {
  toolbars: {},
  divContainerStylesSet: [{}],
  editorsForInit: [],
  initSyncMax: 15,
  initAsyncInterval: 15,
 /**
  * Setup multiple instances
  * @param {boolean} isInAdmin Is the editor in admin?
  * @this {richtextEditorFacade}
  */
  setupMultiple: function(isInAdmin) {
    if(isInAdmin) this.setupMaximizeMinimize();
  
    for (var i = 0, len = this.editorsForInit.length; i < len && i < this.initSyncMax; i++) { // Initiate <=CKEditorsInitSyncMax CKEditors sync
      this.setup(this.editorsForInit[i]);
    }
    if (len > this.initSyncMax) {
      var rteFacade = this;
      var richTextEditorsInitLoadTimer = setTimeout(function () { // Initiate >CKEditorsInitSyncMax CKEditors async
        rteFacade.setup(rteFacade.editorsForInit[i]);
        i++;
        if (i < len) {
          setTimeout(arguments.callee, rteFacade.initAsyncInterval);
        }
      }, rteFacade.initAsyncInterval);
    }
  },
  /**
   * Setup instance config
   *
   * @this {richtextEditorFacade}
   * @param {object} opts The options
   * @param {string} opts.name Name of textarea
   * @param {boolean} opts.isCompleteEditor Use complete toolbar
   * @param {boolean} opts.isWithoutSubSuper Don't display sub and sup buttons in toolbar
   * @param {string} opts.defaultLanguage Language in editor
   * @param {array} opts.cssFileList List of CSS-files to style content in editor
   * @param {string} opts.simple Make h1 format available (for old document types)
   */
  setup: function(opts) {
    var vrtxEdit = vrtxEditor,
        baseUrl = vrtxAdmin.multipleFormGroupingPaths.baseBrowserURL,
        baseFolder = vrtxAdmin.multipleFormGroupingPaths.baseFolderURL,
        browsePath = vrtxAdmin.multipleFormGroupingPaths.basePath;

    // File browser
    var linkBrowseUrl = baseUrl + '/plugins/filemanager/browser/default/browser.html?BaseFolder=' + baseFolder + '&Connector=' + browsePath;
    var imageBrowseUrl = baseUrl + '/plugins/filemanager/browser/default/browser.html?BaseFolder=' + baseFolder + '&Type=Image&Connector=' + browsePath;
    var flashBrowseUrl = baseUrl + '/plugins/filemanager/browser/default/browser.html?BaseFolder=' + baseFolder + '&Type=Flash&Connector=' + browsePath;

    // Classify
    var classification = vrtxEdit.classifyEditorInstance(opts);

    // Initialize
    this.init({
      name: opts.name,
      isReadOnly: opts.isReadOnly || false,
      linkBrowseUrl: linkBrowseUrl,
      imageBrowseUrl: classification.isMain ? imageBrowseUrl : null,
      flashBrowseUrl: classification.isMain ? flashBrowseUrl : null,
      defaultLanguage: opts.defaultLanguage, 
      cssFileList: opts.cssFileList,
      height: vrtxEdit.setupEditorHeight(classification, opts),
      maxHeight: vrtxEdit.setupEditorMaxHeight(classification, opts),
      minHeight: opts.isCompleteEditor ? 50 : 40,
      toolbar: vrtxEdit.setupEditorToolbar(classification, opts),
      complete: classification.isMain,
      requiresStudyRefPlugin: classification.requiresStudyRefPlugin,
      resizable: vrtxEdit.setupEditorResizable(classification, opts),
      baseDocumentUrl: classification.isMessage ? null : vrtxAdmin.multipleFormGroupingPaths.baseDocURL,
      isSimple: classification.isSimple,
      isFrontpageBox: classification.isFrontpageBox
    });

  },
  /**
   * Initialize instance with config
   *
   * @this {richtextEditorFacade}
   * @param {object} opts The config
   * @param {string} opts.name Name of textarea
   * @param {string} opts.isReadOnly Make it read only
   * @param {string} opts.linkBrowseUrl Link browse integration URL
   * @param {string} opts.imageBrowseUrl Image browse integration URL
   * @param {string} opts.flashBrowseUrl Flash browse integration URL
   * @param {string} opts.defaultLanguage Language in editor 
   * @param {string} opts.cssFileList List of CSS-files to style content in editor
   * @param {number} opts.height Height of editor
   * @param {number} opts.maxHeight Max height of editor
   * @param {number} opts.minHeight Min height of editor
   * @param {object} opts.toolbar The toolbar config
   * @param {string} opts.complete Use complete toolbar
   * @param {boolean} opts.resizable Possible to resize editor
   * @param {string} opts.baseDocumentUrl URL to current document 
   * @param {string} opts.isSimple Make h1 format available (for old document types)
   * @param {string} opts.isFrontpageBox Make h2 format unavailable (for frontpage boxes)
   */
  init: function(opts) {
    var config = {};

    config.baseHref = opts.baseDocumentUrl;
    config.contentsCss = opts.cssFileList;
    config.entities = false;
    
    if(opts.isReadOnly) {
      config.readOnly = true;
    }
    
    if (opts.linkBrowseUrl) {
      config.filebrowserBrowseUrl = opts.linkBrowseUrl;
      config.filebrowserImageBrowseLinkUrl = opts.linkBrowseUrl;
    }

    if (opts.complete) {
      config.filebrowserImageBrowseUrl = opts.imageBrowseUrl;
      config.filebrowserFlashBrowseUrl = opts.flashBrowseUrl;
      if(opts.requiresStudyRefPlugin) {
    	// Temporarily remove before new plugins are tested
        config.extraPlugins = 'mediaembed,studyreferencecomponent,htmlbuttons,button-h2,button-h3,button-h4,button-h5,button-h6,button-normal,lineutils,widget,image2,mathjax';
	    // config.extraPlugins = 'mediaembed,studyreferencecomponent,htmlbuttons,button-h2,button-h3,button-h4,button-h5,button-h6,button-normal,lineutils,widget,mathjax';	    
      } else {
        config.extraPlugins = 'mediaembed,htmlbuttons,button-h2,button-h3,button-h4,button-h5,button-h6,button-normal,lineutils,widget,image2,mathjax';
        // config.extraPlugins = 'mediaembed,htmlbuttons,button-h2,button-h3,button-h4,button-h5,button-h6,button-normal,lineutils,widget,mathjax';  
      }
      config.image2_alignClasses = [ 'image-left', 'image-center', 'image-right' ];
      config.image2_captionedClass = 'image-captioned';
      
      config.stylesSet = this.divContainerStylesSet;
      if (opts.isSimple) { // XHTML
        config.format_tags = 'p;h1;h2;h3;h4;h5;h6;pre;div';
      } else {
        config.format_tags = 'p;h2;h3;h4;h5;h6;pre;div';
      }    
    } else {
      config.removePlugins = 'elementspath';
    }
    
    //  if (opts.isFrontpageBox) {
    //	config.format_tags = 'p;h3;h4;h5;h6;pre;div';
    //  }
    
    config.resize_enabled = opts.resizable;
    config.toolbarCanCollapse = false;
    config.defaultLanguage = 'no';
    if(opts.defaultLanguage) {
      config.language = opts.defaultLanguage;
    }
    config.toolbar = opts.toolbar;
    config.height = opts.height + 'px';
    config.autoGrow_maxHeight = opts.maxHeight + 'px';
    config.autoGrow_minHeight = opts.minHeight + 'px';

    config.forcePasteAsPlainText = false;
    config.disableObjectResizing = true;
    config.disableNativeSpellChecker = false;
    
    config.allowedContent = true;
    /* Enable ACF - with all elements 
    config.allowedContent = {
      $1: {
        // Use the ability to specify elements as an object.
        elements: CKEDITOR.dtd,
        attributes: true,
        styles: true,
        classes: true
      }
    };
    */
    
    // Enable ACF
    if (vrtxEditor.editorForm.hasClass("vrtx-course-schedule")) {
      config.allowedContent = null;
    }
    
    config.linkShowTargetTab = false;

    // Key strokes
    config.keystrokes = [
      [ CKEDITOR.CTRL + 50 /*2*/, 'button-h2' ],
      [ CKEDITOR.CTRL + 51 /*3*/, 'button-h3' ],
      [ CKEDITOR.CTRL + 52 /*4*/, 'button-h4' ],
      [ CKEDITOR.CTRL + 53 /*5*/, 'button-h5' ],
      [ CKEDITOR.CTRL + 54 /*6*/, 'button-h6' ],
      [ CKEDITOR.CTRL + 49 /*0*/, 'button-normal' ]
    ];

    // Tag formatting in source
    var rteFacade = this;
    config.on = {
      instanceReady: function (ev) {
        rteFacade.setupTagsFormatting(this, ['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'], false);
        rteFacade.setupTagsFormatting(this, ['ol', 'ul', 'li'], true);
        
        /* 
         * VTK-3873
         * 
         * Migrate old div-containers (image or image+caption) to new image plugin
         *
         */
        var data = $($.parseHTML("<div>" + rteFacade.getValue(this) + "</div>"));
        var containers = data.find(".vrtx-container, .vrtx-img-container");
        for(var i = 0, len = containers.length; i < len; i++) {
          var container = $(containers[i]);
          var containerChildren = container.children();
          if(containerChildren.length == 1 || containerChildren.length == 2) {
            var img = container.find("img").clone();
            if(img.length) {
              var out = "";
              
              // Widht/height conversion
              var width = img.css("width");
              var height = img.css("height");
              img.attr({ "width": parseInt(img.css("width"), 10),
                         "height": parseInt(img.css("height"), 10) });
              img.removeAttr("style");
              
              // Alignment
              var align = container.hasClass("vrtx-container-left") ? "image-left" : "";
                  align += container.hasClass("vrtx-container-right") ? "image-right" : "";
                  align += container.hasClass("vrtx-container-middle") ? "image-center" : "";
              
              // Has caption?
              var caption = container.find("p");
              if($.trim(caption.text()) !== "") {
                caption.find("img").remove();
                if(align !== "") {
                  out = "<figure class='image-captioned " + align + "'>";
                } else {
                  out = "<figure class='image-captioned'>";
                }
                out += img[0].outerHTML +
                    "<figcaption>" +
                      caption.html() +
                    "</figcaption>" +
                    "</figure>";
                    
              // Only image
              } else {
                if(align !== "") {
                  img.addClass(align);
                }
                out += img[0].outerHTML;
              }
       
              if(out !== "") {
                container.replaceWith(out);
              }
            }
          }
        }
        if(len) {
          rteFacade.setValue(this, data.html());
        }
        
      }
    };
  
    if (!this.isInstance(opts.name)) {
      CKEDITOR.replace(opts.name, config);
    }
  },
  /**
   * Setup instance tags formatting
   *
   * @this {richTextEditorFacade}
   * @param {object} instance CKEditor instance
   * @param {array} tags Tags
   * @param {bool} isIndented If they should be indented
   */
  setupTagsFormatting: function(instance, tags, isIndented) {
    for (key in tags) {
      instance.dataProcessor.writer.setRules(tags[key], {
        indent: isIndented,
        breakBeforeOpen: true,
        breakAfterOpen: false,
        breakBeforeClose: false,
        breakAfterClose: true
      });
    }
  },
  setupCTRLS: function() {
    CKEDITOR.on('instanceReady', function (event) {
      _$(".cke_contents iframe").contents().find("body").bind('keydown', 'ctrl+s', $.debounce(150, true, function (e) {
        ctrlSEventHandler(_$, e);
      }));
      // Fix bug (http://dev.ckeditor.com/ticket/9958) with IE triggering onbeforeunload on dialog click
      event.editor.on('dialogShow', function(dialogShowEvent) {
        if(CKEDITOR.env.ie) {
          $(dialogShowEvent.data._.element.$).find('a[href*="void(0)"]').removeAttr('href');
        }
      });
      if(event.editor.name == "caption" || event.editor.name == "resource.caption") {
        event.editor.on('change', function() {
          var ref = $("input.preview-image-inputfield");
          if(ref.length) {
            previewImage(ref[0].id, false);
          }
        });
      }
    });
  },
  setupMaximizeMinimize: function() {
    vrtxAdmin.cachedAppContent.on("click", ".cke_button__maximize.cke_button_on", this.maximize);
    vrtxAdmin.cachedAppContent.on("click", ".cke_button__maximize.cke_button_off", this.minimize);
  },
  maximize: function() {
    var vrtxAdm = vrtxAdmin,
        _$ = vrtxAdm._$;
  
    var stickyBar = _$("#vrtx-editor-title-submit-buttons");
    stickyBar.hide();
    
    vrtxAdm.cachedBody.addClass("forms-new");
    vrtxAdm.cachedBody.addClass("js");

    var ckInject = _$(this).closest(".cke_reset")
                           .find(".cke_toolbar_end:last");

    if (!ckInject.find("#editor-help-menu").length) {
      var shortcuts = stickyBar.find(".submit-extra-buttons");
      var save = shortcuts.find("#vrtx-save").html();
      var helpMenu = "<div id='editor-help-menu' class='js-on'>" + shortcuts.find("#editor-help-menu").html() + "</div>";
      ckInject.append("<div class='ck-injected-save-help'>" + save + helpMenu + "</div>");

      // Fix markup
      var saveInjected = ckInject.find(".ck-injected-save-help > a");
      if (!saveInjected.hasClass("vrtx-button")) {
        saveInjected.addClass("vrtx-button");
      } else {
        saveInjected.removeClass("vrtx-focus-button");
      }
    } else {
      ckInject.find(".ck-injected-save-help").show();
    }
  },
  minimize: function() {
    var _$ = vrtxAdmin._$;
    var stickyBar = _$("#vrtx-editor-title-submit-buttons");
    stickyBar.show();
    var ckInject = _$(this).closest(".cke_reset").find(".ck-injected-save-help").hide();
  },
  getInstanceValue: function(name) {
    var inst = this.getInstance(name);
    return inst !== null ? inst.getData() : null;
  },
  updateInstanceByInstance: function(instance) {
    instance.updateElement();
  },
  updateInstance: function(name) {
    var inst = this.getInstance(name);
    if(inst !== null) {
      inst.updateElement();
    }
  },
  updateInstances: function() {
    for (var instance in CKEDITOR.instances) {
      CKEDITOR.instances[instance].updateElement();
    }
  },
  getValue: function(instance) {
    return instance.getData();
  },
  setValue: function(instance, data) {
    instance.setData(data);
  },
  setInstanceValue: function(name, data) {
    var inst = this.getInstance(name);
    if (inst !== null && data !== null) {
      inst.setData(data);
    }
  },
  isInstance: function(name) {
    return this.getInstance(name) !== null;
  },
  isChanged: function(instance) {
    return instance.checkDirty(); 
  },
  resetChanged: function() {
    for (var instance in CKEDITOR.instances) {
      CKEDITOR.instances[instance].resetDirty();
    }
  },
  getInstance: function(name) {
    return CKEDITOR.instances[name] || null;
  },
  removeInstance: function(name) {
    if (this.isInstance(name)) {
      var ckInstance = this.getInstance(name);
      ckInstance.destroy();
      if (this.isInstance(name)) { /* Just in case not removed */
        this.deleteInstance(name);
      }
    }
  },
  deleteInstance: function(name) {
    delete CKEDITOR.instances[name];
  },
  swap: function(nameA, nameB) {
    var rteFacade = this;
    var waitAndSwap = setTimeout(function () {
      var ckInstA = rteFacade.getInstance(nameA);
      var ckInstB = rteFacade.getInstance(nameB);
      var ckValA = ckInstA.getData();
      var ckValB = ckInstB.getData();
      ckInstA.setData(ckValB, function () {
        ckInstB.setData(ckValA);
      });
    }, 10);
  }
};

/* Toolbars */

vrtxEditor.richtextEditorFacade.toolbars.inlineToolbar = [
  ['Source', 'PasteText', 'Link', 'Unlink', 'Bold', 'Italic', 'Strike', 'Subscript', 'Superscript', 'SpecialChar']
];

vrtxEditor.richtextEditorFacade.toolbars.withoutSubSuperToolbar = [
  ['Source', 'PasteText', 'Link', 'Unlink', 'Bold', 'Italic', 'Strike', 'SpecialChar']
];

vrtxEditor.richtextEditorFacade.toolbars.commentsToolbar = [
  ['Source', 'PasteText', 'Bold', 'Italic', 'Strike', 'NumberedList', 'BulletedList', 'Link', 'Unlink']
];

vrtxEditor.richtextEditorFacade.toolbars.completeToolbar = [
  ['PasteText', 'PasteFromWord', '-', 'Undo', 'Redo'], ['Replace'], ['Link', 'Unlink', 'Anchor'],
  ['Image', 'MediaEmbed', 'Table', 'CreateDiv', 'HorizontalRule', 'Mathjax', 'SpecialChar'],
  ['Maximize'], ['Source'], '/', ['Format'], 
  ['Bold', 'Italic', 'Strike', 'Subscript', 'Superscript', 'TextColor', '-', 'RemoveFormat'],
  ['NumberedList', 'BulletedList', '-', 'Outdent', 'Indent', '-', 'Blockquote']
];

vrtxEditor.richtextEditorFacade.toolbars.studyToolbar = [
  ['Source', 'PasteText', 'PasteFromWord', '-', 'Undo', 'Redo', '-', 'Replace',
   'RemoveFormat', '-', 'Link', 'Unlink', 'Studyreferencecomponent', 'Anchor',
   'Image', 'MediaEmbed', 'CreateDiv', 'Table', 'Studytable', 'HorizontalRule', 'SpecialChar'],
  ['Format', 'Bold', 'Italic', 'Subscript', 'Superscript', 'NumberedList', 'BulletedList', 'Outdent', 'Indent', 'Maximize']
];

vrtxEditor.richtextEditorFacade.toolbars.studyRefToolbar = [
  ['Source', 'PasteText', 'PasteFromWord', '-', 'Undo', 'Redo', '-', 'Replace',
   'RemoveFormat', '-', 'Link', 'Unlink', 'Studyreferencecomponent', 'Anchor',
   'Image', 'MediaEmbed', 'Table', 'CreateDiv', 'HorizontalRule', 'SpecialChar'],
  ['Format', 'Bold', 'Italic', 'Subscript', 'Superscript', 'NumberedList', 'BulletedList', 'Outdent', 'Indent', 'Maximize']
];

vrtxEditor.richtextEditorFacade.toolbars.messageToolbar = [
  ['Source', 'PasteText', 'Bold', 'Italic', 'Strike', 'RemoveFormat', '-', 'Undo', 'Redo', '-', 'Link',
   'Unlink', 'Subscript', 'Superscript', 'NumberedList', 'BulletedList', 'Outdent', 'Indent']
];

vrtxEditor.richtextEditorFacade.toolbars.resourcesTextToolbar = [
  ['Source', 'PasteText', 'Bold', 'Italic', 'Strike', '-', 'Undo', 'Redo', '-', 'Link',
   'Unlink', 'Subscript', 'Superscript', 'NumberedList', 'BulletedList', 'Outdent', 'Indent']
];

/* Div containers */

vrtxEditor.richtextEditorFacade.divContainerStylesSet = [
  { name: 'Facts left',                 element: 'div', attributes: { 'class': 'vrtx-facts-container vrtx-container-left'  } },
  { name: 'Facts right',                element: 'div', attributes: { 'class': 'vrtx-facts-container vrtx-container-right' } }/*,
  { name: 'Image left',                 element: 'div', attributes: { 'class': 'vrtx-img-container vrtx-container-left'    } },
  { name: 'Image center',               element: 'div', attributes: { 'class': 'vrtx-img-container vrtx-container-middle vrtx-img-container-middle-ie' } },
  { name: 'Image right',                element: 'div', attributes: { 'class': 'vrtx-img-container vrtx-container-right' } },
  { name: 'Img & capt left (800px)',    element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xxl vrtx-container-left' } },
  { name: 'Img & capt left (700px)',    element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xl vrtx-container-left' } },
  { name: 'Img & capt left (600px)',    element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-l vrtx-container-left' } },
  { name: 'Img & capt left (500px)',    element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-m vrtx-container-left' } },
  { name: 'Img & capt left (400px)',    element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-s vrtx-container-left' } },
  { name: 'Img & capt left (300px)',    element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xs vrtx-container-left' } },
  { name: 'Img & capt left (200px)',    element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xxs vrtx-container-left' } },
  { name: 'Img & capt center (full)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-full vrtx-container-middle' } },
  { name: 'Img & capt center (800px)',  element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xxl vrtx-container-middle' } },
  { name: 'Img & capt center (700px)',  element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xl vrtx-container-middle' } },
  { name: 'Img & capt center (600px)',  element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-l vrtx-container-middle' } },
  { name: 'Img & capt center (500px)',  element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-m vrtx-container-middle' } },
  { name: 'Img & capt center (400px)',  element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-s vrtx-container-middle' } },
  { name: 'Img & capt right (800px)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xxl vrtx-container-right' } },
  { name: 'Img & capt right (700px)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xl vrtx-container-right' } },
  { name: 'Img & capt right (600px)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-l vrtx-container-right' } },
  { name: 'Img & capt right (500px)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-m vrtx-container-right' } },
  { name: 'Img & capt right (400px)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-s vrtx-container-right' } },
  { name: 'Img & capt right (300px)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xs vrtx-container-right' } },
  { name: 'Img & capt right (200px)',   element: 'div', attributes: { 'class': 'vrtx-container vrtx-container-size-xxs vrtx-container-right' } } */
];

/* Functions for generating editor config based on classification
 *
 * TODO: any better way to write this short and concise
 */

VrtxEditor.prototype.setupEditorHeight = function setupEditorHeight(c, opts) {
  return opts.isCompleteEditor ? ((c.isContent || c.isCourseGroup) ? 400 : (c.isSupervisorBox ? 130 : (c.isCourseDescriptionB ? 200 : 220)))
                               : (c.isMessage ? 250
                                              : (c.isCaption ? 55 
                                                             : ((c.isStudyField || c.isScheduleComment || c.isAdditionalContent) ? 150 
                                                                                                                                 : (c.isIntro ? 100 
                                                                                                                                              : 90))));
};

VrtxEditor.prototype.setupEditorMaxHeight = function setupEditorMaxHeight(c, opts) {
  return (c.isContent || c.isCourseGroup) ? 800 : (c.isSupervisorBox ? 300 : 400);
};

VrtxEditor.prototype.setupEditorToolbar = function setupEditorToolbar(c, opts) {
  var tb = vrtxEditor.richtextEditorFacade.toolbars;
  return classification.isMain ? ((c.isCourseDescriptionB || c.isCourseGroup) ? tb.studyRefToolbar 
                                                                              : (c.isStudyContent ? tb.studyToolbar
                                                                                                  : tb.completeToolbar))
                               : (c.isMessage ? tb.messageToolbar
                                              : (c.isResourcesText ? tb.resourcesTextToolbar                     
                                                                   : (c.isStudyField ? tb.studyToolbar 
                                                                                     : ((c.isIntro || c.isCaption || c.isScheduleComment) ? tb.inlineToolbar
                                                                                                                                            : tb.withoutSubSuperToolbar))));
};

VrtxEditor.prototype.setupEditorResizable = function setupEditorResizable(c, opts) {
  return classification.isMain || !(c.isCourseDescriptionA || c.isIntro || c.isCaption || c.isMessage || c.isStudyField || c.isScheduleComment);
};

/**
 * Classify editor based on its name
 *
 * @this {VrtxEditor}
 * @param {object} opts Config
 * @return {object} The classification with booleans
 */
VrtxEditor.prototype.classifyEditorInstance = function classifyEditorInstance(opts) {
  var vrtxEdit = this,
      name = opts.name;
      classification = {};

  // Content
  classification.isOldContent = name === "resource.content";
  classification.isStudyContent = name === "content-study";
  classification.isContent = name === "content" ||
                             classification.isOldContent ||
                             classification.isStudyContent;
  classification.isSimple = classification.isOldContent && opts.simple;
  classification.isFrontpageBox = vrtxEdit.editorForm.hasClass("vrtx-frontpage");
                             
  // Additional-content                  
  classification.isAdditionalContent = vrtxEdit.contains(name, "additional-content") ||
                                       vrtxEdit.contains(name, "additionalContents");
                                       
  classification.isMain = opts.isCompleteEditor || classification.isAdditionalContent;                   
  
  // Introduction / caption / sp.box
  classification.isIntro = vrtxEdit.contains(name, "introduction") ||
                           vrtxEdit.contains(name, "resource.description") ||
                           vrtxEdit.contains(name, "resource.image-description") ||
                           vrtxEdit.contains(name, "resource.video-description") ||
                           vrtxEdit.contains(name, "resource.audio-description");
  classification.isCaption = vrtxEdit.contains(name, "caption");
  classification.isMessage = vrtxEdit.contains(name, "message");
  classification.isResourcesText = vrtxEdit.contains(name, "vrtxResourcesText");
  classification.isSupervisorBox = vrtxEdit.contains("supervisor-box");
  
  // Studies
  classification.isStudyField = vrtxEdit.contains(name, "frist-frekvens-fri") ||
                                vrtxEdit.contains(name, "metode-fri") ||
                                vrtxEdit.contains(name, "internasjonale-sokere-fri") ||
                                vrtxEdit.contains(name, "nordiske-sokere-fri") ||
                                vrtxEdit.contains(name, "opptakskrav-fri") ||
                                vrtxEdit.contains(name, "generelle-fri") ||
                                vrtxEdit.contains(name, "spesielle-fri") ||
                                vrtxEdit.contains(name, "politiattest-fri") ||
                                vrtxEdit.contains(name, "rangering-sokere-fri") ||
                                vrtxEdit.contains(name, "forstevitnemal-kvote-fri") ||
                                vrtxEdit.contains(name, "ordinar-kvote-alle-kvalifiserte-fri") ||
                                vrtxEdit.contains(name, "innpassing-tidl-utdanning-fri") ||
                                vrtxEdit.contains(name, "regelverk-fri") ||
                                vrtxEdit.contains(name, "description-en") ||
                                vrtxEdit.contains(name, "description-nn") ||
                                vrtxEdit.contains(name, "description-no");
  classification.isScheduleComment = vrtxEdit.contains(name, "comment") && vrtxEdit.editorForm.hasClass("vrtx-schedule");
  classification.isCourseDescriptionA = name === "teachingsemester-other" ||
                                        name === "examsemester-other" ||
                                        name === "teaching-language-text-field" ||
                                        name === "eksamensspraak-text-field" ||
                                        name === "sensur-text-field" ||
                                        name === "antall-forsok-trekk-text-field" ||
                                        name === "tilrettelagt-eksamen-text-field";
  classification.isCourseDescriptionB = name === "course-content" ||
                                        name === "learning-outcomes" ||
                                        name === "opptak-og-adgang-text-field" ||
                                        name === "ikke-privatist-text-field" ||
                                        name === "obligatoriske-forkunnskaper-text-field" ||
                                        name === "recommended-prerequisites-text-field" ||
                                        name === "overlapping-courses-text-field" ||
                                        name === "teaching-text-field" ||
                                        name === "adgang-text-field" ||
                                        name === "assessment-and-grading" ||
                                        name === "hjelpemidler-text-field" ||
                                        name === "klage-text-field" ||
                                        name === "ny-utsatt-eksamen-text-field" ||
                                        name === "evaluering-av-emnet-text-field" ||
                                        name === "other-text-field";
  classification.isCourseGroup = name === "course-group-about" ||
                                 name === "courses-in-group" ||
                                 name === "course-group-admission" ||
                                 name === "relevant-study-programmes" ||
                                 name === "course-group-other";       
                                 
  classification.requiresStudyRefPlugin = classification.isStudyContent || classification.isCourseDescriptionB || classification.isCourseGroup || classification.isStudyField;
         
  return classification;
};


/*-------------------------------------------------------------------*\
    5. Validation and change detection
\*-------------------------------------------------------------------*/

function storeInitPropValues(contents) {
  if (!contents.length || (vrtxEditor.editorForm && vrtxEditor.editorForm.hasClass("vrtx-course-schedule"))) return;

  var vrtxEdit = vrtxEditor;

  var inputFields = contents.find("input").not("[type=submit]").not("[type=button]")
                            .not("[type=checkbox]").not("[type=radio]");
  var selects = contents.find("select");
  var checkboxes = contents.find("input[type=checkbox]:checked");
  var radioButtons = contents.find("input[type=radio]:checked");
  
  for (var i = 0, len = inputFields.length; i < len; i++) {
    vrtxEdit.editorInitInputFields[i] = inputFields[i].value;
  }
  for (i = 0, len = selects.length; i < len; i++) {
    vrtxEdit.editorInitSelects[i] = selects[i].value;
  }
  for (i = 0, len = checkboxes.length; i < len; i++) {
    vrtxEdit.editorInitCheckboxes[i] = checkboxes[i].name;
  }
  for (i = 0, len = radioButtons.length; i < len; i++) {
    vrtxEdit.editorInitRadios[i] = radioButtons[i].name + " " + radioButtons[i].value;
  }
}

function unsavedChangesInEditor() {
  if (!vrtxEditor.needToConfirm) {
    vrtxAdmin.ignoreAjaxErrors = true;
    return false;
  }
  
  var vrtxEdit = vrtxEditor;
  
  if(typeof editorCourseSchedule === "object") {
    return editorCourseSchedule.checkUnsavedChanges();
  }
  
  var contents = $("#app-content > form, #contents");

  var currentStateOfInputFields = contents.find("input").not("[type=submit]").not("[type=button]")
                                                        .not("[type=checkbox]").not("[type=radio]"),
      textLen = currentStateOfInputFields.length,
      currentStateOfSelects = contents.find("select"),
      selectsLen = currentStateOfSelects.length,
      currentStateOfCheckboxes = contents.find("input[type=checkbox]:checked"),
      checkboxLen = currentStateOfCheckboxes.length,
      currentStateOfRadioButtons = contents.find("input[type=radio]:checked"),
      radioLen = currentStateOfRadioButtons.length;

  // Check if count has changed
  if (textLen !== vrtxEdit.editorInitInputFields.length || selectsLen !== vrtxEdit.editorInitSelects.length || checkboxLen !== vrtxEdit.editorInitCheckboxes.length || radioLen !== vrtxEdit.editorInitRadios.length) return true;
  
  // Check if values have changed
  for (var i = 0; i < textLen; i++) if (currentStateOfInputFields[i].value !== vrtxEdit.editorInitInputFields[i]) return true;
  for (i = 0; i < selectsLen; i++) if (currentStateOfSelects[i].value !== vrtxEdit.editorInitSelects[i]) return true;
  for (i = 0; i < checkboxLen; i++) if (currentStateOfCheckboxes[i].name !== vrtxEdit.editorInitCheckboxes[i]) return true;
  for (i = 0; i < radioLen; i++) if (currentStateOfRadioButtons[i].name + " " + currentStateOfRadioButtons[i].value !== vrtxEdit.editorInitRadios[i]) return true;

  var currentStateOfTextFields = contents.find("textarea"); // CK->checkDirty()
  if (typeof CKEDITOR !== "undefined") {
    var rteFacade = vrtxEdit.richtextEditorFacade;
    for (i = 0, len = currentStateOfTextFields.length; i < len; i++) {
      var ckInstance = rteFacade.getInstance(currentStateOfTextFields[i].name);
      if (ckInstance && rteFacade.isChanged(ckInstance) && rteFacade.getValue(ckInstance) !== "") {
        return true;
      }
    }
  }
  vrtxAdmin.ignoreAjaxErrors = true;
  return false;
}

function unsavedChangesInEditorMessage() {
  if (unsavedChangesInEditor()) {
    return UNSAVED_CHANGES_CONFIRMATION;
  }
}

/* Detect changes between JSON-objects (Only working for Schedule per. 15.08.2014) */
function editorDetectChange(sessionId, o1, o2, isCK) {
  if(typeof o1 === "object" && typeof o2 === "object") {
    if(o1.length) { // Array
      if(o1.length !== o2.length) return true;
      for(var i = 0, len = o1.length; i < len; i++) {
        if(editorDetectChange(sessionId, o1[i], o2[i])) return true;
      }
    } else {
      var propCount2 = 0;
      for(prop2 in o2) {
        propCount2++;
      }
      var propCount1 = 0;
      for(prop1 in o1) {
        if(editorDetectChange(sessionId, o1[prop1], o2[prop1], prop1 === "vrtxResourcesText")) return true;
        propCount1++;
      }
      if(propCount1 !== propCount2) return true;
    }
  } else if(typeof o1 === "string" && typeof o2 === "string") {
    if(typeof isCK === "boolean" && isCK) { // TODO: use description to check for CK (if textarea)
      var rteFacade = vrtxEditor.richtextEditorFacade;
      var ckInstance = rteFacade.getInstance("vrtxResourcesText-" + sessionId);
      if (ckInstance && rteFacade.isChanged(ckInstance) && rteFacade.getValue(ckInstance) !== "") {
        return true;
      }
    } else {
      if(o1 !== o2) return true;
    }
  } else if(typeof o1 === "number" && typeof o2 === "number") {
    if(o1 !== o2) return true;
  } else if(typeof o1 !== typeof o2) {
    return true;
  }
  return false;
}

/* Validate length for 2048 bytes fields */
function validTextLengthsInEditor(isOldEditor) {
  var MAX_LENGTH = 1500, // Back-end limits is 2048
  
    // NEW starts on wrapper and OLD starts on field (because of slightly different semantic/markup build-up)
    INPUT_NEW = ".vrtx-string:not(.vrtx-multiple), .vrtx-resource-ref, .vrtx-image-ref, .vrtx-media-ref",
    INPUT_OLD = "input[type=text]:not(.vrtx-multiple)", // RT# 1045040 (skip aggregate and manually approve hidden input-fields)
    RTE_NEW = ".vrtx-simple-html, .vrtx-simple-html-small", // aka. textareas
    RTE_OLD = "textarea:not(#resource\\.content)";

  var contents = vrtxAdmin.cachedContent;

  var validTextLengthsInEditorErrorFunc = validTextLengthsInEditorError; // Perf.

  // String textfields
  var currentInputFields = isOldEditor ? contents.find(INPUT_OLD) : contents.find(INPUT_NEW);
  for (var i = 0, textLen = currentInputFields.length; i < textLen; i++) {
    var strElm = $(currentInputFields[i]);
    var str = "";
    if (isOldEditor) {
      str = (typeof strElm.val() !== "undefined") ? str = strElm.val() : "";
    } else {
      var strInput = strElm.find("input");
      str = (strInput.length && typeof strInput.val() !== "undefined") ? str = strInput.val() : "";
    }
    if (str.length > MAX_LENGTH) {
      validTextLengthsInEditorErrorFunc(strElm, isOldEditor);
      return false;
    }
  }

  // Textareas that are not content-fields (RichText)
  if (typeof CKEDITOR !== "undefined") {
    var currentTextAreas = isOldEditor ? contents.find(RTE_OLD) : contents.find(RTE_NEW);
    var rteFacade = vrtxEditor.richtextEditorFacade;
    for (i = 0, len = currentTextAreas.length; i < len; i++) {
      var txtAreaElm = $(currentTextAreas[i]);
      var txtArea = isOldEditor ? txtAreaElm : txtAreaElm.find("textarea");
      if (txtArea.length && typeof txtArea[0].name !== "undefined") {
        var ckInstance = rteFacade.getInstance(txtArea[0].name);
        if (ckInstance && rteFacade.getValue(ckInstance).length > MAX_LENGTH) {
          validTextLengthsInEditorErrorFunc(txtAreaElm, isOldEditor);
          return false;
        }
      }
    }
  }
  return true;
}

function validTextLengthsInEditorError(elm, isOldEditor) {
  if (typeof tooLongFieldPre !== "undefined" && typeof tooLongFieldPost !== "undefined") {
    $("html").scrollTop(0);
    var lbl = "";
    if (isOldEditor) {
      var elmPropWrapper = elm.closest(".property-item");
      if (elmPropWrapper.length) {
        lbl = elmPropWrapper.find(".property-label:first");
      }
    } else {
      lbl = elm.find("label");
    }
    if(lbl.length) {
      lbl = lbl.text();
    } else {
      lbl = "<?>";
    }
    var d = new VrtxMsgDialog({ msg: tooLongFieldPre + lbl + tooLongFieldPost, title: "" });
    d.open();
  }
}


/*-------------------------------------------------------------------*\
    6. Image preview
\*-------------------------------------------------------------------*/

VrtxEditor.prototype.initPreviewImage = function initPreviewImage() {
  var _$ = vrtxAdmin._$;

  // Box pictures
  var altTexts = $(".boxPictureAlt, .featuredPictureAlt");
  initBoxPictures(altTexts);
  
  // Introduction pictures
  var introImageAndCaption = _$(".introImageAndCaption, #vrtx-resource\\.picture");
  var injectionPoint = introImageAndCaption.find(".picture-and-caption, .vrtx-image-ref");
  var caption = introImageAndCaption.find(".caption");
  var hidePicture = introImageAndCaption.find(".hidePicture");
  var pictureAlt = introImageAndCaption.next(".pictureAlt");
  if(caption.length)     injectionPoint.append(caption.remove());
  if(hidePicture.length) injectionPoint.append(hidePicture.remove());
  if(pictureAlt.length) {
    injectionPoint.append(pictureAlt.remove());
  } else {
    pictureAlt = introImageAndCaption.find(".pictureAlt");
    injectionPoint.append(pictureAlt.remove());
  }
  
  /* Hide image previews on init (unobtrusive) */
  var previewInputFields = _$("input.preview-image-inputfield"),
      hideImagePreviewCaptionFunc = hideImagePreviewCaption;
  for (i = previewInputFields.length; i--;) {
    var elm = $(previewInputFields[i]);
    if (previewInputFields[i].value === "" && !hasImageCaption(elm, true)) {
      hideImagePreviewCaptionFunc(elm, true);
    }
  }
  
  /* Inputfield events for image preview */
  eventListen(vrtxAdmin.cachedDoc, "blur", "input.preview-image-inputfield", function (ref) {
    previewImage(ref.id, true);
  });
  eventListen(vrtxAdmin.cachedDoc, "keyup", "input.preview-image-inputfield", function (ref) {
    previewImage(ref.id);
  }, null, 50, true);
};

function initPictureAddJsonField(elm) {
  initBoxPictures(elm.find(".boxPictureAlt, .featuredPictureAlt"));
  hideImagePreviewCaption(elm.find("input.preview-image-inputfield"), true);
}

function initBoxPictures(altTexts) {
  for (var i = altTexts.length; i--;) {
    var altText = $(altTexts[i]);
    var imageRef = altText.prev(".vrtx-image-ref");
    imageRef.addClass("vrtx-image-ref-alt-text");
    imageRef.find(".vrtx-image-ref-preview").append(altText.remove());
  }
}

function hideImagePreviewCaption(input, isInit) {
  if (!input.length) return;
  var previewImg = $("div#" + input[0].id.replace(/\./g, '\\.') + '\\.preview:visible');
  if (!previewImg.length) return;

  var fadeSpeed = isInit ? 0 : "fast";

  previewImg.fadeOut(fadeSpeed);

  var captionWrp = input.closest(".introImageAndCaption, #vrtx-resource\\.picture");
  if (captionWrp.length) {
    captionWrp.find(".caption").fadeOut(fadeSpeed);
    captionWrp.find(".hidePicture").fadeOut(fadeSpeed);
    captionWrp.find(".pictureAlt").fadeOut(fadeSpeed);
    captionWrp.animate({
      height: "59px"
    }, fadeSpeed);
  }
}

function showImagePreviewCaption(input) {
  var previewImg = $("div#" + input[0].id.replace(/\./g, '\\.') + '\\.preview');
  if (!previewImg.length) return;

  previewImg.fadeIn("fast");

  var captionWrp = input.closest(".introImageAndCaption, #vrtx-resource\\.picture");
  if (captionWrp.length) {
    captionWrp.find(".caption").fadeIn("fast");
    captionWrp.find(".hidePicture").fadeIn("fast");
    captionWrp.find(".pictureAlt").fadeIn("fast");
    captionWrp.animate({
      height: "225px"
    }, "fast");
  }
}

function previewImage(urlobj, isBlurEvent) {
  if (typeof urlobj === "undefined") return;

  urlobj = urlobj.replace(/\./g, '\\.');
  var previewNode = $("#" + urlobj + '\\.preview-inner');
  if (previewNode.length) {
    var elm = $("#" + urlobj);
    if (elm.length) {
      var url = elm.val();
      if (url !== "" || hasImageCaption(elm, false)) {
        var parentPreviewNode = previewNode.parent();
        previewNode.find("img").attr("src", url + "?vrtx=thumbnail");
        previewNode.find("img").attr("alt", "thumbnail");
        showImagePreviewCaption(elm);
        if(typeof isBlurEvent === "undefined") elm.focus();
      } else {
        hideImagePreviewCaption(elm, false);
      } 
    }
  }
}

function hasImageCaption(input, init) {
  var captionWrp = input.closest(".introImageAndCaption, #vrtx-resource\\.picture");
  if(captionWrp.length) {
    if(init) {
      var captionTextAreaValue = captionWrp.find(".caption textarea").val();
      return captionTextAreaValue != "";
    } else {
      var captionId = captionWrp.find(".caption textarea")[0].id;
      vrtxEditor.richtextEditorFacade.updateInstance(captionId)
      var captionCkValue = vrtxEditor.richtextEditorFacade.getInstanceValue(captionId);
      return captionCkValue != null && captionCkValue != "";
    }
  }
  return false;
}


/*-------------------------------------------------------------------*\
    7. Enhancements
\*-------------------------------------------------------------------*/

VrtxEditor.prototype.initEnhancements = function initEnhancements() {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$,
    vrtxEdit = this;
    
  /* Show / hide for fields */
  var initResetAggregationManuallyApproved = function(_$, checkboxId, name) {
    if (!_$(checkboxId + "\\.true").is(":checked")) {
      _$("#vrtx-resource\\." + name).slideUp(0, "linear");
    }
    vrtxAdm.cachedAppContent.on("click", checkboxId + "\\.true", function (e) {
      if (!_$(this).is(":checked")) {
        _$("." + name + " .vrtx-multipleinputfield").remove();
        _$("#resource\\." + name).val("");
        _$(".vrtx-" + name + "-limit-reached").remove();
        _$("#vrtx-" + name + "-add").show();
      }
      _$("#vrtx-resource\\." + name).slideToggle(vrtxAdm.transitionDropdownSpeed, "swing");
      e.stopPropagation();
    });
  };
  
  initResetAggregationManuallyApproved(_$, "#resource\\.display-aggregation", "aggregation");
  initResetAggregationManuallyApproved(_$, "#resource\\.display-manually-approved", "manually-approve-from");

  vrtxAdm.cachedAppContent.on("change", "#resource\\.courseContext\\.course-status", function (e) {
    var courseStatus = _$(this);
    var animation = new VrtxAnimation({
      animationSpeed: vrtxAdm.transitionDropdownSpeed,
      easeIn: "swing",
      easeOut: "swing",
      afterIn: function(animation) {
        animation.__opts.elem.removeClass("hidden");
      },
      afterOut: function(animation) {
        animation.__opts.elem.addClass("hidden");
      }
    });
    if (courseStatus.val() === "continued-as") {
      animation.updateElem(_$("#vrtx-resource\\.courseContext\\.course-continued-as.hidden"));
      animation.topDown();
    } else {
      animation.updateElem(_$("#vrtx-resource\\.courseContext\\.course-continued-as:not(.hidden)"));
      animation.bottomUp();
    }
    e.stopPropagation();
  });
  _$("#resource\\.courseContext\\.course-status").change();
  
  
  // Show/hide mappings for radios/booleans
  
  // Exchange sub-folder title
  setShowHideBooleanOldEditor("#resource\\.show-subfolder-menu\\.true, #resource\\.show-subfolder-menu\\.unspecified",
    "#vrtx-resource\\.show-subfolder-title",
    "#resource\\.show-subfolder-menu\\.unspecified:checked",
    "");
    
  // Recursive
  setShowHideBooleanOldEditor("#resource\\.recursive-listing\\.false, #resource\\.recursive-listing\\.unspecified",
    "#vrtx-resource\\.recursive-listing-subfolders",
    "#resource\\.recursive-listing\\.false:checked",
    "false");
    
  // Calendar title
  setShowHideBooleanOldEditor("#resource\\.display-type\\.unspecified, #resource\\.display-type\\.calendar",
    "#vrtx-resource\\.event-type-title",
    "#resource\\.display-type\\.calendar:checked",
    null);
  setShowHideBooleanOldEditor("#resource\\.display-type\\.unspecified, #resource\\.display-type\\.calendar",
    "#vrtx-resource\\.hide-additional-content",
    "#resource\\.display-type\\.calendar:checked",
    "calendar");


  // Show / hide mappings for selects
  vrtxEdit.setShowHideSelectNewEditor();
  
  
  // Documenttype domains
  if(vrtxEdit.editorForm.hasClass("vrtx-course-schedule")) {
    editorCourseSchedule = new courseSchedule();
  } else if (vrtxEdit.editorForm.hasClass("vrtx-hvordan-soke")) {
    vrtxEdit.accordionGroupedInit();
  } else if (vrtxEdit.editorForm.hasClass("vrtx-course-description")) {
    setShowHideBooleanNewEditor("course-fee", "div.course-fee-amount", false);
    vrtxEdit.accordionGroupedInit();
  } else if (vrtxEdit.editorForm.hasClass("vrtx-semester-page")) {
    setShowHideBooleanNewEditor("cloned-course", "div.cloned-course-code", false);
    vrtxEdit.accordionGroupedInit("[class*=link-box]");
  } else if (vrtxEdit.editorForm.hasClass("vrtx-student-exchange-agreement")) {
    vrtxEdit.accordionGroupedInit(".vrtx-sea-accordion");
  } else if (vrtxEdit.editorForm.hasClass("vrtx-samlet-program")) {
    var samletElm = vrtxEdit.editorForm.find(".samlet-element");
    vrtxEdit.replaceTag(samletElm, "h6", "strong");
    vrtxEdit.replaceTag(samletElm, "h5", "h6");
    vrtxEdit.replaceTag(samletElm, "h4", "h5");
    vrtxEdit.replaceTag(samletElm, "h3", "h4");
    vrtxEdit.replaceTag(samletElm, "h2", "h3");
    vrtxEdit.replaceTag(samletElm, "h1", "h2");
  } else if (vrtxEdit.editorForm.hasClass("vrtx-frontpage")) {
    vrtxEdit.accordionGroupedInit(".vrtx-sea-accordion", "fast");
  } else if (vrtxEdit.editorForm.hasClass("vrtx-contact-supervisor")) {
    vrtxAdm.cachedDoc.on("keyup", ".vrtx-string.id input[type='text']", $.debounce(50, true, function () {
      vrtxAdm.inputUpdateEngine.update({
        input: $(this),
        substitutions: {
          "#": "",
          " ": "-"
        },
        toLowerCase: false
      });
    }));
  }
};

/*
 * Boolean switch show/hide
 *
 */
function setShowHideBooleanNewEditor(name, properties, hideTrues) {
  vrtxEditor.initEventHandler('[name=' + name + ']', {
    wrapper: "#editor",
    callback: function (props, hideTrues, name, init) {
      if ($('#' + name + (hideTrues ? '-false' : '-true'))[0].checked) {
        toggleShowHideBoolean(props, true, init);
      } else if ($('#' + name + (hideTrues ? '-true' : '-false'))[0].checked) {
        toggleShowHideBoolean(props, false, init);
      }
    },
    callbackParams: [properties, hideTrues, name]
  });
}

function setShowHideBooleanOldEditor(radioIds, properties, conditionHide, conditionHideEqual) {
  vrtxEditor.initEventHandler(radioIds, {
    wrapper: "#editor",
    callback: function (props, conditionHide, conditionHideEqual, init) {
      toggleShowHideBoolean(props, $(conditionHide).val() != conditionHideEqual, init);
    },
    callbackParams: [properties, conditionHide, conditionHideEqual]
  });
}

function toggleShowHideBoolean(props, show, init) {
  var theProps = $(props);
  if (init || vrtxAdmin.isIE9) {
    if (!vrtxAdmin.isIE9) {
      theProps.addClass("animate-optimized");
    }
    theProps[(show && !init) ? "show" : "hide"]();
  } else {
    var animation = new VrtxAnimation({
      animationSpeed: vrtxAdmin.transitionPropSpeed,
      elem: theProps
    });
    animation[show ? "topDown" : "bottomUp"]();
  }
}

/**
 * Set select field show/hide
 *
 * @this {VrtxEditor}
 */
VrtxEditor.prototype.setShowHideSelectNewEditor = function setShowHideSelectNewEditor() {
  var vrtxEdit = this;

  for (var select in vrtxEdit.selectMappings) {
    vrtxEdit.initEventHandler("#" + select, {
      event: "change",
      callback: vrtxEdit.showHideSelect
    });
  }
};

/**
 * Select field show/hide
 *
 * @this {VrtxEditor}
 * @param {object} select The select field jQElement
 * @param {boolean} init
 */
VrtxEditor.prototype.showHideSelect = function showHideSelect(select, init) {
  var vrtxEdit = this;

  var id = select.attr("id");
  var mappings = vrtxEdit.selectMappings[id];
  if (mappings) {
    var selectClassName = "select-" + id;
    if (!vrtxEdit.editorForm.hasClass(selectClassName)) {
      vrtxEdit.editorForm.addClass(selectClassName);
    }
    var selected = select.val();
    for (var i = 0, len = mappings.length; i < len; i++) {
      var mappedClass = selectClassName + "-" + mappings[i];
      var editorHasMappedClass = vrtxEdit.editorForm.hasClass(mappedClass);
      if (selected === mappings[i]) {
        if (!editorHasMappedClass) {
          vrtxEdit.editorForm.addClass(mappedClass);
        }
      } else {
        if (editorHasMappedClass) {
          vrtxEdit.editorForm.removeClass(mappedClass);
        }
      }
    }
  }
  if (!init && accordionGrouped) accordionGrouped.closeActiveHidden();
};
   

/*-------------------------------------------------------------------*\
    8. Multiple fields and boxes
\*-------------------------------------------------------------------*/

/*
 * Multiple comma-seperated inputfields (supports JSON inputfields)
 *
 */

var userEnrichmentSeperators = {
  url: "%%URL%%",
  text: "%%TEXT%%"
};

function getMultipleFieldsBoxesTemplates() {
  if (!vrtxEditor.multipleFieldsBoxesDeferred) {
    vrtxEditor.multipleFieldsBoxesDeferred = $.Deferred();
    vrtxEditor.multipleFieldsBoxesTemplates = vrtxAdmin.templateEngineFacade.get("multiple-fields-boxes", 
      ["string", "html", "radio", "checkbox", "dropdown", "date", "browse",
       "browse-images", "add-remove-move", "button", "add-button",
       "multiple-inputfield", "accordion"],
    vrtxEditor.multipleFieldsBoxesDeferred);
  }
}

function initMultipleInputFields() {
  getMultipleFieldsBoxesTemplates();
  
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-multipleinputfield button.remove", function (ref) {
    removeFormField($(ref));
  }, "clickOrEnter");
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-multipleinputfield button.movedown", function (ref) {
    swapContentTmp($(ref), 1);
  }, "clickOrEnter");
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-multipleinputfield button.moveup", function (ref) {
    swapContentTmp($(ref), -1);
  }, "clickOrEnter");
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-multipleinputfield button.browse-resource-ref", function (ref) {
    var m = $(ref).closest(".vrtx-multipleinputfield");
    var elm = m.find('input.resource_ref');
    if(!elm.length) {
      elm = m.find('input');
    }
    browseServer(elm.attr('id'), vrtxAdmin.multipleFormGroupingPaths.baseBrowserURL, vrtxAdmin.multipleFormGroupingPaths.baseFolderURL, vrtxAdmin.multipleFormGroupingPaths.basePath, 'File');
  }, "clickOrEnter");
}

function enhanceMultipleInputFields(name, isMovable, isBrowsable, limit, json, isReadOnly) { // TODO: simplify
  var inputField = $("." + name + " input[type='text']");
  if (!inputField.length || vrtxAdmin.isIE7 || vrtxAdmin.isIETridentInComp) return;

  // Config
  var size = inputField.attr("size");
    
  var isDropdown = inputField.hasClass("vrtx-multiple-dropdown");
  isMovable = !isDropdown && isMovable;

  var inputFieldParent = inputField.parent();
  if (inputFieldParent.hasClass("vrtx-resource-ref-browse")) {
    isBrowsable = true;
    inputField.next().filter(".vrtx-button").hide();
  }

  inputFieldParent.addClass("vrtx-multipleinputfields").data("name", name); // Don't like to do this need get it easily
  $($.parseHTML(vrtxEditor.htmlFacade.getMultipleInputFieldsAddButton(name, size, isBrowsable, isMovable, isDropdown, JSON.stringify(json, null, 2)), document, true)).insertAfter(inputField);

  var inputFieldVal = inputField.hide().val();
  var formFields = json && json.length ? inputFieldVal.split("$$$")
                                       : inputFieldVal.split(",");

  vrtxEditor.multipleFieldsBoxes[name] = { counter: 1, limit: limit };

  var addFormFieldFunc = addFormField, html = "", isEnriched = false;
  for (var i = 0, len = formFields.length; i < len; i++) {
    var htmlEnriched = addFormFieldFunc(name, len, $.trim(formFields[i]), size, isBrowsable, isMovable, isDropdown, true, json, isReadOnly);
    html += htmlEnriched.html;
    isEnriched = htmlEnriched.isEnriched;
  }
  html = $.parseHTML(html, document, true);
  $(html).insertBefore("#vrtx-" + name + "-add");
  inputFieldParent.find(".vrtx-multipleinputfield:first").addClass("first");
  
  // Hide add button if limit is reached / gone over or isReadOnly
  var isLimitReached = len >= vrtxEditor.multipleFieldsBoxes[name].limit;
  if(isLimitReached || isReadOnly) {
    var moreBtn = $("#vrtx-" + name + "-add");
    if(isLimitReached) {
   	  $("<p class='vrtx-" + name + "-limit-reached'>" + vrtxAdmin.multipleFormGroupingMessages.limitReached + "</p>").insertBefore(moreBtn);
	}
    moreBtn.hide();
  }
  autocompleteUsernames(inputFieldParent.filter(".vrtx-autocomplete-username"), isEnriched);
}

function addFormField(name, len, value, size, isBrowsable, isMovable, isDropdown, init, json, isReadOnly) {
  var fields = $("." + name + " div.vrtx-multipleinputfield"),
    idstr = "vrtx-" + name + "-",
    i = vrtxEditor.multipleFieldsBoxes[name].counter,
    removeButton = "",
    moveUpButton = "",
    moveDownButton = "",
    browseButton = "";
    
  len = !init ? fields.length : len; /* If new field set len to fields length */

  removeButton = vrtxEditor.htmlFacade.getMultipleInputfieldsInteractionsButton("remove", " " + name, idstr, "", vrtxAdmin.multipleFormGroupingMessages.remove);
  if (isMovable) {
    if (i > 1 && len > 0) {
      moveUpButton = vrtxEditor.htmlFacade.getMultipleInputfieldsInteractionsButton("moveup", "", idstr, vrtxAdmin.multipleFormGroupingMessages.moveUp, "<span class='moveup-arrow'></span>");
    }
    if (i < len) {
      moveDownButton = vrtxEditor.htmlFacade.getMultipleInputfieldsInteractionsButton("movedown", "", idstr, vrtxAdmin.multipleFormGroupingMessages.moveDown, "<span class='movedown-arrow'></span>");
    }
  }
  if (isBrowsable) {
    browseButton = vrtxEditor.htmlFacade.getMultipleInputfieldsInteractionsButton("browse", "-resource-ref", idstr, "", vrtxAdmin.multipleFormGroupingMessages.browse);
  }
  
  // Add form field with possible JSON multiple and user enrichments
  var isEnriched = false;
  var hasEnrichedText = false;
  var hasEnrichedUrl = false;
  var jsonProcessed = null;
  if(json) {
    if(value && value.indexOf("###") !== -1) {
      value = value.split("###");
    }
    jsonProcessed = jQuery.extend(true, [], json);
    var enriched = addFormFieldUserEnrichment(value, jsonProcessed, isEnriched, hasEnrichedText, hasEnrichedUrl);
    isEnriched = enriched.isEnriched;
    hasEnrichedText = enriched.hasEnrichedText;
    hasEnrichedUrl = enriched.hasEnrichedUrl;
  }
  
  var html = vrtxEditor.htmlFacade.getMultipleInputfield(name, idstr, i, value, size, browseButton, removeButton, moveUpButton, moveDownButton, isDropdown, jsonProcessed, isReadOnly, hasEnrichedText, hasEnrichedUrl);

  vrtxEditor.multipleFieldsBoxes[name].counter++;

  if (!init) {
    if (len > 0 && isMovable) {
      var last = fields.filter(":last");
      if (!last.find("button.movedown").length) {
        moveDownButton = vrtxEditor.htmlFacade.getMultipleInputfieldsInteractionsButton("movedown", "", idstr, vrtxAdmin.multipleFormGroupingMessages.moveDown, "<span class='movedown-arrow'></span>");
        last.append(moveDownButton);
      }
    }
    
    var moreBtn = $("#vrtx-" + name + "-add");
    $($.parseHTML(html, document, true)).insertBefore(moreBtn);
    
    fields = $("." + name + " div.vrtx-multipleinputfield");
    
    if(len === 0) {
      fields.filter(":first").addClass("first");
    }
    
    // Setup autocomplete on username fields
    autocompleteUsername(".vrtx-autocomplete-username", idstr + i);
    autocompleteUsername(".vrtx-autocomplete-username", idstr + "id-" + i, isEnriched); // JSON name='id' fix
    
    var focusable = moreBtn.prev().find("input[type='text'], select");
    if(focusable.length) {
      focusable[0].focus();
    }

    // Hide add button if limit is reached
    var isLimitReached = (len === (vrtxEditor.multipleFieldsBoxes[name].limit - 1));
    if(isLimitReached || isReadOnly) {
      if(isLimitReached) {
	    $("<p class='vrtx-" + name + "-limit-reached'>" + vrtxAdmin.multipleFormGroupingMessages.limitReached + "</p>").insertBefore(moreBtn);
      }
	  moreBtn.hide();
    }
  } else {
    return { "html": html, "isEnriched": isEnriched };
  }
}

function removeFormField(input) {
  var parent = input.closest(".vrtx-multipleinputfields");
  var field = input.closest(".vrtx-multipleinputfield");
  var name = parent.data("name");
  field.remove();

  // Find and set focus on first field
  var fields = parent.find(".vrtx-multipleinputfield");
  var firstField = fields.filter(":first");
  if(firstField.length) {
    if(!firstField.hasClass("first")) {
      firstField.addClass("first");
    }
    var focusable = firstField.find("input[type='text'], select").filter(":first");
    if(focusable.length) {
      focusable[0].focus();
    }
  }
  
  // Show add button if is within limit again
  if(fields.length === (vrtxEditor.multipleFieldsBoxes[name].limit - 1)) {
    $(".vrtx-" + name + "-limit-reached").remove();
    $("#vrtx-" + name + "-add").show();
  }
  var moveUpFirst = fields.filter(":first").find("button.moveup");
  var moveDownLast = fields.filter(":last").find("button.movedown");
  if (moveUpFirst.length) moveUpFirst.remove();
  if (moveDownLast.length) moveDownLast.remove();
}

function swapContentTmp(moveBtn, move) {
  var curElm = moveBtn.closest(".vrtx-multipleinputfield");
  var movedElm = (move > 0) ? curElm.next() : curElm.prev();
  var curElmInputs = curElm.find("input");
  var movedElmInputs = movedElm.find("input");
  for(var i = curElmInputs.length;i--;) {
    var tmp = curElmInputs[i].value;
    curElmInputs[i].value = movedElmInputs[i].value;
    movedElmInputs[i].value = tmp;
  }
  swapUserEnrichment(curElm, movedElm, curElmInputs, movedElmInputs);
  
  movedElmInputs.filter(":first")[0].focus();
}

function addFormFieldUserEnrichment(value, json, isEnriched, hasEnrichedText, hasEnrichedUrl) {
  var enrichedUrl = "";
  var enrichedText = "";
  var sep = userEnrichmentSeperators;
  
  // Extract user enrichments if exists
  if(value && value.length) {
    var valueIsMultiple = typeof value === "object";
    var lastVal = valueIsMultiple ? value[value.length - 1] : value;
    if(lastVal.indexOf(sep.url) !== -1) {
      var enrichedUrl = lastVal.split(sep.url);
      if(enrichedUrl[1].indexOf(sep.text) !== -1) {
        enrichedText = enrichedUrl[1].split(sep.text)[0];
        enrichedUrl = enrichedUrl[0];
      } else {
        enrichedText = "";
      }
      value = [ value[0] ];
    } else if(lastVal.indexOf(sep.text) !== -1) {
      var enrichedText = lastVal.split(sep.text)[0];
      value = [ value[0] ];
    } else {
      if(!valueIsMultiple) {
        value = [ value ];
      }
    }
  } else {
    value = [ "" ];
  }
  
  // Prepare JSON multiple and user enrichments for template
  var i = 0;
  var enrichedTextProp = null;
  var val = "";
  for(var prop in json) {
    switch(json[prop].type) {
      case "enrichedText":
        isEnriched = true;
        json[prop].enrichedText = true; // Access for template
        enrichedTextProp = prop;
        if(enrichedText.length) {
          json[prop].enrichedTextVal = enrichedText;
          hasEnrichedText = true;
        }
        break;
      case "enrichedUrl":
        isEnriched = true;
        json[prop].enrichedUrl = true; // Access for template
        if(enrichedUrl.length) {
          json[prop].enrichedUrlVal = enrichedUrl;
          hasEnrichedUrl = true;
        }
        break;
      default:
        json[prop].val = value[i];
        if(json[prop].val != "") {
          val = json[prop].val;
        }
        break;
    }
    i++;
  }
  if(!hasEnrichedText && !hasEnrichedUrl && enrichedTextProp != null && val != "") {
    json[enrichedTextProp].enrichedTextVal = val;
    hasEnrichedText = true;
  }
  return { "isEnriched": isEnriched,
           "hasEnrichedText": hasEnrichedText,
           "hasEnrichedUrl": hasEnrichedUrl };
}

function swapUserEnrichment(curElm, movedElm, curElmInputs, movedElmInputs) {
  var curElmEnrichment = curElm.find(".vrtx-multiple-inputfield-enrichment");
  var movedElmEnrichment = movedElm.find(".vrtx-multiple-inputfield-enrichment");
  if(curElmEnrichment.length) {
    if(movedElmEnrichment.length) {
      var tmp = curElmEnrichment[0].outerHTML;
      curElmEnrichment.replaceWith(movedElmEnrichment[0].outerHTML);
      movedElmEnrichment.replaceWith(tmp);
    } else {
      $(curElmInputs[0]).removeClass("vrtx-multipleinputfield-field-enriched");
      $(movedElmInputs[0]).addClass("vrtx-multipleinputfield-field-enriched");
      $(curElmEnrichment.remove())
        .insertAfter(movedElm.find(".vrtx-multipleinputfield-json-wrapper").filter(":last"));
    }
  } else if(movedElmEnrichment.length) {
    $(movedElmInputs[0]).removeClass("vrtx-multipleinputfield-field-enriched");
    $(curElmInputs[0]).addClass("vrtx-multipleinputfield-field-enriched");
    $(movedElmEnrichment.remove())
      .insertAfter(curElm.find(".vrtx-multipleinputfield-json-wrapper").filter(":last"));
  }
}

/* ^ User Enrichments */

/* DEHANCE PART */
function saveMultipleInputFields(content, arrSeperator) {
  var multipleFields = (typeof content !== "undefined")
                       ? content.find(".vrtx-multipleinputfields")
                       : $(".vrtx-multipleinputfields");
  var arrSep = (typeof arrSeperator === "string") ? arrSeperator : ",";
  for (var i = 0, len = multipleFields.length; i < len; i++) {
    var multiple = $(multipleFields[i]);
    var multipleInput = multiple.find("> input, .ui-accordion-content > input");
    if (!multipleInput.length) continue;
    var multipleInputFields = multiple.find(".vrtx-multipleinputfield");
    if (!multipleInputFields.length) {
      multipleInput.val("");
      continue;
    }
    var result = "";
    for (var j = 0, len2 = multipleInputFields.length; j < len2; j++) {
      var multipleInputField = $(multipleInputFields[j]);
      var fields = multipleInputField.find("input");
      if (!fields.length) {
        fields = multipleInputField.find("select");
      }
      var fieldsLen = fields.length;
      if (!fieldsLen) continue;
      for(var k = 0; k < fieldsLen; k++) {
        result += $.trim(fields[k].value);
        if(fieldsLen > 1) {
          result += "###";
        }
      }
      if (j < (len2 - 1)) {
        result += arrSep;
      }
    }
    multipleInput.val(result);
  }
}

/* Multiple JSON boxes */

function initJsonMovableElements() {
  $.when(vrtxEditor.multipleFieldsBoxesDeferred, vrtxEditor.multipleBoxesTemplatesContractBuilt).done(function () {
    for (var i = 0, len = vrtxEditor.multipleBoxesTemplatesContract.length; i < len; i++) {
      var jsonName = vrtxEditor.multipleBoxesTemplatesContract[i].name;
      var jsonElm = $("#" + jsonName);
      jsonElm.append(vrtxEditor.htmlFacade.getJsonBoxesInteractionsButton("add", vrtxAdmin.multipleFormGroupingMessages.add, "<span class='add-arrow'></span>"))
        .find(".vrtx-add-button").data({
        'number': i
      });
      vrtxEditor.multipleFieldsBoxes[jsonName] = {counter: jsonElm.find(".vrtx-json-element").length, limit: -1};
    }

    accordionJsonInit();

    JSON_ELEMENTS_INITIALIZED.resolve();
  });
  
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-json .vrtx-move-down-button", function (ref) {
    swapContent($(ref), 1);
  }, "clickOrEnter");
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-json .vrtx-move-up-button", function (ref) {
    swapContent($(ref), -1);
  }, "clickOrEnter");
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-json .vrtx-add-button", addJsonField, "clickOrEnter");
  eventListen(vrtxAdmin.cachedAppContent, "click keypress", ".vrtx-json .vrtx-remove-button", removeJsonField, "clickOrEnter");
}

function addJsonField(ref) {
  var btn = $(ref);
  var jsonParent = btn.closest(".vrtx-json");
  var numOfElements = jsonParent.find(".vrtx-json-element").length;
  var j = vrtxEditor.multipleBoxesTemplatesContract[parseInt(btn.data('number'), 10)];
  var htmlTemplate = "";
  var inputFieldName = "";

  // Add correct HTML for Vortex type
  var types = j.a;

  var ckHtmls = [];
  var ckSimpleHtmls = [];
  var dateTimes = [];

  for (var i in types) {
    inputFieldName = j.name + "." + types[i].name + "." + vrtxEditor.multipleFieldsBoxes[j.name].counter;
    htmlTemplate += vrtxEditor.htmlFacade.getTypeHtml(types[i], inputFieldName);
    switch (types[i].type) {
      case "html":        ckHtmls.push(inputFieldName);       break;
      case "simple_html": ckSimpleHtmls.push(inputFieldName); break;
      case "datetime":    dateTimes.push(inputFieldName);     break;
    }
  }

  // Interaction
  var isImmovable = jsonParent && jsonParent.hasClass("vrtx-multiple-immovable");
  var removeButton = vrtxEditor.htmlFacade.getJsonBoxesInteractionsButton('remove', "", vrtxAdmin.multipleFormGroupingMessages.remove);

  var newElementId = "vrtx-json-element-" + j.name + "-" + vrtxEditor.multipleFieldsBoxes[j.name].counter;
  var newElementHtml = htmlTemplate + "<input type=\"hidden\" class=\"id\" value=\"" + vrtxEditor.multipleFieldsBoxes[j.name].counter + "\" \/>" + removeButton;
  if (!isImmovable && numOfElements > 0) {
    var moveUpButton = vrtxEditor.htmlFacade.getJsonBoxesInteractionsButton('move-up', vrtxAdmin.multipleFormGroupingMessages.moveUp, "<span class='moveup-arrow'></span>");
    newElementHtml += moveUpButton;
  }
  newElementHtml = "<div class='vrtx-json-element last' id='" + newElementId + "'>" + newElementHtml + "<\/div>";

  var oldLast = jsonParent.find(".vrtx-json-element.last");
  if (oldLast.length) {
    oldLast.removeClass("last");
  }

  jsonParent.find(".vrtx-add-button").before(newElementHtml);

  var accordionWrapper = btn.closest(".vrtx-json-accordion");
  var hasAccordion = accordionWrapper.length;

  if (!isImmovable && numOfElements > 0 && oldLast.length) {
    var moveDownButton = vrtxEditor.htmlFacade.getJsonBoxesInteractionsButton('move-down', vrtxAdmin.multipleFormGroupingMessages.moveDown, "<span class='movedown-arrow'></span>");
    if (hasAccordion) {
      oldLast.find("> div.ui-accordion-content").append(moveDownButton);
    } else {
      oldLast.append(moveDownButton);
    }
  }
  if (hasAccordion) {
    accordionJsonNew(accordionWrapper);
  }

  // Init CKEditors and enhance date inputfields
  var ckHtmlsLen = ckHtmls.length,
    ckSimpleHtmlsLen = ckSimpleHtmls.length,
    dateTimesLen = dateTimes.length,
    rteFacade = vrtxEditor.richtextEditorFacade;
  if (ckHtmlsLen || ckSimpleHtmlsLen || dateTimesLen) {
    var checkForAppendComplete = setTimeout(function () {
      if ($("#" + newElementId + " .vrtx-remove-button").length) {
        for (var i = 0; i < ckHtmlsLen; i++) {
          rteFacade.setup({
            name: ckHtmls[i],
            isCompleteEditor: true,
            defaultLanguage: requestLang,
            cssFileList: cssFileList
          });
        }
        for (i = 0; i < ckSimpleHtmlsLen; i++) {
          rteFacade.setup({
            name: ckSimpleHtmls[i],
            defaultLanguage: requestLang,
            cssFileList: cssFileList,
            simple: true
          });
        }
        datepickerEditor.initFields(dateTimes);
      } else {
        setTimeout(checkForAppendComplete, 25);
      }
    }, 25);
  }
  
  // Box picture
  initPictureAddJsonField(btn.closest(".vrtx-json").find(".vrtx-json-element:last"));
  // Count
  vrtxEditor.multipleFieldsBoxes[j.name].counter++;
}

function removeJsonField(ref) {
  var btn = $(ref),
      removeElement = btn.closest(".vrtx-json-element"),
      accordionWrapper = removeElement.closest(".vrtx-json-accordion"),
      hasAccordion = accordionWrapper.length,
      removeElementParent = removeElement.parent(),
      textAreas = removeElement.find("textarea"),
      rteFacade = vrtxEditor.richtextEditorFacade;
  for (var i = 0, len = textAreas.length; i < len; i++) {
    rteFacade.removeInstance(textAreas[i].name);
  }

  var updateLast = removeElement.hasClass("last");
  removeElement.remove();
  removeElementParent.find(".vrtx-json-element:first .vrtx-move-up-button").remove();
  var newLast = removeElementParent.find(".vrtx-json-element:last");
  newLast.find(".vrtx-move-down-button").remove();
  if (updateLast) {
    newLast.addClass("last");
  }
  if (hasAccordion) {
    accordionJsonRefresh(accordionWrapper.find(".fieldset"), false);
    accordionJson.create();
  }
}

// Move up or move down  
function swapContent(moveBtn, move) {
  var curElm = moveBtn.closest(".vrtx-json-element");
  var accordionWrapper = curElm.closest(".vrtx-json-accordion");
  var hasAccordion = accordionWrapper.length;
  var movedElm = (move > 0) ? curElm.next(".vrtx-json-element") : curElm.prev(".vrtx-json-element");
  var curCounter = curElm.find("input.id").val();
  var moveToCounter = movedElm.find("input.id").val();

  var j = vrtxEditor.multipleBoxesTemplatesContract[parseInt(curElm.closest(".vrtx-json").find(".vrtx-add-button").data('number'), 10)];
  var types = j.a;
  var swapElementFn = swapElement,
      rteFacade = vrtxEditor.richtextEditorFacade;
  var runOnce = false;
  for (var i = 0, len = types.length; i < len; i++) {
    var field = j.name + "\\." + types[i].name + "\\.";
    var fieldEditor = field.replace(/\\/g, "");

    var elementId1 = "#" + field + curCounter;
    var elementId2 = "#" + field + moveToCounter;
    var element1 = $(elementId1);
    var element2 = $(elementId2);

    /* We need to handle special cases like CK fields and date */
    var ckInstanceName1 = fieldEditor + curCounter;
    var ckInstanceName2 = fieldEditor + moveToCounter;

    if (rteFacade.isInstance(ckInstanceName1) && rteFacade.isInstance(ckInstanceName2)) {
      rteFacade.swap(ckInstanceName1, ckInstanceName2);
    } else if (element1.hasClass("date") && element2.hasClass("date")) {
      var element1Wrapper = element1.closest(".vrtx-string");
      var element2Wrapper = element2.closest(".vrtx-string");
      swapElementFn(element1Wrapper.find(elementId1 + '-date'), element2Wrapper.find(elementId2 + '-date'));
      swapElementFn(element1Wrapper.find(elementId1 + '-hours'), element2Wrapper.find(elementId2 + '-hours'));
      swapElementFn(element1Wrapper.find(elementId1 + '-minutes'), element2Wrapper.find(elementId2 + '-minutes'));
    }

    swapElementFn(element1, element2);

    if (hasAccordion && !runOnce) {
      accordionJson.updateHeader(element1, true, false);
      accordionJson.updateHeader(element2, true, false);
      runOnce = true;
    }
    /* Do we need these on all elements? */
    element1.blur();
    element2.blur();
    element1.change();
    element2.change();
  }
  curElm.focusout();
  movedElm.focusout();

  if (hasAccordion) { /* Wait with scroll until accordion switch */
    vrtxEditor.multipleFieldsBoxesAccordionSwitchThenScrollTo = movedElm;
    accordionWrapper.find(".fieldset").accordion("option", "active", (movedElm.index() - 1)).accordion("option", "refresh");
  } else {
    scrollToElm(movedElm);
  }
}

function swapElement(elemA, elemB) {
  var tmp = elemA.val();
  elemA.val(elemB.val());
  elemB.val(tmp);
}

/* NOTE: can be used generally if boolean hasScrollAnim is turned on */

function scrollToElm(movedElm) {
  if (typeof movedElm.offset() === "undefined") return;
  var absPos = movedElm.offset();
  var absPosTop = absPos.top;
  var stickyBar = $("#vrtx-editor-title-submit-buttons");
  if (stickyBar.css("position") === "fixed") {
    var stickyBarHeight = stickyBar.height();
    absPosTop -= (stickyBarHeight <= absPosTop) ? stickyBarHeight : 0;
  }
  $('body').scrollTo(absPosTop, 250, {
    easing: 'swing',
    queue: true,
    axis: 'y',
    onAfter: function () {
      vrtxEditor.multipleFieldsBoxesAccordionSwitchThenScrollTo = null;
    }
  });
}

/**
 * HTML facade (Input/JSON=>Template Engine=>HTML)
 *
 * @namespace
 */
VrtxEditor.prototype.htmlFacade = {
  /* 
   * Turn a block of JSON into HTML (only working for Schedule)
   */
  jsonToHtml: function(isMedisin, id, sessionId, idForLookup, session, fixedResourcesUrl, fixedResources, descs, i18n, embeddedAdminService) {
    var html = "";
    var multiples = [];
    var rtEditors = [];
    var vrtxEdit = vrtxEditor;
    var sep = userEnrichmentSeperators;
    
    for(var name in descs) {
      var desc = descs[name];
      if((desc.notMedisin && isMedisin) || (desc.onlyMedisin && !isMedisin)) {
        continue;
      }
      var descProps = jQuery.extend(true, [], desc.props),
          val = session[name] != undefined ? session[name] : fixedResources[name],
          origVal = "",
          propsVal = "",
          readOnly = session.vrtxOrphan,
          browsable = false,
          hasOrig = false;
    
      var origName = name.split("vrtx")[1];
      if(origName) {
        var origVal = session[origName.toLowerCase()];
        if(origVal && origVal != "") {
          if(val == undefined) {
            val = origVal;
          }
          hasOrig = true;
        }
      }
      switch(desc.type) {
        case "json":
          for(var i = 0, descPropsLen = descProps.length; i < descPropsLen; i++) {
            descProps[i].title = i18n[name + "-" + descProps[i].name]; // Placeholder
            if(desc.multiple && desc.props[i].type === "resource_ref" && !isMedisin) {
              browsable = true;
            }
          }
          if(val && val.length) {
            for(var j = 0, propsLen = val.length; j < propsLen; j++) {
              for(i = 0; i < descPropsLen; i++) {
                if(desc.props[i].type === "enrichedUrl") {
                  propsVal += (val[j][descProps[i].name] || "") + sep.url;
                } else if(desc.props[i].type === "enrichedText") {
                  propsVal += (val[j][descProps[i].name] || "") + sep.text;
                } else {
                  propsVal += (val[j][descProps[i].name] || "") + "###";
                }
              }
              if(j < (propsLen - 1)) propsVal += "$$$";
            }
          }
        case "string":
          val = (propsVal !== "") ? propsVal : val;
          val = (desc.multiple && typeof val === "object" && val.length != undefined) ? val.join(",") : val;
          if(desc.multiple) {
            multiples.push({
              name: name,
              json: descProps ? descProps : null, 
              movable: desc.multiple.movable,
              browsable: browsable,
              readOnly: readOnly
            });
          }
          var nameI18n = isMedisin && name === "vrtxResources" ? name + "NotFixed" : name;
          html += vrtxEdit.htmlFacade.getStringField({ title: i18n[nameI18n],
                                                       name: (desc.autocomplete ? "vrtx-autocomplete-" + desc.autocomplete + " " : "") + name + " " + name + "-" + sessionId,
                                                       id: name + "-" + sessionId,
                                                       val: val,
                                                       size: desc.size,
                                                       divide: desc.divide,
                                                       readOnly: readOnly
                                                     }, name);
          break;
        case "json-fixed":
          if(fixedResourcesUrl && val.length) {
            for(i = 0, len = val.length; i < len; i++) {
              var fr = val[i];
              var folderUrl = fr.folderUrl;
              var folderType = fr.folderType;
              var folderName = fr.folderName;
              var folderRoot = fr.folderRoot;
              html += "<div class='vrtx-simple-html vrtx-fixed-resources vrtx-fixed-resources-" + folderType + (i == 0 && desc.divide ? " divide-" + desc.divide : "") + "'>" + 
                      "<label>" + i18n[name + "-" + folderType] +
                        (i18n[name + "-" + folderType + "-info"] ? "<abbr tabindex='0' class='tooltips label-tooltips' title='" + i18n[name + "-" + folderType + "-info"] + "'></abbr>" : "") +
                      "</label>";
              if(folderUrl && folderUrl.length) {
                /* Iframe placeholder */
                html += "<div class='admin-fixed-resources-iframe' data-src='" + folderUrl + embeddedAdminService + "'></div>";
              } else {
                html += "<a class='vrtx-button create-fixed-resources-folder' id='create-fixed-resources-folder-" + idForLookup +
                        "SID" + sessionId +
                        "SUBF" + folderName +
                        ((folderRoot && folderRoot != "") ? ("PARENTR" + encodeURIComponent(folderRoot)) : "") +
                        "' href='javascript:void(0);'>" + i18n[name + "CreateFolder"] + "</a>" + "<p class='fixed-resources-permissions-info'>" + i18n.vrtxResourcesFixedInfo + "</p>";
              }
              html += "</div>";
            }
          }
          break;
        case "html":
          html += vrtxEdit.htmlFacade.getSimpleHtmlField({ title: i18n[name],
                                                           name: name + "-" + sessionId,
                                                           id: name + "-" + sessionId,
                                                           divide: desc.divide,
                                                           val: val
                                                         }, name + "-" + sessionId);
          rtEditors.push({ name: name + "-" + sessionId, readOnly: readOnly });
          break;
        case "checkbox":
          if(!session.vrtxOrphan) {
            if(!origVal || origVal !== desc.checkedVal) {
              html += vrtxEdit.htmlFacade.getCheckboxField({ title: i18n[name],
                                                             name: name + "-" + sessionId,
                                                             id: name + "-" + sessionId,
                                                             checked: (val === desc.checkedVal ? val : null),
                                                             divide: desc.divide,
                                                             tooltip: i18n.cancelledVortexTooltip
                                                           }, name);
            } else {
              html += "<abbr tabindex='0' class='tooltips cancelled-tp' title='" + i18n.cancelledTPTooltip + "'></abbr>";
            }
          }
          break;
        default:
          break;
      }
    }
    return { html: html, multiples: multiples, rtEditors: rtEditors };
  },
 /* 
  * Turn a block of HTML/DOM into JSON (only working for Schedule)
  */
  htmlToJson: function (isMedisin, sessionElms, sessionId, descs, rawOrig, rawOrigTP, rawPtr) {
    var vrtxEdit = vrtxEditor;
    var hasChanges = false;
    var editorDetectChangeFunc = editorDetectChange;
    
    for(var name in descs) {
      var desc = descs[name],
          val = "";
      // Skip fixed resources and branch: Medisin | Not Medisin
      if(desc.type === "json-fixed" || (desc.notMedisin && isMedisin) 
                                    || (desc.onlyMedisin && !isMedisin)) {
        continue;
      } else if(desc.type === "html") {
        // XXX: support multiple CK-fields starting with same name
        var elm = sessionElms.find("textarea[name^='" + name + "']");
      } else {
        var elm = sessionElms.find("input[name='" + name + "']");
      }
      if(!elm.length) continue;

      if(desc.type === "checkbox") {
        if(elm[0].checked) {
          val = desc.checkedVal;
        }
      } else if(desc.type === "html") {
        val = vrtxEdit.richtextEditorFacade.getInstanceValue(elm.attr("name"));
      } else {
        // Reconstruct data from flattened string to objects
        val = elm.val(); // To string (string)
        if(desc.multiple && val.length) { // To array (multiple)
          val = val.split("$$$");
        }
        if(desc.type === "json" && val.length) { // Object props into array (JSON multiple)
          var arrProps = [];
          for(var i = 0, arrLen = val.length; i < arrLen; i++) {
            var newProp = null;
            var prop = val[i].split("###");
            for(var j = 0, descPropsLen = desc.props.length; j < descPropsLen; j++) { // Definition
              if(desc.props[j].type == "enrichedUrl"
              || desc.props[j].type == "enrichedText") continue;
              
              if(prop[j] !== "") {
                if(!newProp) {
                  newProp = {};
                }
                newProp[desc.props[j].name] = prop[j];
              }
            }
            if(newProp) {
              arrProps.push(newProp);
            }
          }
          val = arrProps;
        }
      }

      // Has content
      if(val && val.length) {
        if(editorDetectChangeFunc(sessionId, val, rawOrig[name], name === "vrtxResourcesText")) { // Has changed
          var isChangedFromTP = editorDetectChangeFunc(sessionId, val, rawOrigTP[name.split("vrtx")[1].toLowerCase()], name === "vrtxResourcesText");
          if(isChangedFromTP) { // Differs from TP
            vrtxAdmin.log({msg: "ADD / CHANGE " + name + (typeof val === "string" ? " " + val : "")});
            rawPtr[name] = val;
            hasChanges = true;
          } else { // Otherwise Delete
            if(rawOrig[name] != undefined) { // If exists
              vrtxAdmin.log({msg: "DEL " + name + (typeof val === "string" ? " " + val : "")});
              delete rawPtr[name];
              hasChanges = true;
            }
          }
        }
      } else { // Empty
        // Is "vrtxStaff" and has "staff" set to []
        if(name === "vrtxStaff" && rawOrigTP[name.split("vrtx")[1].toLowerCase()]) {
	      if(rawPtr[name] == undefined || rawPtr[name].length > 0) {
            vrtxAdmin.log({msg: "DEL EMPTY " + name});
            rawPtr[name] = [];
            hasChanges = true;
	      }
        } else { // Otherwise Delete
	      if(rawOrig[name] != undefined) { // If exists
            vrtxAdmin.log({msg: "DEL " + name});
            delete rawPtr[name];
            hasChanges = true;
	      }
	    }
      }
    }
    return hasChanges;
  },
  /* 
   * Interaction
   */
  getMultipleInputfieldsInteractionsButton: function (clazz, name, idstr, title, text) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["button"], {
      type: clazz,
      name: name,
      idstr: idstr,
      title: title,
      buttonText: text
    });
  },
  getMultipleInputFieldsAddButton: function (name, size, isBrowsable, isMovable, isDropdown, json) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["add-button"], {
      name: name,
      size: size,
      isBrowsable: isBrowsable,
      isMovable: isMovable,
      isDropdown: isDropdown,
      title: vrtxAdmin.multipleFormGroupingMessages.add,
      buttonText: "<span class='add-arrow'></span>",
      json: json
    });
  },
  getJsonBoxesInteractionsButton: function (clazz, title, text) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["add-remove-move"], {
      clazz: clazz,
      title: title,
      buttonText: text
    });
  },
  getAccordionInteraction: function (level, id, clazz, title, content) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["accordion"], {
      level: level,
      id: id,
      clazz: clazz,
      title: title,
      content: content
    });
  },
  /* 
   * Type / fields 
   */
  getMultipleInputfield: function (name, idstr, i, value, size, browseButton, removeButton, moveUpButton, moveDownButton, isDropdown, json, isReadOnly, hasEnrichedText, hasEnrichedUrl) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["multiple-inputfield"], {
      idstr: idstr,
      i: i,
      value: value,
      size: size,
      browseButton: browseButton,
      removeButton: removeButton,
      moveUpButton: moveUpButton,
      moveDownButton: moveDownButton,
      isDropdown: isDropdown,
      dropdownArray: "dropdown" + name,
      json: json,
      isReadOnly: isReadOnly,
      hasEnrichedText: hasEnrichedText,
      hasEnrichedUrl: hasEnrichedUrl
    });
  },
  getTypeHtml: function (elem, inputFieldName) {
    var methodName = "get" + this.typeToMethodName(elem.type) + "Field";
    if (this[methodName]) { // If type maps to method
      return this[methodName](elem, inputFieldName);
    }
    return "";
  },
  typeToMethodName: function (str) { // Replaces "_" with "" and camelCase Vortex types. XXX: Optimize RegEx
    return str.replace("_", " ").replace(/(\w)(\w*)/g, function (g0, g1, g2) {
      return g1.toUpperCase() + g2.toLowerCase();
    }).replace(" ", "");
  },
  getStringField: function (elem, inputFieldName) {
    if (elem.dropdown && elem.valuemap) {
      return this.getDropdown(elem, inputFieldName);
    } else {
      return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["string"], {
        classes: "vrtx-string" + " " + elem.name,
        elemTitle: elem.title,
        inputFieldName: inputFieldName,
        elemId: elem.id || inputFieldName,
        elemVal: elem.val,
        elemSize: elem.size || 40,
        elemDivide: elem.divide,
        elemPlaceholder: elem.placeholder,
        elemReadOnly: elem.readOnly
      });
    }
  },
  getSimpleHtmlField: function (elem, inputFieldName) {
    return this.getHtmlField(elem, inputFieldName, "vrtx-simple-html");
  },
  getHtmlField: function (elem, inputFieldName, htmlType) {
    if (typeof htmlType === "undefined") htmlType = "vrtx-html";
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["html"], {
      classes: htmlType + " " + elem.name,
      elemTitle: elem.title,
      inputFieldName: inputFieldName,
      elemId: elem.id || inputFieldName,
      elemDivide: elem.divide,
      elemVal: elem.val
    });
  },
  getBooleanField: function (elem, inputFieldName) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["radio"], {
      elemTitle: elem.title,
      inputFieldName: inputFieldName
    });
  },
  getCheckboxField: function (elem, inputFieldName) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["checkbox"], {
      elemTitle: elem.title,
      elemId: elem.id || inputFieldName,
      elemChecked: elem.checked,
      elemTooltip: elem.tooltip,
      elemDivide: elem.divide,
      inputFieldName: inputFieldName
    });
  },
  getDropdown: function (elem, inputFieldName) {
    var htmlOpts = [];
    for (var i in elem.valuemap) {
      var keyValuePair = elem.valuemap[i];
      var keyValuePairSplit = keyValuePair.split("$");
      htmlOpts.push({
        key: keyValuePairSplit[0],
        value: keyValuePairSplit[1]
      });
    }
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["dropdown"], {
      classes: "vrtx-string" + " " + elem.name,
      elemTitle: elem.title,
      inputFieldName: inputFieldName,
      options: htmlOpts
    });
  },
  getDatetimeField: function (elem, inputFieldName) {
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates["date"], {
      elemTitle: elem.title,
      inputFieldName: inputFieldName
    });
  },
  getImageRefField: function (elem, inputFieldName) {
    return this.getBrowseField(elem, inputFieldName, "browse-images", "vrtx-image-ref", "", 30, {
      previewTitle: browseImagesPreview
    });
  },
  getResourceRefField: function (elem, inputFieldName) {
    return this.getBrowseField(elem, inputFieldName, "browse", "vrtx-resource-ref", "File", 40, {});
  },
  getMediaRefField: function (elem, inputFieldName) {
    return this.getBrowseField(elem, inputFieldName, "browse", "vrtx-media-ref", "Media", 30, {});
  },
  getBrowseField: function (elem, inputFieldName, templateName, clazz, type, size, extraConfig) {
    var config = {
      clazz: clazz,
      elemTitle: elem.title,
      inputFieldName: inputFieldName,
      baseCKURL: vrtxAdmin.multipleFormGroupingPaths.baseBrowserURL,
      baseFolderURL: vrtxAdmin.multipleFormGroupingPaths.baseFolderURL,
      basePath: vrtxAdmin.multipleFormGroupingPaths.basePath,
      browseButtonText: vrtxAdmin.multipleFormGroupingMessages.browse,
      type: type,
      size: size
    };
    for (var key in extraConfig) { // Copy in extra config
      config[key] = extraConfig[key];
    }
    return vrtxAdmin.templateEngineFacade.render(vrtxEditor.multipleFieldsBoxesTemplates[templateName], config);
  }
};


/*-------------------------------------------------------------------*\
    9. Accordions
\*-------------------------------------------------------------------*/

/**
 * Initialize grouped as accordion
 * @param {string} subGroupedSelector The sub grouping selector
 * @param {string} customSpeed The custom animation speed (only "fast" or "slow")
 * @this {VrtxEditor}
 */
VrtxEditor.prototype.accordionGroupedInit = function accordionGroupedInit(subGroupedSelector, customSpeed) { /* param name pending */
  var vrtxEdit = this,
    _$ = vrtxAdmin._$;

  var accordionWrpId = "accordion-grouped", // TODO: multiple accordion group pr. page
      groupedSelector = ".vrtx-grouped" + ((typeof subGroupedSelector !== "undefined") ? subGroupedSelector : "") + ", " +
                        ".vrtx-pseudo-grouped" + ((typeof subGroupedSelector !== "undefined") ? subGroupedSelector : ""),
      grouped = vrtxEdit.editorForm.find(groupedSelector);

  grouped.wrapAll("<div id='" + accordionWrpId + "' />");

  accordionContentSplitHeaderPopulators(true);
  
  var opts = {
    elem: vrtxEdit.editorForm.find("#" + accordionWrpId),
    headerSelector: "> div > .header",
    onActivate: function (e, ui, accordion) {
      accordionGrouped.updateHeader(ui.oldHeader, false, false);
    }
  };
  if(typeof customSpeed !== "undefined" && customSpeed === "fast") {
    opts.animationSpeed = 200;
  }
  accordionGrouped = new VrtxAccordion(opts);

  // Because accordion needs one content wrapper
  for (var i = grouped.length; i--;) {
    var group = $(grouped[i]);
    if (group.hasClass("vrtx-pseudo-grouped")) {
      group.find("> label").wrap("<div class='header' />");
      group.addClass("vrtx-grouped");
    } else {
      group.find("> *:not(.header)").wrapAll("<div />");
    }
    accordionGrouped.updateHeader(group, false, true);
  }
  
  accordionGrouped.create();
  opts.elem.addClass("fast");
};

function accordionJsonInit() {
  accordionContentSplitHeaderPopulators(true);
  accordionJsonRefresh($(".vrtx-json-accordion .fieldset"), false);

  // Because accordion needs one content wrapper
  for (var grouped = $(".vrtx-json-accordion .vrtx-json-element"), i = grouped.length; i--;) {
    var group = $(grouped[i]);
    group.find("> *").wrapAll("<div />");
    accordionJson.updateHeader(group, true, true);
  }
  accordionJson.create();
}

function accordionJsonNew(accordionWrapper) {
  var accordionContent = accordionWrapper.find(".fieldset");
  var group = accordionContent.find(".vrtx-json-element").filter(":last");
  group.find("> *").wrapAll("<div />");
  group.prepend('<div class="header">' + (vrtxAdmin.lang !== "en" ? "Intet innhold" : "No content") + '</div>');
  accordionContentSplitHeaderPopulators(false);
  
  accordionJsonRefresh(accordionContent, false);
  accordionJson.create();
}

function accordionJsonRefresh(elem, active) {
  accordionJson = new VrtxAccordion({
    elem: elem,
    headerSelector: "> div > .header",
    activeElem: active,
    onActivate: function (e, ui, accordion) {
      accordion.updateHeader(ui.oldHeader, true, false);
      if (vrtxEditor.multipleFieldsBoxesAccordionSwitchThenScrollTo) {
        scrollToElm(vrtxEditor.multipleFieldsBoxesAccordionSwitchThenScrollTo);
      }
    }
  });
}

// XXX: avoid hardcoded enhanced fields..
function accordionContentSplitHeaderPopulators(init) {
  var sharedTextItems = $("#editor.vrtx-shared-text #shared-text-box .vrtx-json-element");
  var semesterResourceLinksItems = $("#editor.vrtx-semester-page .vrtx-grouped[class*=link-box]");

  if(sharedTextItems.length) {
    if (!init) {
      sharedTextItems = sharedTextItems.filter(":last");
    }
    sharedTextItems.find(".title input").addClass("header-populators");
    sharedTextItems.find(".vrtx-html").addClass("header-empty-check-or");
  } else if (semesterResourceLinksItems.length) {
    semesterResourceLinksItems.find(".vrtx-string input[id*=-title]").addClass("header-populators");
    semesterResourceLinksItems.find(".vrtx-json-element").addClass("header-empty-check-and");
    if(init) {
      vrtxAdmin.cachedDoc.on("click", semesterResourceLinksItems.find(".vrtx-add-button"), function(e) {
        semesterResourceLinksItems.find(".vrtx-json-element:last").addClass("header-empty-check-and"); 
      });
    }
  }
}


/*-------------------------------------------------------------------*\
    10. Send to approval
\*-------------------------------------------------------------------*/

VrtxEditor.prototype.initSendToApproval = function initSendToApproval() {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  vrtxAdm.cachedDoc.on("click", "#vrtx-send-to-approval, #vrtx-send-to-approval-global", function (e) {
    vrtxEditor.openSendToApproval(this);
    e.stopPropagation();
    e.preventDefault();
  });

  vrtxAdm.cachedDoc.on("click", "#dialog-html-send-approval-content .vrtx-focus-button", function (e) {
    vrtxEditor.saveSendToApproval(_$(this));
    e.stopPropagation();
    e.preventDefault();
  });
};

VrtxEditor.prototype.openSendToApproval = function openSendToApproval(link) {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  var id = link.id + "-content";
  var dialogManageCreate = _$("#" + id);
  if (!dialogManageCreate.length) {
    vrtxAdm.serverFacade.getHtml(link.href, {
      success: function (results, status, resp) {
        vrtxAdm.cachedBody.append("<div id='" + id + "'>" + _$(_$.parseHTML(results)).find("#contents").html() + "</div>");
        dialogManageCreate = _$("#" + id);
        dialogManageCreate.hide();
        vrtxEditor.openSendToApprovalOpen(dialogManageCreate, link);
      }
    });
  } else {
    vrtxEditor.openSendToApprovalOpen(dialogManageCreate, link);
  }
};

VrtxEditor.prototype.openSendToApprovalOpen = function openSendToApprovalOpen(dialogManageCreate, link) {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  var hasEmailFrom = dialogManageCreate.find("#emailFrom").length;
  var d = new VrtxHtmlDialog({
    name: "send-approval",
    html: dialogManageCreate.html(),
    title: link.title,
    width: 430,
    height: 620
  });
  d.open();
  var dialog = _$(".ui-dialog");
  if (dialog.find("#emailTo").val().length > 0) {
    if (hasEmailFrom) {
      dialog.find("#emailFrom")[0].focus();
    } else {
      dialog.find("#yourCommentTxtArea")[0].focus();
    }
  }
};

VrtxEditor.prototype.saveSendToApproval = function saveSendToApproval(btn) {
  var vrtxAdm = vrtxAdmin,
    _$ = vrtxAdm._$;

  var form = btn.closest("form");
  var url = form.attr("action");
  var dataString = form.serialize();
  vrtxAdm.serverFacade.postHtml(url, dataString, {
    success: function (results, status, resp) {
      var formParent = form.parent();
      formParent.html(_$($.parseHTML(results)).find("#contents").html());
      var successWrapper = formParent.find("#email-approval-success");
      if (successWrapper.length) { // Save async if sent mail
        successWrapper.trigger("click");
        setTimeout(function () {
          _$("#vrtx-save-view-shortcut").trigger("click");
        }, 250);
      }
    }
  });
};


/*-------------------------------------------------------------------*\
    11. Utils
\*-------------------------------------------------------------------*/

/**
 * Check if string contains substring
 *
 * @this {VrtxEditor}
 * @param {string} string The string
 * @param {string} substring The substring
 * @return {boolean} Existance 
 */
VrtxEditor.prototype.contains = function contains(string, substring) {
  return string.indexOf(substring) != -1;
};

/**
 * Replace tags
 * 
 * XXX: Should be chainable / jQuery fn
 *
 * @this {VrtxEditor}
 * @param {string} selector The context selector
 * @param {string} tag The selector for tags to be replaced
 * @param {string} replacementTag The replacement tag name
 */
VrtxEditor.prototype.replaceTag = function replaceTag(selector, tag, replacementTag) {
  selector.find(tag).replaceWith(function () {
    return "<" + replacementTag + ">" + $(this).text() + "</" + replacementTag + ">";
  });
};

/**
 * Handler for events and init code applying a callback function with parameters
 * 
 * - If no parameters are provided then $(selector) is used as default
 * - 'this' in the callback is vrtxEditor with its prototype chain
 * 
 * @example
 * // Process special list links
 * vrtxEditor.initEventHandler("#list a.special", {
 *   callback: processSpecialListLinksFn
 * });
 *
 * @this {VrtxEditor}
 * @param {string} selector The selector
 * @param {object} opts The options 
 */
VrtxEditor.prototype.initEventHandler = function initEventHandler(selector, opts) {
  var select = $(selector);
  if (!select.length) return;

  opts.event = opts.event || "click";
  opts.wrapper = opts.wrapper || document;
  opts.callbackParams = opts.callbackParams || [$(selector)];
  opts.callbackChange = opts.callbackChange || function (p) {};

  var vrtxEdit = this;

  opts.callback.apply(vrtxEdit, opts.callbackParams, true);
  $(opts.wrapper).on(opts.event, select, function () {
    opts.callback.apply(vrtxEdit, opts.callbackParams, false);
  });
};

function autocompleteUsernames(elms, useEnrichment) {
  var _$ = vrtxAdmin._$;
  var autocompleteTextfields = elms.find('.vrtx-textfield');
  for (var i = autocompleteTextfields.length; i--;) {
    var id = autocompleteTextfields[i].id;
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
  for (var i = autocompleteTextfields.length; i--;) {
    setAutoComplete(autocompleteTextfields[i].id, 'tags', vrtxAdmin.tagAutocompleteParams);
  }
}

/* ^ Vortex Editor */
