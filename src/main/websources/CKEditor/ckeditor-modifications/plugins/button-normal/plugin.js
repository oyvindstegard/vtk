(function(){
   var a= {
      exec:function(editor){
         var format = {
            element : 'p',
         };
      var style = new CKEDITOR.style(format);
      style.apply(editor.document);
   }
 },

 b="button-normal";
 CKEDITOR.plugins.add(b,{
    init:function(editor){
       editor.addCommand(b,a);
    }
 });
})();
