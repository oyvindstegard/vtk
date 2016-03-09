/*
 * @example An iframe-based dialog with custom button handling logics.
 * TODO: fix VTK-2010 and refactor, optimalize, use API etc. for existing code
 */
var paramsStandard = {
  "url": "",
  "poster": "",
  "width": 507,
  "height": 322,
  "autoplay": "false",
  "contentType": "",
  "streamType": "",
  "showdl": ""
};

var params = {
  "url": "",
  "poster": "",
  "width": 507,
  "height": 322,
  "autoplay": "false",
  "contentType": "",
  "streamType": "",
  "showdl": ""
};

var divAlign = "";

(function () {
  CKEDITOR.plugins.add('mediaembed', {
    requires: ['iframedialog'],
    init: function (editor) {
      var lang = editor.lang.mediaembed;
      var me = this;
      CKEDITOR.dialog.add('MediaEmbedDialog', function (editor) {
        return {
          title: 'Embed Media Dialog',
          minWidth: 550,
          minHeight: 200,
          contents: [{
            id: 'iframe',
            label: 'Embed Media',
            expand: true,
            elements: [{
              type: 'html',
              id: 'pageMediaEmbed',
              label: 'Embed Media',
              style: 'width : 100%',
              html: '<iframe src="' + me.path.toLowerCase() + 'dialogs/mediaembed_' + editor.config.language + '.html" frameborder="0" name="iframeMediaEmbed" id="iframeMediaEmbed" allowtransparency="1" style="width:100%;height:250px;margin:0;padding:0;"></iframe>'
            }]
          }],
          onShow: function () {
            putDialogValues("iframe#iframeMediaEmbed", true);
          },
          onOk: function () {
            insertOrModifyComponent(this.getParentEditor(), "iframe#iframeMediaEmbed", true);
          }
        };
      });


      CKEDITOR.dialog.add('MediaEmbedDialogMod', function (editor) {
        return {
          title: 'Embed Media Dialog',
          minWidth: 550,
          minHeight: 200,
          contents: [{
            id: 'iframe',
            label: 'Embed Media',
            expand: true,
            elements: [{
              type: 'html',
              id: 'pageMediaEmbedMod',
              label: 'Embed Media',
              style: 'width : 100%',
              html: '<iframe src="' + me.path.toLowerCase() + 'dialogs/mediaembed_' + editor.config.language + '.html" frameborder="0" name="iframeMediaEmbed" id="iframeMediaEmbedMod" allowtransparency="1" style="width:100%;height:250px;margin:0;padding:0;"></iframe>'
            }]
          }],
          onShow: function () {
            putDialogValues("iframe#iframeMediaEmbedMod", false);
          },
          onOk: function () {
            insertOrModifyComponent(this.getParentEditor(), "iframe#iframeMediaEmbedMod", false);
          }
        };
      });

      editor.addCommand('mediaembed', new CKEDITOR.dialogCommand('MediaEmbedDialog'));
      editor.addCommand('mediaembedmod', new CKEDITOR.dialogCommand('MediaEmbedDialogMod'));
      editor.addCommand('MediaEmbedRemove', {
        exec: function (editor) {
          var selection = editor.getSelection();
          var bookmarks = selection.createBookmarks();
          var node = selection.getStartElement();
          node.remove(false);
          selection.selectBookmarks(bookmarks);
        }
      });

      editor.ui.addButton('MediaEmbed', {
        label: 'Embed Media',
        command: 'mediaembed',
        icon: this.path.toLowerCase() + 'images/icon.png'
      });

      editor.on('doubleclick', function (evt) {
        var data = evt.data;
        var element = data.element;

        var HTML = element.getHtml();
        if (HTML.indexOf("include:media-player") == -1) {
          return null;
        }

        extractMediaPlayerProps(HTML, element);
        data.dialog = 'MediaEmbedDialogMod';
      });


      if (editor.addMenuItems) {
        // A group menu is required
        // order, as second parameter, is not required
        editor.addMenuGroup('mediaembed');

        // Create a menu item
        editor.addMenuItems({
          MediaEmbedDialogMod: {
            label: lang.edit,
            command: 'mediaembedmod',
            group: 'mediaembed',
            icon: this.path.toLowerCase() + 'images/icon.png',
            order: 1
          },
          RemoveMedia: {
            label: lang.remove,
            command: 'MediaEmbedRemove',
            group: 'mediaembed',
            icon: this.path.toLowerCase() + 'images/iconremove.png',
            order: 5
          }

        });
      }

      if (editor.contextMenu) {
        editor.contextMenu.addListener(function (element, selection) {
          var HTML = element.getHtml();
          if (HTML.indexOf("include:media-player") == -1) {
            return null;
          }

          extractMediaPlayerProps(HTML, element);

          return {
            MediaEmbedDialogMod: CKEDITOR.TRISTATE_OFF,
            RemoveMedia: CKEDITOR.TRISTATE_OFF
          };
        });
      }
    }
  });
  
  // i18n         
  CKEDITOR.plugins.setLang('mediaembed', 'en', {
    mediaembed: {
      edit: 'Media properties',
      remove: 'Remove media'
    }
  });

  CKEDITOR.plugins.setLang('mediaembed', 'no', {
    mediaembed: {
      edit: 'Mediaegenskaper',
      remove: 'Fjern media'
    }
  });
  
})();

