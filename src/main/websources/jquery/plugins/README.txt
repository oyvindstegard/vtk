1. jquery.autocomplete.js (v1.0.2) is modified for use in Vortex (see file)

2. jquery.treeview.async.js is modified for use in Vortex with new JSON-data (see file)

3. jquery.vortexTips.js is a modified version of jquery.tinyTips.js for use in Vortex (see file)

4. jquery.forms.js has this added to it in $.fn.ajaxSubmit():

   NOTE: upgrading to newest jquery.form.js will break IE8 saving

   ------------------------------------------------------------------------------------
   // USIT added: name of clicked button
   if(typeof vrtxAdmin !== "undefined" && vrtxAdmin.editorSaveButtonName != "") {
     options.data += "&" + vrtxAdmin.editorSaveButtonName;
   }
   ------------------------------------------------------------------------------------
   
   and in $.fn.formToArray() in loop that adds files to formdata:
   ------------------------------------------------------------------------------------
   // USIT added: possible to skip uploading files
   if(typeof vrtxAdmin !== "undefined" && vrtxAdmin.uploadCopyMoveSkippedFiles[files[i].name]) {
     vrtxAdmin.log({msg: "Skip: " + files[i].name}); 
   } else {
     formdata.append(name, files[i]);
     vrtxAdmin.log({msg: "Upload and overwrite: " + files[i].name});
   }
   ------------------------------------------------------------------------------------
   
   and in $.fieldValue():
   ------------------------------------------------------------------------------------
   var val = $(el).val();
   // USIT added
    try {
      // Remove Chrome added 'font-size: 14px;' (VTK-3543)
      val = val.replace(/<span style="font-size: 14px;">([^<]*)<\/span>/g, "$1", "");
      val = val.replace(/<(p|em|strong|s|ul|ol) style="font-size: 14px;">/g, "<$1>", "");
      // Replace <p> with <div> around Vortex components (VTK-3578)
      val = val.replace(/<p>([\s]*\${(?:include\:(?:events|feed|feeds|file|folder|image-listing|library-search|media-player|messages|number-of-resources|property|recent-comments|ref|resource-list|search-form|tag-cloud|tags|ub-mapping|unit-search-form|uri-menu)|resource\:(?:breadcrumb|email-friend|feedback|manage-url|property|share-at|subfolder-menu|tags|toc))[^}]*}[\s]*)<\/p>/g, "<div>$1</div>", "");
    } catch(err) {
      vrtxAdmin.log({msg: err});
    }
   return val;
   ------------------------------------------------------------------------------------
   
   and in fileUploadXhr(a):
   ------------------------------------------------------------------------------------
   // USIT added from newest jquery.form.js: fix for upload progress
   if (options.uploadProgress) {
       // workaround because jqXHR does not expose upload property
       s.xhr = function() {
           var xhr = $.ajaxSettings.xhr();
           if (xhr.upload) {
               xhr.upload.addEventListener('progress', function(event) {
                   var percent = 0;
                   var position = event.loaded || event.position; /*event.position is deprecated*/
                   var total = event.total;
                   if (event.lengthComputable) {
                       percent = Math.ceil(position / total * 100);
                   }
                   options.uploadProgress(event, position, total, percent);
               }, false);
           }
           return xhr;
       };
   }
   ------------------------------------------------------------------------------------
   
   API: http://malsup.com/jquery/form/#api
