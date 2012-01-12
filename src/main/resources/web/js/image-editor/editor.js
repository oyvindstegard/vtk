/* 
 * Vortex HTML5 Canvas image editor
 *
 */

function VrtxImageEditor() {
  var instance; // Class-like singleton pattern (p.145 JavaScript Patterns)
  VrtxImageEditor = function VrtxImageEditor() {
    return instance;
  };
  VrtxImageEditor.prototype = this;
  instance = new VrtxImageEditor();
  instance.constructor = VrtxImageEditor;

  this.img = null;
  this.canvas = null;
  this.ctx = null;
  this.rw = null;
  this.rh = null;
  this.origw = null;
  this.origh = null;
  this.ratio = 1;
  this.restorePoints = [],
  this.keepAspectRatio = true;
  this.scaledBeforeCrop = false,
  this.hasCropBeenInitialized = false;

  return instance;
};

var vrtxImageEditor = new VrtxImageEditor();

$(function () {
  var imageEditorElm = $("#vrtx-image-editor-wrapper");
  if('getContext' in document.createElement('canvas') && imageEditorElm.length) {
    vrtxImageEditor.init(imageEditorElm);   
  }
});

VrtxImageEditor.prototype.init = function init(imageEditorElm) {
  var editor = this;

  imageEditorElm.addClass("canvas-supported");
  var $canvas = imageEditorElm.find("#vrtx-image-editor");
  editor.canvas = $canvas[0];
  editor.ctx = editor.canvas.getContext('2d');

  editor.img = new Image();
  var path = location.href;
  editor.img.src = path.substring(0, path.indexOf("?"));
  editor.img.onload = function () {
    editor.rw = editor.origw = editor.img.width;
    editor.rh = editor.origh = editor.img.height;
    editor.ratio = editor.origw / editor.origh;
    editor.canvas.setAttribute('width', editor.rw);
    editor.canvas.setAttribute('height', editor.rh);
    editor.canvas.width = editor.rw;
    editor.canvas.height = editor.rh;
    editor.displayDimensions(editor.rw, editor.rh);
    editor.saveRestorePoint();
    editor.ctx.drawImage(editor.img, 0, 0);
    $canvas.resizable({
      aspectRatio: editor.keepAspectRatio,
      grid: [1, 1],
      stop: function (event, ui) {
        var newWidth = Math.round(ui.size.width);
        var newHeight = Math.round(ui.size.height);
        editor.scale(newWidth, newHeight);
      },
      resize: function (event, ui) {
        editor.displayDimensions(Math.round(ui.size.width), Math.round(ui.size.height));
      }
    });
  }

  $("#app-content").delegate("#vrtx-image-crop", "click", function (e) {
    if (editor.hasCropBeenInitialized) {
      editor.rw = editor.origw = theSelection.w;
      editor.rh = editor.origh = theSelection.h;
      editor.ratio = editor.origw / editor.origh;
      editor.updateDimensions(editor.rw, editor.rh);
      editor.ctx.drawImage(editor.img, theSelection.x, theSelection.y, theSelection.w, theSelection.h, 
                                                    0,              0, theSelection.w, theSelection.h);
      editor.resetCropPlugin();
      $(this).val("Start beskjæring...");
      $("#vrtx-image-editor").resizable("enable");
      editor.saveRestorePoint();
      editor.renderRestorePoint();
      editor.hasCropBeenInitialized = false;
    } else {
      if (editor.scaledBeforeCrop) {
        editor.saveRestorePoint();
        editor.img.src = editor.restorePoints[editor.restorePoints.length - 1];
        editor.img.onload = function () {
          editor.ctx.drawImage(editor.img, 0, 0);
          $(this).val("Beskjær bilde");
          $("#vrtx-image-editor").resizable("disable");
          initSelection(editor.canvas, editor.ctx, editor.img);
          editor.scaledBeforeCrop = false;
        }
      } else {
        $(this).val("Beskjær bilde");
        $("#vrtx-image-editor").resizable("disable");
        initSelection(editor.canvas, editor.ctx, editor.img);
      }
      editor.hasCropBeenInitialized = true;
    }
    e.stopPropagation();
    e.preventDefault();
  });

  $("#app-content").delegate("#resource-width, #resource-height", "change", function (e) {
    var w = parseInt($.trim($("#resource-width").val()));
    var h = parseInt($.trim($("#resource-height").val()));
    if (!w.isNaN && !h.isNaN) {
      if (w !== editor.rw) {
        if (editor.keepAspectRatio) {
          h = w / editor.ratio;
          h = Math.round(h);
        }
        $("#resource-height").val(h)
      } else if (h !== editor.rh) {
        if (editor.keepAspectRatio) {
          w = h * editor.ratio;
          w = Math.round(w);
        }
        $("#resource-width").val(w)
      }
      editor.scale(w, h);
    }
  });

  $("#app-content").delegate("#resource-width", "keydown", function (e) {
    if (e.which == 38 || e.which == 40) {
      var w = parseInt($.trim($("#resource-width").val()));
      var h = parseInt($.trim($("#resource-height").val()));
      if (!w.isNaN && !h.isNaN) {
        if (e.which == 38) {
          w++;
        } else {
          if (w > 2) {
            w--;
          }
        }
        if (editor.keepAspectRatio) {
          h = w / editor.ratio;
          h = Math.round(h);
        }
        $("#resource-width").val(w);
        $("#resource-height").val(h);
        editor.scale(w, h);
      }
    }
  });

  $("#app-content").delegate("#resource-height", "keydown", function (e) {
    if (e.which == 38 || e.which == 40) {
      var w = parseInt($.trim($("#resource-width").val()));
      var h = parseInt($.trim($("#resource-height").val()));
      if (!w.isNaN && !h.isNaN) {
        if (e.which == 38) {
          h++;
        } else {
          if (h > 2) {
            h--;
          }
        }
        if (editor.keepAspectRatio) {
          w = h * editor.ratio;
          w = Math.round(w);
        }
        $("#resource-width").val(w);
        $("#resource-height").val(h);
        editor.scale(w, h);
      }
    }
  });
};

