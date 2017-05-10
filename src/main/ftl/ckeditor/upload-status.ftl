<#ftl strip_whitespace=true output_format="HTML" auto_esc=true>
<#--
  - File: upload-status.ftl
  -  
  - Description: served to CKeditor as a response to a file upload
  - request. Invokes a Javascript function to let FCK know the status
  - of the completed operation.
  -  
  - 
  - Required model data:
  -  error - the error number
  -  fileURL - the URL of the file just uploaded
  -  fileName - the name of the file just uploaded
  -  
  - Optional model data:
  -  customMessage - a custom error message
  -
  -->
<script type="text/javascript">
  var uploadCompletedFunction;
  if (window.parent.frames['frmUpload'].OnUploadCompleted) {
     uploadCompletedFunction = window.parent.frames['frmUpload'].OnUploadCompleted;
  } else {
     uploadCompletedFunction = window.parent.OnUploadCompleted;
  }
  uploadCompletedFunction(
    ${error}, '${(fileURL)?default("")}', '${(fileName)?default("")}', '${(customMessage)?default("")}'
  );
</script>
