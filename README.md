# The VTK Web Framework README

## Installation

To set up a minimal version of the VTK CMS the following steps
are necessary:

1. Download and install Maven 3
   ( http://maven.apache.org/download.html )

2. [OPTIONAL] Create a database using PostgreSQL. The default is to use an embedded
   hsqldb database.

        sudo -u postgres createuser -P vortex
        sudo -u postgres createdb -O vortex vortex

3. [OPTIONAL] Set up the configuration. Configuration should be placed in the
   files `~/.vtk.properties` and/or `~/.vrtx.properties`.
   (also work as regular non-hidden files). The default is to use hsqldb and
   files under the `var` directory

        databaseDriver = [your JDBC driver, e.g. org.postgresql.Driver] 
        sqlDialect = {postgresql, hsqldb or oracle}
        databaseURL = [your JDBC URL, e.g. jdbc:postgresql:vortex]
        jdbcUsername = [your JDBC user]
        jdbcPassword = [your JDBC password]
        vtkFileSystemRoot = [an empty directory to place files]

4. Run the command `mvn jetty:run` (standing in the project
   directory). 

5. You should now be able to access the web service on
   http://localhost:9322/ and the WebDAV service on
   http://localhost:9321/. 
   
    Log in as `root@localhost:fish` or user `user@localhost:pw` through the
    [administration interface](http://localhost:9322/?vrtx=admin).

6. See the default configuration file
   `src/main/resources/vtk/beans/vtk.properties` for
   descriptions of the various configuration settings.

7. Custom bean definitions and overriding can be placed in the file
   `~/.vrtx-context.xml` (loaded if it exists).

8. Create `folder-templates`, `document-templates` and set up simple
   decorating using commands described in the following sub sections.

### Setting some root node properties:

Execute the following command in a Bourne-compatible shell:

```
curl -s -uroot@localhost:fish -X PROPPATCH http://localhost:9321/ -H 'Content-type: text/xml' \
     --data-binary @- <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<D:propertyupdate xmlns:D="DAV:">
  <D:set>
    <D:prop><userTitle xmlns="vrtx">The VTK Web Framework</userTitle></D:prop>
  </D:set>
  <D:set>
    <D:prop><recursive-listing xmlns="vrtx">false</recursive-listing></D:prop>
  </D:set>
</D:propertyupdate>
EOF
```

### Folder templates:

Execute the following commands in a Bourne-compatible shell:

```
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/folder-templates/
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/folder-templates/types/
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/folder-templates/types/article-listing/
curl -s -uroot@localhost:fish -X PROPPATCH http://localhost:9321/vrtx/folder-templates/types/article-listing/ \
     --data-binary @- <<'EOF'
<?xml version="1.0" encoding="utf-8" ?>
<D:propertyupdate xmlns:D="DAV:"
  xmlns:v="vrtx">
  <D:set>
    <D:prop>
      <v:collection-type>article-listing</v:collection-type>
    </D:prop>
  </D:set>
</D:propertyupdate>
EOF

curl -s -uroot@localhost:fish -X PUT http://localhost:9321/vrtx/folder-templates/config.txt \
     -H 'Content-Type: text/plain' --data-binary @- <<'EOF'
/ = types
EOF

```

### Document templates:

Execute the following command in a Bourne-compatible shell:

```
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/doc-templates/
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/doc-templates/types/
curl -s -uroot@localhost:fish -X PUT http://localhost:9321/vrtx/doc-templates/types/article.html \
     -H 'Content-Type: application/json' --data-binary @- <<'EOF'
{
   "resourcetype": "structured-article",
   "properties":    {
      "title": "#title#",
      "showAdditionalContent": "true",
      "hideAdditionalContent": "false"
   }
}
EOF

curl -s -uroot@localhost:fish -X PUT http://localhost:9321/vrtx/doc-templates/types/plain.txt \
     -H 'Content-Type: text/plain' --data-binary @- <<'EOF'
Plain text file.
EOF

curl -s -uroot@localhost:fish -X PUT http://localhost:9321/vrtx/doc-templates/config.txt \
     -H 'Content-Type: text/plain' --data-binary @- <<'EOF'
/ = types
EOF

```

### Decorating:

Execute the following command in a Bourne-compatible shell:

```
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/decorating/
curl -s -uroot@localhost:fish -X MKCOL http://localhost:9321/vrtx/decorating/templates/
curl -s -uroot@localhost:fish -X PUT http://localhost:9321/vrtx/decorating/templates/simple.html \
     -H 'Content-Type: text/html' --data-binary @- <<'EOF'
<!DOCTYPE html>
<html>
  <head ${document:element-attributes select=[html.head]}>
    <title>${document:title}</title>
    <!-- Include ugly CSS: -->
    <style type="text/css">
      body { background: #d9b38c; }
      h1 { background: #ffe6ea; }
      div#vrtx-collections { background: #ffffcc; }
    </style>
    ${document:head}
  </head>
  <body ${document:element-attributes select=[html.body]}>
    <!-- This ugly HTML is generated by template /vrtx/decorating/simple.html -->
    ${document:body}
    <hr>
    ${resource:manage-url}
  </body>
</html>
EOF

curl -s -uroot@localhost:fish -X PUT http://localhost:9321/vrtx/decorating/config.txt \
     -H 'Content-Type: text/plain' --data-binary @- <<'EOF'
/ = simple.html
/vrtx/decorating = NONE
EOF

curl -s -uroot@localhost:fish -X PUT http://localhost:9321/vrtx/decorating/title-config.txt \
     -H 'Content-Type: text/plain' --data-binary @- <<'EOF'

EOF

```

### Upload README.md

Assuming current directory is the VTK source code root, execute the following in
a Bourne-compatible shell:

```
curl -uroot@localhost:fish -X PUT -H "Content-type: text/markdown; charset=UTF-8" \
     --data-binary @README.md http://localhost:9321/README.md
```