VrtxImageEditor.prototype.scale = function scale(newWidth, newHeight) {
  var editor = this;

  if(newWidth < editor.origw && $("#lanczos-downscaling:checked").length) { // Downscaling with Lanczos3
    editor.rw = newWidth;
    editor.rh = newHeight;
    editor.updateDimensions(editor.rw, editor.rh);
    new thumbnailer(editor.canvas, editor.ctx, editor.img, editor.rw, 3);
  } else { // Upscaling (I think with nearest neighbour. TODO: should be bicubic or bilinear)
    editor.rw = newWidth;
    editor.rh = newHeight;
    editor.updateDimensions(editor.rw, editor.rh);
    editor.ctx.drawImage(editor.img, 0, 0, editor.rw, editor.rh); 
  }
  editor.scaledBeforeCrop = true; 
};

VrtxImageEditor.prototype.resetCropPlugin = function resetCropPlugin() {
  $("#vrtx-image-editor").unbind("mousemove").unbind("mousedown").unbind("mouseup");
  iMouseX, iMouseY = 1;
  theSelection;
};

VrtxImageEditor.prototype.updateDimensions = function updateDimensions(w, h) {
  var editor = this;

  editor.canvas.setAttribute('width', w);
  editor.canvas.setAttribute('height', h);
  editor.canvas.width = w;
  editor.canvas.height = h;
  $(".ui-wrapper").css({
    "width": w,
    "height": h
  });
  $("#vrtx-image-editor").css({
    "width": w,
    "height": h
  });
  editor.displayDimensions(w, h);
};

VrtxImageEditor.prototype.displayDimensions = function displayDimensions(w, h) {
  if ($("#vrtx-image-dimensions-crop").length) {
    $("#resource-width").val(w);
    $("#resource-height").val(h);
  } else {
    var dimensionHtml = '<div id="vrtx-image-dimensions-crop">'
                      + '<div class="property-label">Bredde</div>'
                      + '<div class="vrtx-textfield" id="vrtx-textfield-width"><input id="resource-width" type="text" value="' + w + '" size="6" /></div>'
                      + '<div class="property-label">Høyde</div>'
                      + '<div class="vrtx-textfield" id="vrtx-textfield-height"><input id="resource-height" type="text" value="' + h + '" size="6" /></div>'
                      + '<div id="vrtx-image-crop-button"><div class="vrtx-button">'
                      + '<input type="button" id="vrtx-image-crop" value="Start beskjæring..." /></div></div>'
                      + '<div id="vrtx-lanczos-downscaling-wrapper"><input type="checkbox" id="lanczos-downscaling" />&nbsp;'
                      + '<label for="lanczos-downscaling" />Bruk Lanczos3 ved nedskalering</label></div>'
                      + '</div>';
                    //  + '<div class="vrtx-button-small vrtx-image-filters"><input type="button" id="vrtx-image-filters-sharpen" value="Skarpere" /></div>'
                    //  + '<div class="vrtx-button-small vrtx-image-filters"><input type="button" id="vrtx-image-filters-gray" value="Gråskala" /></div>'
                    //  + '<div class="vrtx-button-small vrtx-image-filters"><input type="button" id="vrtx-image-filters-sepia" value="Sepia" /></div>'
                    //  + '<div class="vrtx-image-filters-slider" id="vrtx-image-filters-sepia-slider"></div>';
    $(dimensionHtml).insertBefore("#vrtx-image-editor-preview");
    // $("#vrtx-image-filters-sepia-slider").slider({min: 0, max: 25, value: 10});
  }
};