function insertOrModifyComponent(editor, iframeId, init) {

  var theIframe = $(iframeId);
  var contents = theIframe.contents();

  var url = contents.find("#txtUrl").val();
  if (url != "" && url.indexOf(".") != -1) {
    var content = "${include:media-player url=[" + encodeURI(url) + "]";
    var posterUrl = contents.find("#txtPosterUrl").val();
    if (posterUrl.length > 0) {
      content = content + " poster=[" + encodeURI(posterUrl) + "]";
    }
    var contentType = contents.find("#txtContentType").val();
    if (contentType.length > 0) {
      content = content + " content-type=[" + contentType + "]";
    }
    var width = contents.find("#txtWidth").val();
    if (width.length > 0 && width != paramsStandard.width) {
      content = content + " width=[" + width + "]";
    }
    var height = contents.find("#txtHeight").val();
    if (height.length > 0 && height != paramsStandard.height) {
      content = content + " height=[" + height + "]";
    }
    var autoplay = contents.find("#chkAutoplay");
    if (autoplay.is(':checked')) {
      content = content + " autoplay=[true]";
    }
    var streamLive = contents.find("#chkLiveStream");
    if (streamLive.is(':checked')) {
      content = content + " stream-type=[live]";
    }
    var showDL = contents.find("#chkShowDL");
    if (showDL.is(':checked')) {
      content = content + " show-download-link=[true]";
    }
    var align = contents.find("#txtAlign").val();

    if (content.length > 0) {
      content = content + "}";
    }

    var divClassType = '';
    if (contentType.length > 0 && contentType == "audio/mp3") {
      divClassType = 'vrtx-media-player-audio';
    } else if (getExtension(url) == "mp3") {
      divClassType = 'vrtx-media-player-audio';
    } else {
      divClassType = 'vrtx-media-player';
    }

    var selected = editor.getSelection().getStartElement();
    if (selected.is("p")) {
      selected.renameNode("div");
    }

    // TODO: robustify (need a more robust insertHtml() from CKEditor ppl.)
    if (init) { // Insert
      //console.log(content);
      var divClasses = divClassType;
      if (align != "") {
        divClasses = divClasses + " " + align;
      }
      //console.log(editor.getSelection().getStartElement());
      //console.log(editor.getSelection().getNative());
      selected.appendHtml('<div class="' + divClasses + '">' + content + '</div>');
    } else { // Modify
      selected.removeAttribute("class");
      if (align != "" && divClassType != "") {
        selected.addClass(divClassType);
        selected.addClass(align);
      } else {
        selected.addClass(divClassType);
      }
      selected.setText(content);
    }

  } else {
    alert("Du må spesifisere en URL");
    return false;
  }

}

