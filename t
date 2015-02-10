diff --git a/src/main/resources/web/themes/default/scss/default.scss b/src/main/resources/web/themes/default/scss/default.scss
index 3a85883..b3d1be7 100644
--- a/src/main/resources/web/themes/default/scss/default.scss
+++ b/src/main/resources/web/themes/default/scss/default.scss
@@ -850,44 +850,37 @@ div.dropdown-shortcut-menu-container form .vrtx-button button {
 
 #tabMenuRight {
   float: right;
-}
-
-#tabMenuRight li {
-  float: left;
-  margin: -10px 0 0 30px;
-  padding: 10px 0 10px;
-}
-
-#tabMenuRight li a {
-  display: block;
-  padding: 5px 0 6px 30px;
-}
-
-#tabMenuRight li.deleteResourcesService a {
-  background: url(images/tab-menu-delete.png) no-repeat center left;
-  padding-left: 25px;
-}
-
-#tabMenuRight li.moveResourcesService a {
-  background: url(images/tab-menu-move.png) no-repeat center left;
-  padding-left: 41px;
-}
-
-#tabMenuRight li.copyResourcesService a {
-  background: url(images/tab-menu-copy.png) no-repeat center left;
-  padding-left: 38px;
-}
-
-#tabMenuRight li.fileUploadService a {
-  background: url(images/tab-menu-upload.png) no-repeat center left; 
-}
-
-#tabMenuRight li.createDocumentService a {
-  background: url(images/tab-menu-new-file.png) no-repeat center left; 
-}
+  li {
+    float: left;
+    margin: -10px 0 0 30px;
+    padding: 10px 0 10px;
 
-#tabMenuRight li.createCollectionService a {
-  background: url(images/tab-menu-new-folder.png) no-repeat center left;
+    a {
+      display: block;
+      padding: 5px 0 6px 30px;
+    }
+    &.deleteResourcesService a {
+      background: url(images/tab-menu-delete.png) no-repeat center left;
+      padding-left: 25px;
+    }
+    &.moveResourcesService a {
+      background: url(images/tab-menu-move.png) no-repeat center left;
+      padding-left: 41px;
+    }
+    &.copyResourcesService a {
+      background: url(images/tab-menu-copy.png) no-repeat center left;
+      padding-left: 38px;
+    }
+    &.fileUploadService a {
+      background: url(images/tab-menu-upload.png) no-repeat center left;
+    }
+    &.createDocumentService a {
+      background: url(images/tab-menu-new-file.png) no-repeat center left;
+    }
+    &.createCollectionService a {
+      background: url(images/tab-menu-new-folder.png) no-repeat center left;
+    }
+  }
 }
 
 /* Tab forms */