/* Save/render restore points 
 * Credits: http://hyankov.wordpress.com/2010/12/26/how-to-implement-html5-canvas-undo-function/
 * TODO: Undo/redo functionality
 */
VrtxImageEditor.prototype.saveRestorePoint = function saveRestorePoint() {
  var editor = this;

  var imgSrc = editor.canvas.toDataURL("image/png");
  editor.restorePoints.push(imgSrc);
};

VrtxImageEditor.prototype.renderRestorePoint = function renderRestorePoint() {
  var editor = this;

  editor.img.src = editor.restorePoints[editor.restorePoints.length - 1];
  editor.img.onload = function () {
    editor.ctx.drawImage(editor.img, 0, 0);
  }
};

/* Thumbnailer / Lanczos algorithm for downscaling
 * Credits: http://stackoverflow.com/questions/2303690/resizing-an-image-in-an-html5-canvas
 *
 * Modified by USIT to use Web Workers if supported for process1 and process2 (otherwise degrade to setTimeout)
 *
 * TODO: Optimize and multiple Web Workers pr. process (tasking)
 *
 */

/* elem: Canvas element
 * ctx: Canvas 2D context 
 * img: Image element
 * sx: Scaled width
 * lobes: kernel radius (e.g. 3)
 */
function thumbnailer(elem, ctx, img, sx, lobes) {
  var canvas = elem;
  elem.width = img.width;
  elem.height = img.height;
  elem.style.display = "none";
  $("#vrtx-image-editor-preview").addClass("loading");
  $("#vrtx-image-crop").attr("disabled", "disabled");
  ctx.drawImage(img, 0, 0);

  var w = sx;
  var h = Math.round(img.height * w / img.width);
  var ratio = img.width / w;
  var data = {
    src: ctx.getImageData(0, 0, img.width, img.height),
    lobes: lobes,
    dest: {
      width: w,
      height: h,
      data: new Array(w * h * 3)
    },
    ratio: ratio,
    rcp_ratio: 2 / ratio,
    range2: Math.ceil(ratio * lobes / 2),
    cacheLanc: {},
    center: {},
    icenter: {}
  };

  // Used for Web Workers or setTimeout (inject scripts and use methods inside)
  var process1Url = '/vrtx/__vrtx/static-resources/js/image-editor/lanczos-process1.js';
  var process2Url = '/vrtx/__vrtx/static-resources/js/image-editor/lanczos-process2.js';

  if (false) { // "Worker" in window) { // Use Web Workers if supported); TODO: some problem with canvasPixelArray sent to worker and returned
    var workerLanczosProcess1 = new Worker(process1Url);
    var workerLanczosProcess2 = new Worker(process2Url); 
    workerLanczosProcess1.postMessage(data);
    workerLanczosProcess1.addEventListener('message', function(e) {
      var data = e.data;
      if(data) {   
        canvas.width = data.dest.width;
        canvas.height = data.dest.height;
        ctx.drawImage(img, 0, 0);
        data.src = ctx.getImageData(0, 0, data.dest.width, data.dest.height);
        workerLanczosProcess2.postMessage(data);
      } 
    }, false);
    workerLanczosProcess2.addEventListener('message', function(e) { 
      var data = e.data;
      if(data) { 
        ctx.putImageData(data.src, 0, 0); 
        elem.style.display = "block";
        $("#vrtx-image-editor-preview").removeClass("loading");
        $("#vrtx-image-crop").removeAttr("disabled"); 
      }
    }, false);
  } else { // Otherwise gracefully degrade to using setTimeout
    var headID = document.getElementsByTagName("head")[0];  
    var process1Script = document.createElement('script');
    var process2Script = document.createElement('script');
    process1Script.type = 'text/javascript';
    process2Script.type = 'text/javascript';
    process1Script.src = process1Url;
    process2Script.src = process2Url;
    headID.appendChild(process1Script);
    headID.appendChild(process2Script);

    process1Script.onload = function() {
      var u = 0; 
      var lanczos = lanczosCreate(data.lobes);
      var proc1 = setTimeout(function() {
        data = process1(data, u, lanczos);
        if(++u < data.dest.width) {
          setTimeout(arguments.callee, 0);
        } else {
          var proc2 = setTimeout(function() {
            canvas.width = data.dest.width;
            canvas.height = data.dest.height;
            ctx.drawImage(img, 0, 0);
            data.src = ctx.getImageData(0, 0, data.dest.width, data.dest.height);
            data = process2(data);
            ctx.putImageData(data.src, 0, 0);
            elem.style.display = "block";
            $("#vrtx-image-editor-preview").removeClass("loading");
            $("#vrtx-image-crop").removeAttr("disabled"); 
          }, 0);
        }
      }, 0);
    }
  }
}