function putDialogValues(iframeId, init) {
  var check = setInterval(function () { // check each 50ms if iframe content is loaded
    var theIframe = $(iframeId);
    if (theIframe) {
      var contents = theIframe.contents();
      if (contents.find("#chkAutoplay").length) {
        // Put standardvalues in dialog
        contents.find("#txtUrl").val(init ? paramsStandard.url : params.url);
        contents.find("#txtPosterUrl").val(init ? paramsStandard.poster : params.poster);
        contents.find("#txtWidth").val(init ? paramsStandard.width : params.width);
        contents.find("#txtHeight").val(init ? paramsStandard.height : params.height);
        contents.find("#txtContentType").val(init ? paramsStandard.contentType : params.contentType);

        var autoPlay = init ? paramsStandard.autoplay : params.autoplay;
        if (autoPlay == "true") {
          contents.find("#chkAutoplay").attr("checked", true);
        } else {
          contents.find("#chkAutoplay").attr("checked", false);
        }
        var liveStream = init ? paramsStandard.streamType : params.streamType;
        if (liveStream == "live") {
          contents.find("#chkLiveStream").attr("checked", true);
        } else {
          contents.find("#chkLiveStream").attr("checked", false);
        }
        var showDL = init ? paramsStandard.showdl : params.showdl;
        if (showDL == "true") {
          contents.find("#chkShowDL").attr("checked", true);
        } else {
          contents.find("#chkShowDL").attr("checked", false);
        }
        if (divAlign != "" && divAlign != " " && !init) {
          contents.find("#txtAlign").val(divAlign);
        } else {
          contents.find("#txtAlign").val("");
        }

        if (!init) {
          // Restore init values
          params.url = paramsStandard.url;
          params.poster = paramsStandard.poster;
          params.width = paramsStandard.width;
          params.height = paramsStandard.height;
          params.autoplay = paramsStandard.autoplay;
          params.streamType = paramsStandard.streamType;
          params.contentType = paramsStandard.contentType;
          params.showdl = paramsStandard.showdl;
          divAlign = "";
        }

        // Clear loop
        clearInterval(check);
      }
    }
  }, 50);
}

/** Get the file extension  */

function getExtension(url) {
  var ext = url.match(/\.(avi|asf|fla|flv|mov|mp3|mp4|m4v|mpg|mpeg|mpv|qt|swf|wma|wmv)$/i);
  if (ext != null && ext.length && ext.length > 0) {
    ext = ext[1];
  } else {
    ext = '';
  }
  return ext;
}

function extractMediaPlayerProps(HTML, element) {
  var regexp = [];
  var HTMLOrig = HTML;

  var className = element.$.className;
  divAlign = $.trim(className.replace(/vrtx-media-player[\w-]*/g, ""));

  for (var name in params) {
    if (name == "contentType") {
      regexp = new RegExp('(?:content\\-type[\\s]*?=[\\s]*?\\[[\\s]*?)(.*?)(?=[\\s]*?\\])');
    } else if (name == "streamType") {
      regexp = new RegExp('(?:stream\\-type[\\s]*?=[\\s]*?\\[[\\s]*?)(.*?)(?=[\\s]*?\\])');
    } else if (name == "showdl") {
      regexp = new RegExp('(?:show\\-download\\-link[\\s]*?=[\\s]*?\\[[\\s]*?)(.*?)(?=[\\s]*?\\])');
    } else {
      // non-capturing group for prop=. TODO: positive lookbehind (non-capturing)
      regexp = new RegExp('(?:' + name + '[\\s]*?=[\\s]*?\\[[\\s]*?)(.*?)(?=[\\s]*?\\])');
    }

    var param = regexp.exec(HTML);
    if (param != null) {
      if (param.length = 2) {
        if (name == "url" || name == "poster") {
          params[name] = decodeURI($.trim(param[1]));
        } else {
          params[name] = $.trim(param[1]); // get the capturing group
        }
      }
    }
    HTML = HTMLOrig; //TODO: is it possible to avoid this?
  }
}