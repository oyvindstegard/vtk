/*
* @example An iframe-based dialog with custom button handling logics.
*/

var propsStandard = {
  "url" : "",
  "width" : 507,
  "height" : 322,
  "autoplay" : "false",
  "contentType" : ""
};

var props = {
  "url" : "",
  "width" : 507,
  "height" : 322,
  "autoplay" : "false",
  "contentType" : ""
};

var propsAlign = "";

( function() {
    CKEDITOR.plugins.add( 'mediaembed',
    {
        requires: [ 'iframedialog' ],
        init: function( editor )
        {
           var me = this;
           CKEDITOR.dialog.add( 'MediaEmbedDialog', function (editor)
           {	
              return {
                 title : 'Embed Media Dialog',
                 minWidth : 550,
                 minHeight : 200,
                 contents :
                       [
                          {
                             id : 'iframe',
                             label : 'Embed Media',
                             expand : true,
                             elements :
                                   [
                                      {
						               type : 'html',
						               id : 'pageMediaEmbed',
						               label : 'Embed Media',
						               style : 'width : 100%',
						               html : '<iframe src="'+me.path.toLowerCase()+'dialogs/mediaembed_'+editor.config.language+'.html" frameborder="0" name="iframeMediaEmbed" id="iframeMediaEmbed" allowtransparency="1" style="width:100%;height:250px;margin:0;padding:0;"></iframe>'
						              }
                                   ]
                          }
                       ],
                  onShow : function() {
            	    var check = setInterval(function() { // check each 50ms if iframe content is loaded
            	      var theIframe = $("iframe#iframeMediaEmbed");
            	      if(theIframe) {
            	        var contents = theIframe.contents();
            	        if(contents.find("#chkAutoplay").length) {
            	          // Put standardvalues in dialog
            	          contents.find("#txtUrl").val(propsStandard.url);
            	          contents.find("#txtWidth").val(propsStandard.width);
            	          contents.find("#txtHeight").val(propsStandard.height);
            	          contents.find("#txtContentType").val(propsStandard.contentType);
                	      if(propsStandard.autoplay == "true") {
                	        contents.find("#chkAutoplay").attr("checked", true);  	
                	      } else {
                	        contents.find("#chkAutoplay").attr("checked", false);  
                	      }
                	      contents.find("#txtAlign").val("");  
                	      
                	      // Clear loop
            	          clearInterval(check);
            	        }
            	      }
            	    }, 50);
                  },
                  onOk : function() {
                	  
                	  
                	  var editor = this.getParentEditor();
                	  
					  var theIframe = $("iframe#iframeMediaEmbed");
              	        var contents = theIframe.contents();
					    var url = contents.find("#txtUrl").val();
						if(url != "" && url.indexOf(".") != -1) {
							  var content = "${include:media-player url=["+unescape(url)+"]";			    
							  var contentType = contents.find("#txtContentType").val();
							  if(contentType.length > 0) {
							    content = content + " content-type=["+contentType+"]";
							  }
							  var width = contents.find("#txtWidth").val();
							  if(width.length > 0 && width != propsStandard.width) {
							    content = content + " width=["+width+"]";
							  }
							  var height = contents.find("#txtHeight").val();
							  if(height.length > 0 && height != propsStandard.height) {
							    content = content + " height=["+height+"]";
						      }
							  var autoplay = contents.find("#chkAutoplay");
							  if(autoplay.attr("checked") == true) {
							    content = content + " autoplay=[true]";
							  }
							  var align = contents.find("#txtAlign").val();
				
							  if(content.length>0) {
							    content = content + "}";
							  }			
							
							  var divClassType = '';
							  if(contentType.length > 0 && contentType == "audio/mp3") {  
							    divClassType = 'vrtx-media-player-audio';
							  } else if (getExtension(url) == "mp3") {
							    divClassType = 'vrtx-media-player-audio';
							  } else {
							    divClassType ='vrtx-media-player';
							  }
							  
	                          selected = editor.getSelection().getStartElement();
	                      	  selected.removeAttribute("class");
		                      if(align != "" && divClassType != "") {
		                        selected.addClass(divClassType);
		                    	selected.addClass(align);
		                      } else {
		                    	selected.addClass(divClassType); 
		                      }
	                      	  if(selected.is("p")) {
	                      	    selected.renameNode("div");
	                      	  }
	                      	  selected.setText(content);
							  
						} else {
						  alert("Du må spesifisere en URL");
						  return false;	
						}
                 }
              };
           } );
           
           
           CKEDITOR.dialog.add( 'MediaEmbedDialogMod', function (editor)
                   {	
                      return {
                         title : 'Embed Media Dialog',
                         minWidth : 550,
                         minHeight : 200,
                         contents :
                               [
                                  {
                                     id : 'iframe',
                                     label : 'Embed Media',
                                     expand : true,
                                     elements :
                                           [
                                              {
        						               type : 'html',
        						               id : 'pageMediaEmbed',
        						               label : 'Embed Media',
        						               style : 'width : 100%',
        						               html : '<iframe src="'+me.path.toLowerCase()+'dialogs/mediaembed_'+editor.config.language+'.html" frameborder="0" name="iframeMediaEmbed" id="iframeMediaEmbed" allowtransparency="1" style="width:100%;height:250px;margin:0;padding:0;"></iframe>'
        						              }
                                           ]
                                  }
                               ],
                          onShow : function() {
                    	    var check = setInterval(function() { // check each 50ms if iframe content is loaded
                    	      var theIframe = $("iframe#iframeMediaEmbed");
                    	      if(theIframe) {
                    	        var contents = theIframe.contents();
                    	        if(contents.find("#chkAutoplay").length) {
                    	          // Put values in dialog
                    	          contents.find("#txtUrl").val(props.url);
                    	          contents.find("#txtWidth").val(props.width);
                    	          contents.find("#txtHeight").val(props.height);
                    	          contents.find("#txtContentType").val(props.contentType);
                        	      if(props.autoplay == "true") {
                        	        contents.find("#chkAutoplay").attr("checked", true);  	
                        	      } else {
                        	        contents.find("#chkAutoplay").attr("checked", false);  
                        	      }
                        	      if(propsAlign != "" && propsAlign != " ") {
                        	        contents.find("#txtAlign").val(propsAlign);
                        	      } else {
                        	    	contents.find("#txtAlign").val("");  
                        	      }
                        	      
                        	      // Restore init values
                        	      props.url = propsStandard.url;
                        	      props.width = propsStandard.width;
                        	      props.height = propsStandard.height;
                        	      props.autoplay = propsStandard.autoplay;
                        	      props.contentType = propsStandard.contentType;
                        	      propsAlign = "";
                        	      
                        	      // Clear loop
                    	          clearInterval(check);
                    	        }
                    	      }
                    	    }, 50);
                          },
                          onOk : function() {
                        	  
                        	  
                        	  var editor = this.getParentEditor();
                        	  
        					  var theIframe = $("iframe#iframeMediaEmbed");
                      	        var contents = theIframe.contents();
        					    var url = contents.find("#txtUrl").val();
        						if(url != "" && url.indexOf(".") != -1) {
        							  var content = "${include:media-player url=["+unescape(url)+"]";			    
        							  var contentType = contents.find("#txtContentType").val();
        							  if(contentType.length > 0) {
        							    content = content + " content-type=["+contentType+"]";
        							  }
        							  var width = contents.find("#txtWidth").val();
        							  if(width.length > 0 && width != propsStandard.width) {
        							    content = content + " width=["+width+"]";
        							  }
        							  var height = contents.find("#txtHeight").val();
        							  if(height.length > 0 && height != propsStandard.height) {
        							    content = content + " height=["+height+"]";
        						      }
        							  var autoplay = contents.find("#chkAutoplay");
        							  if(autoplay.attr("checked") == true) {
        							    content = content + " autoplay=[true]";
        							  }
        							  var align = contents.find("#txtAlign").val();
        				
        							  if(content.length>0) {
        							    content = content + "}";
        							  }			
        							
        							  var divClassType = '';
        							  if(contentType.length > 0 && contentType == "audio/mp3") {  
        							    divClassType = 'vrtx-media-player-audio';
        							  } else if (getExtension(url) == "mp3") {
        							    divClassType = 'vrtx-media-player-audio';
        							  } else {
        							    divClassType ='vrtx-media-player';
        							  }
        							  
        	                          selected = editor.getSelection().getStartElement();
        	                      	  selected.removeAttribute("class");
        		                      if(align != "" && divClassType != "") {
        		                        selected.addClass(divClassType);
        		                    	selected.addClass(align);
        		                      } else {
        		                    	selected.addClass(divClassType); 
        		                      }
        	                      	  if(selected.is("p")) {
        	                      	    selected.renameNode("div");
        	                      	  }
        	                      	  selected.setText(content);
        							  
        						} else {
        						  alert("Du må spesifisere en URL");
        						  return false;	
        						}
                         }
                      };
                   } );

            editor.addCommand( 'mediaembed', new CKEDITOR.dialogCommand( 'MediaEmbedDialog' ) );
            editor.addCommand( 'mediaembedmod', new CKEDITOR.dialogCommand( 'MediaEmbedDialogMod' ) );
            editor.addCommand( 'MediaEmbedRemove',
    				{
    					exec : function( editor )
    					{
    						var selection = editor.getSelection();
							var bookmarks = selection.createBookmarks();
							var node = selection.getStartElement();
                            node.remove( false );
    						selection.selectBookmarks( bookmarks );
    					}
    				} );

            editor.ui.addButton( 'MediaEmbed',
            {
                label: 'Embed Media',
                command: 'mediaembed',
                icon: this.path.toLowerCase() + 'images/icon.gif'
            } );
            
            editor.on( 'doubleclick', function( evt ){
            	        var data = evt.data;
        				var element = data.element;

        				var HTML = element.getHtml();
        				if(HTML.indexOf("include:media-player") == -1) {
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
            	  editor.addMenuItems(
            	  {	  
            		MediaEmbedDialogMod:
            		{
            	        label: 'Mediaegenskaper',
            	        command: 'mediaembedmod',
            	        group: 'mediaembed',
            	        icon: this.path.toLowerCase() + 'images/icon.gif',
            	        order: 1
            		},
					RemoveMedia:
					{
						label: 'Fjern media',
						command: 'MediaEmbedRemove',
						group: 'mediaembed',
						icon: this.path.toLowerCase() + 'images/iconremove.gif',
						order: 5
					}
            	    
            	  });
            	}
            
            	if (editor.contextMenu) {
            	  editor.contextMenu.addListener(function(element, selection) {
            		var HTML = element.getHtml();
            		if(HTML.indexOf("include:media-player") == -1) {
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
} )();

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
	propsAlign = $.trim(className.replace(/vrtx-media-player[\w-]*/g, ""));
  		
    for(var name in props) {
      if(name != "contentType") {
        regexp = new RegExp('(?:' + name + '=\\[)(.*?)(?=\\])'); // non-capturing group for prop=. TODO: positive lookbehind (non-capturing)
      } else {
    	regexp = new RegExp('(?:content\\-type=\\[)(.*?)(?=\\])'); // non-capturing group for prop=. TODO: positive lookbehind (non-capturing)
      }
      
  	  var prop = regexp.exec(HTML);
  	  if(prop != null) {
 		if(prop.length = 2) {
 		  props[name] = prop[1]; // get the capturing group 
 		}
  	  }
  	  HTML = HTMLOrig; //TODO: is it possible to avoid this?
    }
 }