/*
 * Crop plugin
 * Credits: http://www.script-tutorials.com/demos/197/index.html
 * TODO: optimize
 * Modified slightly by USIT
 */

var iMouseX, iMouseY = 1;
var theSelection;

// Define Selection constructor
function Selection(x, y, w, h) {
  this.x = x; // initial positions
  this.y = y;
  this.w = w; // and size
  this.h = h;
  this.px = x; // extra variables to dragging calculations
  this.py = y;
  this.csize = 6; // resize cubes size
  this.csizeh = 10; // resize cubes size (on hover)
  this.bHow = [false, false, false, false]; // hover statuses
  this.iCSize = [this.csize, this.csize, this.csize, this.csize]; // resize cubes sizes
  this.bDrag = [false, false, false, false]; // drag statuses
  this.bDragAll = false; // drag whole selection
}

// Define Selection draw method
Selection.prototype.draw = function (ctx, img) {
  ctx.strokeStyle = '#000';
  ctx.lineWidth = 2;
  ctx.strokeRect(this.x, this.y, this.w, this.h);
  // draw part of original image
  if (this.w > 0 && this.h > 0) {
    ctx.drawImage(img, this.x, this.y, this.w, this.h, this.x, this.y, this.w, this.h);
  }
  // draw resize cubes
  ctx.fillStyle = '#fff';
  ctx.fillRect(this.x - this.iCSize[0], this.y - this.iCSize[0], this.iCSize[0] * 2, this.iCSize[0] * 2);
  ctx.fillRect(this.x + this.w - this.iCSize[1], this.y - this.iCSize[1], this.iCSize[1] * 2, this.iCSize[1] * 2);
  ctx.fillRect(this.x + this.w - this.iCSize[2], this.y + this.h - this.iCSize[2], this.iCSize[2] * 2, this.iCSize[2] * 2);
  ctx.fillRect(this.x - this.iCSize[3], this.y + this.h - this.iCSize[3], this.iCSize[3] * 2, this.iCSize[3] * 2);
}

function drawScene(ctx, img) { // Main drawScene function
  ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height); // clear canvas
  // draw source image
  ctx.drawImage(img, 0, 0, ctx.canvas.width, ctx.canvas.height);
  // and make it darker
  ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
  ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
  // draw selection
  theSelection.draw(ctx, img);
}

