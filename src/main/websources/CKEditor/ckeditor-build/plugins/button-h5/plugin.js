(function(){
   var a= {
      exec:function(editor){
         var format = {
            element : 'h5',
         };
      var style = new CKEDITOR.style(format);
      style.apply(editor.document);
   }
 },

 b="button-h5";
 CKEDITOR.plugins.add(b,{
    init:function(editor){
       editor.addCommand(b,a);
    }
 });
})();