function initSelection(canvas, ctx, img) {
  // create initial selection
  theSelection = new Selection(40, 40, $(canvas).width() - 40, $(canvas).height() - 40);
  $('#vrtx-image-editor').bind("mousemove", function (e) { // binding mouse move event
    var canvasOffset = $(canvas).offset();
    iMouseX = Math.floor(e.pageX - canvasOffset.left);
    iMouseY = Math.floor(e.pageY - canvasOffset.top);
    // in case of drag of whole selector
    if (theSelection.bDragAll) {
      theSelection.x = iMouseX - theSelection.px;
      theSelection.y = iMouseY - theSelection.py;
    }
    for (i = 0; i < 4; i++) {
      theSelection.bHow[i] = false;
      theSelection.iCSize[i] = theSelection.csize;
    }
    // hovering over resize cubes
    if (iMouseX > theSelection.x - theSelection.csizeh 
     && iMouseX < theSelection.x + theSelection.csizeh
     && iMouseY > theSelection.y - theSelection.csizeh
     && iMouseY < theSelection.y + theSelection.csizeh) {
      theSelection.bHow[0] = true;
      theSelection.iCSize[0] = theSelection.csizeh;
    }
    if (iMouseX > theSelection.x + theSelection.w - theSelection.csizeh 
     && iMouseX < theSelection.x + theSelection.w + theSelection.csizeh
     && iMouseY > theSelection.y - theSelection.csizeh
     && iMouseY < theSelection.y + theSelection.csizeh) {
      theSelection.bHow[1] = true;
      theSelection.iCSize[1] = theSelection.csizeh;
    }
    if (iMouseX > theSelection.x + theSelection.w - theSelection.csizeh
     && iMouseX < theSelection.x + theSelection.w + theSelection.csizeh
     && iMouseY > theSelection.y + theSelection.h - theSelection.csizeh
     && iMouseY < theSelection.y + theSelection.h + theSelection.csizeh) {
      theSelection.bHow[2] = true;
      theSelection.iCSize[2] = theSelection.csizeh;
    }
    if (iMouseX > theSelection.x - theSelection.csizeh
     && iMouseX < theSelection.x + theSelection.csizeh
     && iMouseY > theSelection.y + theSelection.h - theSelection.csizeh
     && iMouseY < theSelection.y + theSelection.h + theSelection.csizeh) {
      theSelection.bHow[3] = true;
      theSelection.iCSize[3] = theSelection.csizeh;
    }
    // in case of dragging of resize cubes
    var iFW, iFH;
    if (theSelection.bDrag[0]) {
      var iFX = iMouseX - theSelection.px;
      var iFY = iMouseY - theSelection.py;
      iFW = theSelection.w + theSelection.x - iFX;
      iFH = theSelection.h + theSelection.y - iFY;
    }
    if (theSelection.bDrag[1]) {
      var iFX = theSelection.x;
      var iFY = iMouseY - theSelection.py;
      iFW = iMouseX - theSelection.px - iFX;
      iFH = theSelection.h + theSelection.y - iFY;
    }
    if (theSelection.bDrag[2]) {
      var iFX = theSelection.x;
      var iFY = theSelection.y;
      iFW = iMouseX - theSelection.px - iFX;
      iFH = iMouseY - theSelection.py - iFY;
    }
    if (theSelection.bDrag[3]) {
      var iFX = iMouseX - theSelection.px;
      var iFY = theSelection.y;
      iFW = theSelection.w + theSelection.x - iFX;
      iFH = iMouseY - theSelection.py - iFY;
    }
    if (iFW > theSelection.csizeh * 2 && iFH > theSelection.csizeh * 2) {
      theSelection.w = iFW;
      theSelection.h = iFH;
      theSelection.x = iFX;
      theSelection.y = iFY;
    }
    drawScene(ctx, img);
  });
  $('#vrtx-image-editor').bind("mousedown", function (e) { // binding mousedown event
    var canvasOffset = $(canvas).offset();
    iMouseX = Math.floor(e.pageX - canvasOffset.left);
    iMouseY = Math.floor(e.pageY - canvasOffset.top);
    theSelection.px = iMouseX - theSelection.x;
    theSelection.py = iMouseY - theSelection.y;
    if (theSelection.bHow[0]) {
      theSelection.px = iMouseX - theSelection.x;
      theSelection.py = iMouseY - theSelection.y;
    }
    if (theSelection.bHow[1]) {
      theSelection.px = iMouseX - theSelection.x - theSelection.w;
      theSelection.py = iMouseY - theSelection.y;
    }
    if (theSelection.bHow[2]) {
      theSelection.px = iMouseX - theSelection.x - theSelection.w;
      theSelection.py = iMouseY - theSelection.y - theSelection.h;
    }
    if (theSelection.bHow[3]) {
      theSelection.px = iMouseX - theSelection.x;
      theSelection.py = iMouseY - theSelection.y - theSelection.h;
    }
    if (iMouseX > theSelection.x + theSelection.csizeh
     && iMouseX < theSelection.x + theSelection.w - theSelection.csizeh
     && iMouseY > theSelection.y + theSelection.csizeh
     && iMouseY < theSelection.y + theSelection.h - theSelection.csizeh) {
      theSelection.bDragAll = true;
    }
    for (i = 0; i < 4; i++) {
      if (theSelection.bHow[i]) {
        theSelection.bDrag[i] = true;
      }
    }
  });
  $('#vrtx-image-editor').bind("mouseup", function (e) { // binding mouseup event
    theSelection.bDragAll = false;
    for (i = 0; i < 4; i++) {
      theSelection.bDrag[i] = false;
    }
    theSelection.px = 0;
    theSelection.py = 0;
  });
  drawScene(ctx, img);
}

/* ^ Vortex HTML5 Canvas image editor */
